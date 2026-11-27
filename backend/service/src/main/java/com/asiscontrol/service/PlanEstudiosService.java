package com.asiscontrol.service;

import com.asiscontrol.dto.academico.PlanEstudiosDtos.CursoPlan;
import com.asiscontrol.dto.academico.PlanEstudiosDtos.CompetenciaPlan;
import com.asiscontrol.dto.academico.PlanEstudiosDtos.CursoDelPlan;
import com.asiscontrol.dto.academico.PlanEstudiosDtos.CursoEnPlanRequest;
import com.asiscontrol.dto.academico.PlanEstudiosDtos.GradoDelPlan;
import com.asiscontrol.dto.academico.PlanEstudiosDtos.GuardarPlan;
import com.asiscontrol.dto.academico.PlanEstudiosDtos.IdentidadPlan;
import com.asiscontrol.dto.academico.PlanEstudiosDtos.RenombrarPlan;
import com.asiscontrol.dto.academico.PlanEstudiosDtos.PlanAnual;
import com.asiscontrol.dto.academico.PlanEstudiosDtos.PlanAnterior;
import com.asiscontrol.dto.academico.PlanEstudiosDtos.PlanEstudios;
import com.asiscontrol.dto.academico.PlanEstudiosDtos.SeccionExcedida;
import com.asiscontrol.entity.Curso;
import com.asiscontrol.entity.Competencia;
import com.asiscontrol.entity.CompetenciaEnPlan;
import com.asiscontrol.entity.AsignacionCurso;
import com.asiscontrol.entity.CursoEnPlan;
import com.asiscontrol.entity.GradoAcademico;
import com.asiscontrol.entity.Horario;
import com.asiscontrol.entity.Seccion;
import com.asiscontrol.entity.enums.EstadoAnioAcademico;
import com.asiscontrol.entity.enums.ResultadoAuditoria;
import com.asiscontrol.exception.BusinessRuleException;
import com.asiscontrol.exception.ConflictException;
import com.asiscontrol.exception.ResourceNotFoundException;
import com.asiscontrol.repository.AsignacionCursoRepository;
import com.asiscontrol.repository.CursoRepository;
import com.asiscontrol.repository.CompetenciaRepository;
import com.asiscontrol.repository.CursoCompetenciaRepository;
import com.asiscontrol.repository.GradoAcademicoRepository;
import com.asiscontrol.repository.HorarioRepository;
import com.asiscontrol.repository.planificacion.AnioAcademicoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.util.Comparator;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@Transactional(readOnly = true)
public class PlanEstudiosService {
    private final GradoAcademicoRepository grados;
    private final CursoRepository cursos;
    private final CursoCompetenciaRepository asociaciones;
    private final CompetenciaRepository competencias;
    private final AsignacionCursoRepository asignaciones;
    private final AnioAcademicoRepository anios;
    private final AuditoriaService auditoria;
    private final HorarioRepository horarios;

    public PlanEstudiosService(GradoAcademicoRepository grados, CursoRepository cursos,
            CursoCompetenciaRepository asociaciones, CompetenciaRepository competencias,
                               AsignacionCursoRepository asignaciones, AnioAcademicoRepository anios, AuditoriaService auditoria,
                               HorarioRepository horarios) {
        this.grados = grados;
        this.cursos = cursos;
        this.asociaciones = asociaciones;
        this.competencias = competencias;
        this.asignaciones = asignaciones;
        this.anios = anios;
        this.auditoria = auditoria;
        this.horarios = horarios;
    }

    public PlanEstudios consultar(int anio, int numero) {
        return mapear(buscarGrado(anio, numero));
    }

    public PlanAnterior consultarAnterior(int anio, int numero) {
        buscarGrado(anio, numero);
        int anterior = anio - 1;
        var lista = grados.findByAnioAcademicoAnioAndNumero(anterior, numero)
                .filter(g -> g.isActivo() && g.getAnioAcademico().isActivo() && g.isPlanDefinido())
                .map(this::cursosVigentes).orElseGet(List::of);
        return new PlanAnterior(anterior, lista);
    }

    public IdentidadPlan identidad(int anio) {
        return identidad(anios.findByAnio(anio).filter(a -> a.isActivo())
                .orElseThrow(() -> new ResourceNotFoundException("ANIO_NO_ENCONTRADO", "El año académico no está habilitado")));
    }

    @Transactional
    public IdentidadPlan renombrar(int anio, RenombrarPlan request) {
        var actual = anios.bloquear(anio).filter(a -> a.isActivo())
                .orElseThrow(() -> new ResourceNotFoundException("ANIO_NO_ENCONTRADO", "El año académico no está habilitado"));
        var anterior = identidad(actual);
        if (anterior.version() != request.version()) {
            throw new ConflictException("VERSION_DESACTUALIZADA", "El año cambió. Vuelve a consultar el plan.");
        }
        String nombre = request.nombre().strip();
        if (!anterior.nombre().equals(nombre)) {
            actual.setNombrePlanEstudios(nombre);
            anios.flush();
            auditoria.registrar("RENOMBRAR_PLAN_ESTUDIOS", ModuloAuditoria.ACADEMICO, "AnioAcademico",
                    actual.getId().toString(), ResultadoAuditoria.EXITOSO, null, anterior.nombre(), nombre);
        }
        return identidad(actual);
    }

    private IdentidadPlan identidad(com.asiscontrol.entity.planificacion.AnioAcademico anio) {
        String nombre = anio.getNombrePlanEstudios() == null ? "Plan de estudios " + anio.getAnio() : anio.getNombrePlanEstudios();
        return new IdentidadPlan(nombre, anio.getVersion());
    }

    public PlanAnual consultarPlanAnual(int anio) {
        var actual = anios.findByAnio(anio).filter(a -> a.isActivo())
                .orElseThrow(() -> new ResourceNotFoundException("ANIO_NO_ENCONTRADO", "El año académico no está habilitado"));
        var gradosGuardados = grados.findByAnioAcademicoAnioOrderByNumero(anio).stream()
                .filter(GradoAcademico::isActivo)
                .collect(Collectors.toMap(GradoAcademico::getNumero, grado -> grado));
        var mapa = IntStream.rangeClosed(1, 5)
                .mapToObj(numero -> mapearGradoDelPlan(numero, gradosGuardados.get(numero)))
                .toList();
        return new PlanAnual(anio, actual.getEstado(), actual.getFechaInicio(), actual.getFechaFin(), mapa, identidad(actual).nombre());
    }

    private GradoDelPlan mapearGradoDelPlan(int numero, GradoAcademico grado) {
        if (grado == null || !grado.isPlanDefinido()) {
            return new GradoDelPlan(numero, false, List.of());
        }
        var lista = cursosVigentes(grado).stream()
                .map(curso -> new CursoDelPlan(curso.codigo(), curso.nombre(), curso.horasSemanales()))
                .sorted(Comparator.comparing(CursoDelPlan::nombre).thenComparing(CursoDelPlan::codigo))
                .toList();
        return new GradoDelPlan(numero, true, lista);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PlanEstudios guardar(int anio, int numero, GuardarPlan request) {
        var estado = bloquearAnio(anio);
        var grado = buscarGrado(anio, numero);
        if (!esEditable(grado)) {
            throw new ConflictException("PLAN_ESTUDIOS_BLOQUEADO", "Los cursos del plan de un año cerrado son de solo lectura");
        }
        if (grado.getVersion() != request.version()) {
            throw new ConflictException("VERSION_DESACTUALIZADA", "El plan cambió; vuelva a consultar");
        }
        if ((request.cursosIds() == null) == (request.cursos() == null)) {
            throw new BusinessRuleException("CURSOS_PLAN_REQUERIDOS", "Envía una lista de cursos para el plan.");
        }
        var enviados = request.cursos() == null ? request.cursosIds() : request.cursos().stream().map(CursoEnPlanRequest::id).toList();
        Set<Long> ids = new HashSet<>(enviados);
        if (ids.size() != enviados.size()) {
            throw new BusinessRuleException("CURSO_REPETIDO_EN_PLAN", "Cada curso puede aparecer una sola vez");
        }
        if (estado == EstadoAnioAcademico.ACTIVO && ids.isEmpty()) {
            throw new BusinessRuleException("PLAN_ESTUDIOS_VACIO", "El plan del año activo requiere al menos un curso");
        }
        var cursosAnteriores = cursosVigentes(grado);
        var anterior = cursosAnteriores.stream().collect(Collectors.toMap(CursoPlan::id,
                curso -> new CursoEnPlan(curso.nombre(), curso.horasSemanales())));
        List<Curso> catalogo = ids.isEmpty() ? List.of() : cursos.bloquearTodos(ids);
        if (catalogo.size() != ids.size() || catalogo.stream().anyMatch(curso -> !curso.isActivo())) {
            throw new BusinessRuleException("CURSO_INVALIDO_EN_PLAN", "Solo se pueden seleccionar cursos activos del catálogo");
        }
        var seleccion = seleccionarCursos(catalogo, anterior, request.cursos());
        var competenciasAnteriores = grado.isPlanDefinido() ? competenciasGuardadas(grado) : Map.<Long, List<Competencia>>of();
        var competenciasSeleccionadas = seleccionarCompetencias(ids,
                grado.isPlanDefinido() ? anterior.keySet() : Set.of(), competenciasAnteriores, request.cursos());
        if (grado.isPlanDefinido() && catalogo.stream().allMatch(c -> seleccion.get(c).equals(anterior.get(c.getId())))
                && anterior.size() == seleccion.size()
                && ids.stream().allMatch(id -> idsCompetencias(competenciasAnteriores.getOrDefault(id, List.of()))
                        .equals(idsCompetencias(competenciasSeleccionadas.get(id))))) {
            return mapear(grado);
        }
        // inscripcion cursos: un grado lleva los mismos cursos todo el año; competencias y horas siguen editables
        if (estado == EstadoAnioAcademico.ACTIVO && grado.isPlanDefinido() && !anterior.keySet().equals(ids)) {
            throw new ConflictException("PLAN_ANIO_ACTIVO_BLOQUEADO",
                    "Los cursos del plan del año en curso no se modifican; aplica el cambio en el plan del próximo año.");
        }

        String motivo = request.motivo() == null ? null : request.motivo().strip();
        if (grado.isPlanDefinido() && (motivo == null || motivo.isBlank())) {
            throw new BusinessRuleException("MOTIVO_EDICION_PLAN_REQUERIDO",
                    "Ingresa el motivo de la modificación del plan.", "motivo");
        }
        validarGradoCursosNuevos(catalogo, anterior.keySet(), numero);
        boolean tieneAsignaciones = anterior.keySet().stream().filter(id -> !ids.contains(id))
                .anyMatch(id -> asignaciones.existsByCursoIdAndSeccionAnioAcademicoAndSeccionGradoAndActivoTrue(id, anio, numero));
        if (tieneAsignaciones) {
            throw new ConflictException("CURSO_CON_ASIGNACIONES", "Retire las asignaciones activas del curso antes de quitarlo del plan");
        }
        grado.setPlanDefinido(true);
        grado.getCursosPlan().keySet().retainAll(seleccion.keySet());
        grado.getCursosPlan().putAll(seleccion);
        var vinculos = competenciasSeleccionadas.entrySet().stream()
                .flatMap(item -> item.getValue().stream().map(competencia -> new CompetenciaEnPlan(item.getKey(), competencia)))
                .collect(Collectors.toSet());
        grado.getCompetenciasPlan().retainAll(vinculos);
        grado.getCompetenciasPlan().addAll(vinculos);
        grados.flush();
        auditoria.registrar("ACTUALIZAR_PLAN_ESTUDIOS", ModuloAuditoria.ACADEMICO, "GradoAcademico",
                grado.getId().toString(), ResultadoAuditoria.EXITOSO, motivo,
                describirCursos(anio, numero, cursosAnteriores), describirCursos(anio, numero, cursosVigentes(grado)));
        return mapear(grado);
    }

    private Map<Long, List<Competencia>> seleccionarCompetencias(Set<Long> cursosIds, Set<Long> cursosAnteriores,
            Map<Long, List<Competencia>> anteriores, List<CursoEnPlanRequest> enviados) {
        var explicitas = enviados == null ? Map.<Long, List<Long>>of() : enviados.stream()
                .filter(item -> item.competenciasIds() != null)
                .collect(Collectors.toMap(CursoEnPlanRequest::id, CursoEnPlanRequest::competenciasIds));
        var conPlantilla = cursosIds.stream()
                .filter(id -> !explicitas.containsKey(id) && !cursosAnteriores.contains(id)).toList();
        var base = competenciasBase(conPlantilla);
        var idsAnteriores = anteriores.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, item -> idsCompetencias(item.getValue())));
        var nuevasIds = explicitas.entrySet().stream()
                .flatMap(item -> item.getValue().stream()
                        .filter(id -> !idsAnteriores.getOrDefault(item.getKey(), Set.of()).contains(id)))
                .collect(Collectors.toSet());
        var nuevas = nuevasIds.isEmpty() ? Map.<Long, Competencia>of()
                : competencias.findAllByIdInAndActivoTrue(nuevasIds).stream()
                        .collect(Collectors.toMap(Competencia::getId, competencia -> competencia));
        if (nuevas.size() != nuevasIds.size()) {
            throw new BusinessRuleException("COMPETENCIA_INVALIDA_EN_PLAN", "Selecciona competencias activas del catálogo.");
        }
        var seleccion = new HashMap<Long, List<Competencia>>();
        for (Long cursoId : cursosIds) {
            var previas = anteriores.getOrDefault(cursoId, List.of());
            var lista = explicitas.get(cursoId);
            if (lista == null) {
                seleccion.put(cursoId, cursosAnteriores.contains(cursoId) ? previas : base.getOrDefault(cursoId, List.of()));
                continue;
            }
            if (new HashSet<>(lista).size() != lista.size()) {
                throw new BusinessRuleException("COMPETENCIA_REPETIDA_EN_PLAN", "Cada competencia puede aparecer una vez por curso.");
            }
            var vigentes = previas.stream().collect(Collectors.toMap(Competencia::getId, competencia -> competencia));
            seleccion.put(cursoId, lista.stream().map(id -> vigentes.getOrDefault(id, nuevas.get(id))).toList());
        }
        return seleccion;
    }

    private Set<Long> idsCompetencias(List<Competencia> lista) {
        return lista.stream().map(Competencia::getId).collect(Collectors.toSet());
    }

    Map<Long, List<Competencia>> competenciasBase(Collection<Long> cursosIds) {
        if (cursosIds.isEmpty()) return Map.of();
        return asociaciones.buscarCompetenciasBase(cursosIds).stream()
                .collect(Collectors.groupingBy(item -> item.getCurso().getId(),
                        Collectors.mapping(item -> item.getCompetencia(), Collectors.toList())));
    }

    private Map<Long, List<Competencia>> competenciasGuardadas(GradoAcademico grado) {
        return grado.getCompetenciasPlan().stream()
                .collect(Collectors.groupingBy(CompetenciaEnPlan::cursoId,
                        Collectors.mapping(CompetenciaEnPlan::competencia, Collectors.toList())));
    }

    List<Competencia> competenciasDeAsignacion(AsignacionCurso asignacion) {
        Long cursoId = asignacion.getCurso().getId();
        var seccion = asignacion.getSeccion();
        return grados.findByAnioAcademicoAnioAndNumero(seccion.getAnioAcademico(), seccion.getGrado())
                .filter(GradoAcademico::isPlanDefinido)
                .map(grado -> grado.getCompetenciasPlan().stream().filter(item -> item.cursoId().equals(cursoId))
                        .map(CompetenciaEnPlan::competencia).filter(Competencia::isActivo)
                        .sorted(Comparator.comparing(Competencia::getCodigo)).toList())
                .orElseGet(() -> competenciasBase(List.of(cursoId)).getOrDefault(cursoId, List.of()));
    }

    private Map<Curso, CursoEnPlan> seleccionarCursos(List<Curso> catalogo, Map<Long, CursoEnPlan> anterior,
            List<CursoEnPlanRequest> enviados) {
        var datos = enviados == null ? anterior : enviados.stream().collect(Collectors.toMap(CursoEnPlanRequest::id,
                item -> new CursoEnPlan(item.nombre().strip(), item.horasSemanales())));
        var seleccion = new HashMap<Curso, CursoEnPlan>();
        for (Curso curso : catalogo) {
            CursoEnPlan detalle = datos.get(curso.getId());
            seleccion.put(curso, detalle == null ? CursoEnPlan.desdeCatalogo(curso) : detalle);
        }
        return seleccion;
    }

    private void validarGradoCursosNuevos(List<Curso> catalogo, Set<Long> existentes, int numero) {
        var nuevos = catalogo.stream().filter(curso -> !existentes.contains(curso.getId())).toList();
        if (nuevos.isEmpty()) return;
        var idsNuevos = nuevos.stream().map(Curso::getId).collect(Collectors.toSet());
        var gradosPorCurso = grados.gradosDeCursos(idsNuevos).stream()
                .collect(Collectors.groupingBy(GradoAcademicoRepository.GradoDeCurso::getCursoId,
                        Collectors.mapping(GradoAcademicoRepository.GradoDeCurso::getGrado, Collectors.toSet())));
        for (Curso curso : nuevos) {
            var usados = gradosPorCurso.getOrDefault(curso.getId(), Set.of());
            var origen = curso.getGradoAcademico();
            if (usados.contains(numero) || (origen == null ? usados.isEmpty() : origen.getNumero() == numero)) continue;
            throw new BusinessRuleException("CURSO_DE_OTRO_GRADO",
                    "El curso «" + curso.getNombre() + "» pertenece a otro grado. Selecciona el curso de este grado.");
        }
    }

    private String describirCursos(int anio, int grado, List<CursoPlan> lista) {
        String detalle = lista.isEmpty() ? "Sin cursos" : lista.stream()
                .sorted(Comparator.comparing(CursoPlan::codigo))
                .map(curso -> curso.codigo() + " · " + curso.nombre() + " · " + curso.horasSemanales() + " h/semana"
                        + " · Competencias: " + (curso.competencias().isEmpty() ? "ninguna" : curso.competencias().stream()
                                .map(CompetenciaPlan::codigo).collect(Collectors.joining(", "))))
                .collect(Collectors.joining("\n"));
        return anio + " · " + grado + ".° grado\n" + detalle;
    }

    void comprobarAsignacion(Curso curso, Seccion seccion) {
        if (bloquearAnio(seccion.getAnioAcademico()) == EstadoAnioAcademico.CERRADO) {
            throw new BusinessRuleException("ANIO_NO_VIGENTE", "El año académico está cerrado");
        }
        comprobarPertenencia(curso, seccion);
    }

    // inscripcion cursos: cursos vigentes del plan de un año y grado (los que se inscriben al matricular)
    public List<CursoPlan> cursosDelPlan(int anio, int numero) {
        return cursosVigentes(buscarGrado(anio, numero));
    }

    void comprobarPertenencia(Curso curso, Seccion seccion) {
        var grado = buscarGrado(seccion.getAnioAcademico(), seccion.getGrado());
        boolean autorizado = cursosVigentes(grado).stream().anyMatch(c -> c.id().equals(curso.getId()));
        if (!autorizado) {
            var contexto = curso.getGradoAcademico();
            if (!grado.isPlanDefinido() && contexto != null && !contexto.getId().equals(grado.getId())) {
                throw new ConflictException("CURSO_DE_OTRO_GRADO", "El curso no pertenece al plan del año y grado de esta sección");
            }
            throw new ConflictException("CURSO_FUERA_DEL_PLAN", "El curso no pertenece al plan del año y grado de esta sección");
        }
    }

    private EstadoAnioAcademico bloquearAnio(int anio) {
        var actual = anios.bloquear(anio).filter(a -> a.isActivo())
                .orElseThrow(() -> new ResourceNotFoundException("ANIO_NO_ENCONTRADO", "El año académico no está habilitado"));
        return actual.getEstado();
    }

    private GradoAcademico buscarGrado(int anio, int numero) {
        return grados.findByAnioAcademicoAnioAndNumero(anio, numero).filter(GradoAcademico::isActivo)
                .orElseThrow(() -> new ResourceNotFoundException("GRADO_NO_ENCONTRADO", "El grado no está habilitado para este año"));
    }

    private List<CursoPlan> cursosVigentes(GradoAcademico grado) {
        boolean borradorInicial = !grado.isPlanDefinido() && esBorrador(grado);
        var detalles = borradorInicial ? cursos.findAllByGradoAcademicoIdAndActivoTrue(grado.getId()).stream()
                .collect(Collectors.toMap(curso -> curso, CursoEnPlan::desdeCatalogo)) : grado.getCursosPlan();
        var porCurso = borradorInicial || !grado.isPlanDefinido()
                ? competenciasBase(detalles.keySet().stream().map(Curso::getId).toList()) : competenciasGuardadas(grado);
        var lista = detalles.entrySet().stream().filter(item -> item.getKey().isActivo())
                .map(item -> cursoPlan(item.getKey(), item.getValue(), porCurso.getOrDefault(item.getKey().getId(), List.of())));
        return lista.sorted(Comparator.comparing(CursoPlan::nombre).thenComparing(CursoPlan::id)).toList();
    }

    private CursoPlan cursoPlan(Curso curso, CursoEnPlan detalle, List<Competencia> lista) {
        var seleccion = lista.stream().sorted(Comparator.comparing(Competencia::getCodigo))
                .map(competencia -> new CompetenciaPlan(competencia.getId(), competencia.getCodigo(),
                        competencia.getNombre(), competencia.isActivo())).toList();
        return new CursoPlan(curso.getId(), curso.getCodigo(), detalle.nombre(), detalle.horasSemanales(), seleccion);
    }

    private PlanEstudios mapear(GradoAcademico grado) {
        var anio = grado.getAnioAcademico();
        var cursosPlan = cursosVigentes(grado);
        return new PlanEstudios(anio.getAnio(), grado.getNumero(), anio.getEstado(), esEditable(grado),
                esInicializacion(grado), grado.isPlanDefinido(), grado.getVersion(), cursosPlan,
                seccionesExcedidas(anio.getAnio(), grado.getNumero(), cursosPlan));
    }

    /**
     * OBS-01: horas semanales que el plan vigente del año y grado de la sección asigna al curso. Sigue la
     * misma regla que {@link #cursosVigentes}: en un borrador sin plan definido manda el catálogo del grado.
     */
    public OptionalInt horasDelPlan(Curso curso, Seccion seccion) {
        var encontrado = grados.findByAnioAcademicoAnioAndNumero(seccion.getAnioAcademico(), seccion.getGrado())
                .filter(GradoAcademico::isActivo);
        if (encontrado.isEmpty() || !curso.isActivo()) {
            return OptionalInt.empty();
        }
        var grado = encontrado.get();
        if (!grado.isPlanDefinido() && esBorrador(grado)) {
            var contexto = curso.getGradoAcademico();
            return contexto != null && contexto.getId().equals(grado.getId())
                    ? OptionalInt.of(curso.getHorasSemanales()) : OptionalInt.empty();
        }
        return grado.getCursosPlan().entrySet().stream()
                .filter(item -> item.getKey().getId().equals(curso.getId()))
                .mapToInt(item -> item.getValue().horasSemanales())
                .findFirst();
    }

    /**
     * OBS-01: secciones del grado cuyo horario de un curso supera las horas del plan. Reducir el plan no
     * borra bloques: se guarda y se informa qué secciones ajustar.
     */
    private List<SeccionExcedida> seccionesExcedidas(int anio, int numero, List<CursoPlan> cursosPlan) {
        Map<Long, CursoPlan> porCurso = cursosPlan.stream().collect(Collectors.toMap(CursoPlan::id, curso -> curso));
        Map<List<Long>, List<Horario>> grupos = horarios.findActivosDeGrado(anio, numero).stream()
                .filter(bloque -> porCurso.containsKey(bloque.getAsignacionCurso().getCurso().getId()))
                .collect(Collectors.groupingBy(bloque -> List.of(
                                bloque.getAsignacionCurso().getSeccion().getId(),
                                bloque.getAsignacionCurso().getCurso().getId()),
                        LinkedHashMap::new, Collectors.toList()));
        return grupos.values().stream().map(bloques -> {
                    var asignacion = bloques.getFirst().getAsignacionCurso();
                    var curso = porCurso.get(asignacion.getCurso().getId());
                    long programados = bloques.stream().mapToLong(HorarioService::minutos).sum();
                    return new SeccionExcedida(asignacion.getSeccion().getId(),
                            AsignacionCursoService.etiquetaSeccion(asignacion.getSeccion()), curso.id(), curso.nombre(),
                            curso.horasSemanales(), programados);
                }).filter(item -> item.minutosProgramados() > (long) item.horasPlan() * HorarioService.MINUTOS_POR_HORA)
                .toList();
    }

    private boolean esEditable(GradoAcademico grado) {
        var anio = grado.getAnioAcademico();
        return anio.isActivo() && anio.getEstado() != EstadoAnioAcademico.CERRADO;
    }

    private boolean esBorrador(GradoAcademico grado) {
        return grado.getAnioAcademico().getEstado() == EstadoAnioAcademico.BORRADOR;
    }

    private boolean esInicializacion(GradoAcademico grado) {
        return grado.getAnioAcademico().getEstado() == EstadoAnioAcademico.ACTIVO && !grado.isPlanDefinido();
    }
}
