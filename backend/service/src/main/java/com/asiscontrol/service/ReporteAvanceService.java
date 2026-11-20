package com.asiscontrol.service;

import com.asiscontrol.dto.InformeDisponibleDto;
import com.asiscontrol.dto.ReporteAvanceDto;
import com.asiscontrol.entity.*;
import com.asiscontrol.entity.enums.*;
import com.asiscontrol.entity.planificacion.PeriodoAcademico;
import com.asiscontrol.exception.BusinessRuleException;
import com.asiscontrol.exception.ResourceNotFoundException;
import com.asiscontrol.repository.*;
import com.asiscontrol.repository.planificacion.PeriodoAcademicoRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class ReporteAvanceService {
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private final AlumnoRepository alumnos;
    private final AsignacionCursoRepository asignaciones;
    private final PeriodoAcademicoRepository periodos;
    private final MatriculaRepository matriculas;
    private final ModuloAccessGuard acceso;
    private final EvaluacionService evaluaciones;
    private final EntityManager em;

    public ReporteAvanceService(AlumnoRepository alumnos, AsignacionCursoRepository asignaciones,
            PeriodoAcademicoRepository periodos, MatriculaRepository matriculas,
            ModuloAccessGuard acceso, EvaluacionService evaluaciones, EntityManager em) {
        this.alumnos = alumnos;
        this.asignaciones = asignaciones;
        this.periodos = periodos;
        this.matriculas = matriculas;
        this.acceso = acceso;
        this.evaluaciones = evaluaciones;
        this.em = em;
    }

    public record ResultadoPeriodo(int informesProcesados, List<InformeDisponibleDto> informes) { }
    public record EstadoAlumno(Long idAlumno, String alumno, boolean borrador, boolean publicada) { }
    public record EstadoPeriodo(List<EstadoAlumno> alumnos, boolean publicable, boolean desactualizada,
                                String impedimento) { }

    @Transactional(readOnly = true)
    public EstadoPeriodo estadoPeriodo(Long curso, Long periodoId) {
        var asignacion = asignacion(curso);
        acceso.verificarAsignacionDocente(asignacion);
        var periodo = periodo(periodoId, asignacion);
        var alumnosPeriodo = new ArrayList<EstadoAlumno>();
        boolean desactualizada = false;
        boolean tieneBorradores = false;
        String impedimento = null;
        for (var alumno : estudiantes(asignacion)) {
            var actual = ultimo(alumno.getId(), curso, periodoId, false).orElse(null);
            var publicada = ultimo(alumno.getId(), curso, periodoId, true).orElse(null);
            boolean borrador = actual != null && actual.getEstado() == EstadoInformeProgreso.BORRADOR;
            alumnosPeriodo.add(new EstadoAlumno(alumno.getId(), nombre(alumno.getPersona()), borrador, publicada != null));
            if (publicada != null && !desactualizada) {
                try {
                    validarVigencia(detalles(publicada), asignacion, periodo);
                } catch (BusinessRuleException e) {
                    desactualizada = true;
                }
            }
            if (borrador) {
                tieneBorradores = true;
                try {
                    validarBorrador(actual, asignacion, periodo);
                } catch (BusinessRuleException e) {
                    if (impedimento == null) impedimento = e.getMessage();
                }
            }
        }
        if (tieneBorradores && impedimento == null) {
            if (alumnosPeriodo.stream().anyMatch(a -> !a.borrador() && !a.publicada())) {
                impedimento = "Genera borradores para todos los estudiantes antes de publicar.";
            } else {
                try {
                    evaluaciones.validarPublicacion(evaluacionesDelPeriodo(asignacion, periodo).stream()
                            .filter(e -> e.getEstado() == EstadoEvaluacion.BORRADOR).toList());
                } catch (BusinessRuleException e) {
                    impedimento = e.getMessage();
                }
            }
        }
        return new EstadoPeriodo(alumnosPeriodo, tieneBorradores && impedimento == null, desactualizada, impedimento);
    }

    /** Genera o reemplaza solamente borradores. Una version publicada nunca se modifica. */
    @Transactional
    public ResultadoPeriodo generarPeriodo(Long curso, Long periodoId) {
        var asignacion = asignacion(curso);
        acceso.verificarAsignacionDocente(asignacion);
        // Serializa la asignacion de numeros de version, incluso cuando aun no existe un informe.
        em.lock(asignacion, LockModeType.PESSIMISTIC_WRITE);
        var periodo = periodo(periodoId, asignacion);
        var estudiantes = estudiantes(asignacion);
        for (var alumno : estudiantes) generar(alumno, asignacion, periodo);
        return new ResultadoPeriodo(estudiantes.size(), estudiantes.stream().map(a ->
                new InformeDisponibleDto(a.getId(), nombre(a.getPersona()), curso,
                        asignacion.getCurso().getNombre(), asignacion.getSeccion().getAnioAcademico(),
                        periodoId, periodo.getNombre())).toList());
    }

    /** Publica todas las libretas en una unica transaccion y conserva las versiones anteriores. */
    @Transactional
    public ResultadoPeriodo publicarPeriodo(Long curso, Long periodoId) {
        var asignacion = asignacion(curso);
        acceso.verificarAsignacionDocente(asignacion);
        em.lock(asignacion, LockModeType.PESSIMISTIC_WRITE);
        var periodo = periodo(periodoId, asignacion);
        var estudiantes = estudiantes(asignacion);
        var pendientes = new ArrayList<InformeProgreso>();
        for (var alumno : estudiantes) {
            var informe = ultimo(alumno.getId(), curso, periodoId, false)
                    .orElseGet(() -> generar(alumno, asignacion, periodo));
            if (informe.getEstado() == EstadoInformeProgreso.PUBLICADA) continue;
            validarBorrador(informe, asignacion, periodo);
            pendientes.add(informe);
        }
        // Mantiene las validaciones existentes de las planillas y publica sus evaluaciones.
        if (!pendientes.isEmpty()) evaluaciones.publicarPeriodo(curso, periodoId);
        var fecha = Instant.now();
        for (var informe : pendientes) {
            informe.setEstado(EstadoInformeProgreso.PUBLICADA);
            informe.setFechaPublicacion(fecha);
        }
        return new ResultadoPeriodo(pendientes.size(), List.of());
    }

    private void validarBorrador(InformeProgreso informe, AsignacionCurso asignacion, PeriodoAcademico periodo) {
        var filas = detalles(informe);
        if (filas.isEmpty() || filas.stream().anyMatch(d -> d.getNivelLogro() == null || d.getCalificacion() == null)) {
            throw new BusinessRuleException("INFORME_INCOMPLETO",
                    "Completa los niveles de logro y vuelve a generar los borradores antes de publicar");
        }
        validarVigencia(filas, asignacion, periodo);
    }

    private List<Alumno> estudiantes(AsignacionCurso asignacion) {
        var estudiantes = em.createQuery("""
                select distinct m.alumno from Matricula m where m.activo = true
                and m.alumno.activo = true and m.seccion.id = :seccion
                and m.estado in (com.asiscontrol.entity.enums.EstadoMatricula.ACTIVA,
                                 com.asiscontrol.entity.enums.EstadoMatricula.FINALIZADA)
                """, Alumno.class).setParameter("seccion", asignacion.getSeccion().getId()).getResultList();
        if (estudiantes.isEmpty()) throw new BusinessRuleException("INFORME_SIN_ALUMNOS", "No hay alumnos matriculados en esta seccion");
        return estudiantes;
    }

    private InformeProgreso generar(Alumno alumno, AsignacionCurso asignacion, PeriodoAcademico periodo) {
        var pruebas = evaluacionesDelPeriodo(asignacion, periodo);
        if (pruebas.isEmpty() || pruebas.stream().allMatch(e -> e.getCompetencias().isEmpty())) {
            throw new BusinessRuleException("REPORTE_SIN_CALIFICACIONES", "No hay evaluaciones con competencias en el periodo seleccionado");
        }
        var notas = em.createQuery("""
                select c from Calificacion c where c.activo = true and c.alumno.id = :alumno
                and c.evaluacion.asignacionCurso.id = :curso
                """, Calificacion.class).setParameter("alumno", alumno.getId()).setParameter("curso", asignacion.getId())
                .getResultList().stream().collect(Collectors.toMap(c -> c.getEvaluacion().getId() + ":" + c.getCompetencia().getId(), c -> c));
        var anterior = ultimo(alumno.getId(), asignacion.getId(), periodo.getId(), false).orElse(null);
        InformeProgreso informe;
        if (anterior != null && anterior.getEstado() == EstadoInformeProgreso.BORRADOR) {
            informe = anterior;
            detalles(informe).forEach(em::remove);
            em.flush(); // Libera las claves unicas antes de insertar los nuevos detalles.
        } else {
            informe = new InformeProgreso();
            informe.setAlumno(alumno);
            informe.setAsignacionCurso(asignacion);
            informe.setPeriodoAcademico(periodo);
            informe.setNumeroVersion(anterior == null ? 1 : anterior.getNumeroVersion() + 1);
            informe.setDocente(asignacion.getDocente());
            em.persist(informe);
        }
        informe.setDocente(asignacion.getDocente());
        for (var prueba : pruebas) for (var competencia : prueba.getCompetencias()) {
            var nota = notas.get(prueba.getId() + ":" + competencia.getId());
            var detalle = new DetalleInformeProgreso();
            detalle.setInforme(informe); detalle.setEvaluacion(prueba); detalle.setCompetencia(competencia);
            detalle.setCalificacion(nota); detalle.setNombreCompetencia(competencia.getNombre());
            detalle.setTituloEvaluacion(prueba.getTitulo()); detalle.setFechaEvaluacion(prueba.getFechaEvaluacion());
            detalle.setPonderacionPorcentaje(prueba.getPonderacionPorcentaje());
            detalle.setNivelLogro(nota == null ? null : nota.getNivelLogro());
            detalle.setObservacion(nota == null ? null : nota.getObservacion());
            em.persist(detalle);
        }
        return informe;
    }

    private List<Evaluacion> evaluacionesDelPeriodo(AsignacionCurso asignacion, PeriodoAcademico periodo) {
        return em.createQuery("""
                select distinct e from Evaluacion e left join fetch e.competencias
                where e.activo = true and e.asignacionCurso.id = :curso
                and e.fechaEvaluacion between :inicio and :fin
                and e.estado <> com.asiscontrol.entity.enums.EstadoEvaluacion.ANULADA
                order by e.fechaEvaluacion, e.id
                """, Evaluacion.class).setParameter("curso", asignacion.getId())
                .setParameter("inicio", periodo.getFechaInicio()).setParameter("fin", periodo.getFechaFin()).getResultList();
    }

    private void validarVigencia(List<DetalleInformeProgreso> filas, AsignacionCurso asignacion, PeriodoAcademico periodo) {
        var claves = evaluacionesDelPeriodo(asignacion, periodo).stream().flatMap(e -> e.getCompetencias().stream()
                .map(c -> e.getId() + ":" + c.getId())).collect(Collectors.toSet());
        boolean cambio = !claves.equals(filas.stream().map(d -> d.getEvaluacion().getId() + ":" + d.getCompetencia().getId())
                .collect(Collectors.toSet())) || filas.stream().anyMatch(d -> {
                    var nota = d.getCalificacion();
                    var prueba = d.getEvaluacion();
                    return !nota.isActivo() || nota.getNivelLogro() != d.getNivelLogro()
                            || !Objects.equals(nota.getObservacion(), d.getObservacion())
                            || !Objects.equals(prueba.getTitulo(), d.getTituloEvaluacion())
                            || !Objects.equals(prueba.getFechaEvaluacion(), d.getFechaEvaluacion())
                            || prueba.getPonderacionPorcentaje().compareTo(d.getPonderacionPorcentaje()) != 0
                            || !Objects.equals(d.getCompetencia().getNombre(), d.getNombreCompetencia());
                });
        if (cambio) throw new BusinessRuleException("INFORME_DESACTUALIZADO",
                "Las evaluaciones o notas cambiaron. Vuelve a generar y revisar los borradores antes de publicar");
    }

    @Transactional(readOnly = true)
    public List<InformeDisponibleDto> disponibles() {
        return em.createQuery("""
                select i from InformeProgreso i
                where i.activo = true and i.estado = com.asiscontrol.entity.enums.EstadoInformeProgreso.PUBLICADA
                and i.alumno.activo = true and i.asignacionCurso.activo = true
                and i.periodoAcademico.activo = true and i.periodoAcademico.anioAcademico.activo = true
                and (:docente is null or i.asignacionCurso.docente.persona.id = :docente)
                and (:alumno is null or i.alumno.persona.id = :alumno)
                and (:apoderado is null or exists (select v.id from AlumnoApoderado v
                    where v.activo = true and v.apoderado.activo = true and v.alumno = i.alumno
                    and v.apoderado.persona.id = :apoderado))
                and exists (select m.id from Matricula m where m.activo = true and m.alumno = i.alumno
                    and m.seccion = i.asignacionCurso.seccion
                    and m.estado in (com.asiscontrol.entity.enums.EstadoMatricula.ACTIVA,
                                     com.asiscontrol.entity.enums.EstadoMatricula.FINALIZADA))
                order by i.alumno.id, i.asignacionCurso.id, i.periodoAcademico.id, i.numeroVersion desc
                """, InformeProgreso.class)
                .setParameter("docente", acceso.restriccionDocentePersonaId())
                .setParameter("alumno", acceso.restriccionAlumnoPersonaId())
                .setParameter("apoderado", acceso.restriccionApoderadoPersonaId()).getResultList().stream()
                .map(i -> new InformeDisponibleDto(i.getAlumno().getId(), nombre(i.getAlumno().getPersona()),
                        i.getAsignacionCurso().getId(), i.getAsignacionCurso().getCurso().getNombre(),
                        i.getAsignacionCurso().getSeccion().getAnioAcademico(), i.getPeriodoAcademico().getId(),
                        i.getPeriodoAcademico().getNombre())).distinct().toList();
    }

    @Transactional(readOnly = true)
    public ReporteAvanceDto preparar(Long alumnoId, Long curso, Long periodoId) {
        return preparar(alumnoId, curso, periodoId, false);
    }

    @Transactional(readOnly = true)
    public ReporteAvanceDto prepararBorrador(Long alumnoId, Long curso, Long periodoId) {
        return preparar(alumnoId, curso, periodoId, true);
    }

    private ReporteAvanceDto preparar(Long alumnoId, Long curso, Long periodoId, boolean borrador) {
        var asignacion = asignacion(curso);
        acceso.verificarConsultaInforme(asignacion, alumnoId);
        if (borrador) acceso.verificarAsignacionDocente(asignacion);
        var alumno = alumnos.findByIdAndActivoTrue(alumnoId)
                .orElseThrow(() -> new ResourceNotFoundException("ALUMNO_NO_ENCONTRADO", "Alumno no encontrado"));
        var periodo = periodo(periodoId, asignacion);
        var seccion = asignacion.getSeccion();
        if (Stream.of(EstadoMatricula.ACTIVA, EstadoMatricula.FINALIZADA).noneMatch(estado ->
                matriculas.existsByAlumnoIdAndSeccionIdAndEstadoAndActivoTrue(alumnoId, seccion.getId(), estado))) {
            throw new BusinessRuleException("ALUMNO_CURSO_INVALIDO", "El alumno no tiene matricula vigente o finalizada en esta seccion");
        }
        var informe = ultimo(alumnoId, curso, periodoId, !borrador)
                .orElseThrow(() -> new ResourceNotFoundException("INFORME_NO_ENCONTRADO", "No hay un informe disponible para este periodo"));
        var filas = detalles(informe).stream().map(d -> new ReporteAvanceDto.Fila(d.getNombreCompetencia(),
                d.getTituloEvaluacion(), FECHA.format(d.getFechaEvaluacion()),
                d.getNivelLogro() == null ? "Sin registrar" : d.getNivelLogro().name(),
                d.getPonderacionPorcentaje().stripTrailingZeros().toPlainString() + " %",
                d.getObservacion() == null || d.getObservacion().isBlank() ? "Sin observaciones" : d.getObservacion())).toList();
        return new ReporteAvanceDto(nombre(alumno.getPersona()), alumno.getCodigoAlumno(),
                asignacion.getCurso().getNombre(), nombre(informe.getDocente().getPersona()),
                seccion.getGrado(), seccion.getNombre(), seccion.getAnioAcademico(),
                periodo.getNombre() + " | Version " + informe.getNumeroVersion() + " | " + informe.getEstado(),
                FECHA.format(periodo.getFechaInicio()) + " - " + FECHA.format(periodo.getFechaFin()), Instant.now(), filas);
    }

    private Optional<InformeProgreso> ultimo(Long alumno, Long curso, Long periodo, boolean publicado) {
        return em.createQuery("""
                select i from InformeProgreso i where i.activo = true and i.alumno.id = :alumno
                and i.asignacionCurso.id = :curso and i.periodoAcademico.id = :periodo
                and (:publicado = false or i.estado = com.asiscontrol.entity.enums.EstadoInformeProgreso.PUBLICADA)
                order by i.numeroVersion desc
                """, InformeProgreso.class).setParameter("alumno", alumno).setParameter("curso", curso)
                .setParameter("periodo", periodo).setParameter("publicado", publicado).setMaxResults(1).getResultStream().findFirst();
    }

    private List<DetalleInformeProgreso> detalles(InformeProgreso informe) {
        return em.createQuery("select d from DetalleInformeProgreso d where d.informe = :informe and d.activo = true order by d.nombreCompetencia, d.fechaEvaluacion, d.id",
                DetalleInformeProgreso.class).setParameter("informe", informe).getResultList();
    }

    private AsignacionCurso asignacion(Long id) {
        return asignaciones.findByIdAndActivoTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("ASIGNACION_NO_ENCONTRADA", "Curso no encontrado"));
    }

    private PeriodoAcademico periodo(Long id, AsignacionCurso asignacion) {
        var periodo = periodos.findById(id).filter(p -> p.isActivo() && p.getAnioAcademico().isActivo())
                .orElseThrow(() -> new ResourceNotFoundException("PERIODO_NO_ENCONTRADO", "Periodo no encontrado"));
        if (periodo.getAnioAcademico().getAnio() != asignacion.getSeccion().getAnioAcademico()) {
            throw new BusinessRuleException("PERIODO_CURSO_INVALIDO", "El periodo no pertenece al año del curso");
        }
        return periodo;
    }

    private String nombre(Persona persona) {
        return Stream.of(persona.getNombres(), persona.getApellidoPaterno(), persona.getApellidoMaterno())
                .filter(s -> s != null && !s.isBlank()).collect(Collectors.joining(" "));
    }
}
