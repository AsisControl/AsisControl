package com.asiscontrol.service;

import com.asiscontrol.dto.academico.PlanEstudiosDtos.CursoPlan;
import com.asiscontrol.dto.academico.PlanEstudiosDtos.CursoDelPlan;
import com.asiscontrol.dto.academico.PlanEstudiosDtos.GradoDelPlan;
import com.asiscontrol.dto.academico.PlanEstudiosDtos.GuardarPlan;
import com.asiscontrol.dto.academico.PlanEstudiosDtos.PlanAnual;
import com.asiscontrol.dto.academico.PlanEstudiosDtos.PlanEstudios;
import com.asiscontrol.entity.Curso;
import com.asiscontrol.entity.GradoAcademico;
import com.asiscontrol.entity.Seccion;
import com.asiscontrol.entity.enums.EstadoAnioAcademico;
import com.asiscontrol.entity.enums.ResultadoAuditoria;
import com.asiscontrol.exception.BusinessRuleException;
import com.asiscontrol.exception.ConflictException;
import com.asiscontrol.exception.ResourceNotFoundException;
import com.asiscontrol.repository.AsignacionCursoRepository;
import com.asiscontrol.repository.CursoRepository;
import com.asiscontrol.repository.GradoAcademicoRepository;
import com.asiscontrol.repository.planificacion.AnioAcademicoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@Transactional(readOnly = true)
public class PlanEstudiosService {
    private final GradoAcademicoRepository grados;
    private final CursoRepository cursos;
    private final AsignacionCursoRepository asignaciones;
    private final AnioAcademicoRepository anios;
    private final AuditoriaService auditoria;

    public PlanEstudiosService(GradoAcademicoRepository grados, CursoRepository cursos,
            AsignacionCursoRepository asignaciones, AnioAcademicoRepository anios, AuditoriaService auditoria) {
        this.grados = grados;
        this.cursos = cursos;
        this.asignaciones = asignaciones;
        this.anios = anios;
        this.auditoria = auditoria;
    }

    public PlanEstudios consultar(int anio, int numero) {
        return mapear(buscarGrado(anio, numero));
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
        return new PlanAnual(anio, actual.getEstado(), actual.getFechaInicio(), actual.getFechaFin(), mapa);
    }

    private GradoDelPlan mapearGradoDelPlan(int numero, GradoAcademico grado) {
        if (grado == null || !grado.isPlanDefinido()) {
            return new GradoDelPlan(numero, false, List.of());
        }
        var lista = cursosVigentes(grado).stream()
                .map(curso -> new CursoDelPlan(curso.getCodigo(), curso.getNombre(), curso.getHorasSemanales()))
                .sorted(Comparator.comparing(CursoDelPlan::nombre).thenComparing(CursoDelPlan::codigo))
                .toList();
        return new GradoDelPlan(numero, true, lista);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PlanEstudios guardar(int anio, int numero, GuardarPlan request) {
        bloquearAnio(anio);
        var grado = buscarGrado(anio, numero);
        if (!esEditable(grado)) {
            throw new ConflictException("PLAN_ESTUDIOS_BLOQUEADO", "El plan del año activo o cerrado es de solo lectura");
        }
        if (grado.getVersion() != request.version()) {
            throw new ConflictException("VERSION_DESACTUALIZADA", "El plan cambió; vuelva a consultar");
        }
        Set<Long> ids = new HashSet<>(request.cursosIds());
        if (ids.size() != request.cursosIds().size()) {
            throw new BusinessRuleException("CURSO_REPETIDO_EN_PLAN", "Cada curso puede aparecer una sola vez");
        }
        if (esInicializacion(grado) && ids.isEmpty()) {
            throw new BusinessRuleException("PLAN_ESTUDIOS_VACIO", "El plan inicial del año activo requiere al menos un curso");
        }
        List<Curso> seleccion = ids.isEmpty() ? List.of() : cursos.bloquearTodos(ids);
        if (seleccion.size() != ids.size() || seleccion.stream().anyMatch(curso -> !curso.isActivo())) {
            throw new BusinessRuleException("CURSO_INVALIDO_EN_PLAN", "Solo se pueden seleccionar cursos activos del catálogo");
        }
        var anterior = cursosVigentes(grado).stream().map(Curso::getId).collect(Collectors.toSet());
        boolean tieneAsignaciones = anterior.stream().filter(id -> !ids.contains(id))
                .anyMatch(id -> asignaciones.existsByCursoIdAndSeccionAnioAcademicoAndSeccionGradoAndActivoTrue(id, anio, numero));
        if (tieneAsignaciones) {
            throw new ConflictException("CURSO_CON_ASIGNACIONES", "Retire las asignaciones activas del curso antes de quitarlo del plan");
        }
        grado.setPlanDefinido(true);
        grado.getCursosPlan().retainAll(seleccion);
        grado.getCursosPlan().addAll(seleccion);
        grados.flush();
        auditoria.registrar("ACTUALIZAR_PLAN_ESTUDIOS", ModuloAuditoria.ACADEMICO, "GradoAcademico",
                grado.getId().toString(), ResultadoAuditoria.EXITOSO, null, anterior.toString(), ids.toString());
        return mapear(grado);
    }

    void comprobarAsignacion(Curso curso, Seccion seccion) {
        if (bloquearAnio(seccion.getAnioAcademico()) == EstadoAnioAcademico.CERRADO) {
            throw new BusinessRuleException("ANIO_NO_VIGENTE", "El año académico está cerrado");
        }
        comprobarPertenencia(curso, seccion);
    }

    // inscripcion cursos: cursos vigentes del plan de un año y grado (los de "Mis cursos")
    public List<Curso> cursosDelPlan(int anio, int numero) {
        return cursosVigentes(buscarGrado(anio, numero));
    }

    void comprobarPertenencia(Curso curso, Seccion seccion) {
        var grado = buscarGrado(seccion.getAnioAcademico(), seccion.getGrado());
        boolean autorizado = cursosVigentes(grado).stream().anyMatch(c -> c.getId().equals(curso.getId()));
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

    private List<Curso> cursosVigentes(GradoAcademico grado) {
        if (!grado.isPlanDefinido() && esBorrador(grado)) {
            return cursos.findAllByGradoAcademicoIdAndActivoTrue(grado.getId());
        }
        return grado.getCursosPlan().stream().filter(Curso::isActivo).toList();
    }

    private PlanEstudios mapear(GradoAcademico grado) {
        var anio = grado.getAnioAcademico();
        var items = cursosVigentes(grado).stream()
                .map(curso -> new CursoPlan(curso.getId(), curso.getCodigo(), curso.getNombre()))
                .sorted(Comparator.comparing(CursoPlan::nombre).thenComparing(CursoPlan::id)).toList();
        return new PlanEstudios(anio.getAnio(), grado.getNumero(), anio.getEstado(), esEditable(grado),
                esInicializacion(grado), grado.getVersion(), items);
    }

    private boolean esEditable(GradoAcademico grado) {
        return grado.getAnioAcademico().isActivo() && (esBorrador(grado) || esInicializacion(grado));
    }

    private boolean esBorrador(GradoAcademico grado) {
        return grado.getAnioAcademico().getEstado() == EstadoAnioAcademico.BORRADOR;
    }

    private boolean esInicializacion(GradoAcademico grado) {
        return grado.getAnioAcademico().getEstado() == EstadoAnioAcademico.ACTIVO && !grado.isPlanDefinido();
    }
}
