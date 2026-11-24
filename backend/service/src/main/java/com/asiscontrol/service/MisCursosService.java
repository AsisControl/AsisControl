// inscripcion cursos
package com.asiscontrol.service;

import com.asiscontrol.dto.academico.MisCursosDtos.CursoInscrito;
import com.asiscontrol.dto.academico.MisCursosDtos.MisCursosResponse;
import com.asiscontrol.dto.academico.PlanEstudiosDtos.CursoPlan;
import com.asiscontrol.entity.AsignacionCurso;
import com.asiscontrol.entity.Horario;
import com.asiscontrol.entity.Matricula;
import com.asiscontrol.entity.Seccion;
import com.asiscontrol.entity.enums.DiaSemana;
import com.asiscontrol.entity.enums.EstadoMatricula;
import com.asiscontrol.repository.AsignacionCursoRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Collator;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * "Mis cursos" del estudiante. SOLUCIÓN TEMPORAL: la inscripción no se guarda todavía; los cursos
 * son los del plan de estudios del grado y año de su matrícula activa. Como el plan es el mismo para
 * todas las secciones del grado, la sección solo aporta el docente, el horario y el aula de cada curso.
 * Al formalizarla con una migración, este servicio leerá la inscripción y el contrato no cambia.
 */
@Service
@Transactional(readOnly = true)
public class MisCursosService {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final EntityManager entityManager;
    private final PlanEstudiosService planEstudiosService;
    private final AsignacionCursoRepository asignacionRepository;
    private final HorarioService horarioService;
    private final ModuloAccessGuard accessGuard;

    public MisCursosService(
            EntityManager entityManager,
            PlanEstudiosService planEstudiosService,
            AsignacionCursoRepository asignacionRepository,
            HorarioService horarioService,
            ModuloAccessGuard accessGuard) {
        this.entityManager = entityManager;
        this.planEstudiosService = planEstudiosService;
        this.asignacionRepository = asignacionRepository;
        this.horarioService = horarioService;
        this.accessGuard = accessGuard;
    }

    public MisCursosResponse listar() {
        Matricula matricula = entityManager.createQuery("""
                        SELECT m FROM Matricula m
                        WHERE m.activo = true AND m.estado = :estado AND m.alumno.persona.id = :personaId
                        ORDER BY m.anioAcademico DESC, m.fechaMatricula DESC
                        """, Matricula.class)
                .setParameter("estado", EstadoMatricula.ACTIVA)
                .setParameter("personaId", accessGuard.personaIdActual())
                .setMaxResults(1)
                .getResultStream()
                .findFirst()
                .orElse(null);
        if (matricula == null) {
            return new MisCursosResponse(null, null, List.of());
        }
        Seccion seccion = matricula.getSeccion();
        Collator collator = Collator.getInstance(Locale.of("es"));
        List<CursoInscrito> cursos = planEstudiosService
                .cursosDelPlan(seccion.getAnioAcademico(), seccion.getGrado()).stream()
                .sorted(Comparator.comparing(CursoPlan::nombre, collator))
                .map(curso -> inscrito(curso, seccion))
                .toList();
        return new MisCursosResponse(
                seccion.getAnioAcademico(),
                seccion.getGrado() + "° Grado " + seccion.getNombre(),
                cursos);
    }

    private CursoInscrito inscrito(CursoPlan curso, Seccion seccion) {
        AsignacionCurso asignacion = asignacionRepository
                .findByCursoIdAndSeccionIdAndActivoTrue(curso.id(), seccion.getId())
                .orElse(null);
        if (asignacion == null) {
            return new CursoInscrito(curso.id(), curso.nombre(), null, null, null);
        }
        List<Horario> bloques = horarioService.listarActivosPorAsignacion(asignacion.getId()).stream()
                .sorted(Comparator.comparing(Horario::getDiaSemana).thenComparing(Horario::getHoraInicio))
                .toList();
        String horario = bloques.isEmpty() ? null : bloques.stream()
                .map(b -> dia(b.getDiaSemana()) + " " + b.getHoraInicio().format(HORA) + "–" + b.getHoraFin().format(HORA))
                .collect(Collectors.joining(" · "));
        String aula = bloques.isEmpty() ? null : bloques.stream()
                .map(b -> b.getAula().getNombre())
                .distinct()
                .collect(Collectors.joining(", "));
        return new CursoInscrito(
                curso.id(),
                curso.nombre(),
                asignacion.getDocente().getPersona().nombreCompleto(),
                horario,
                aula);
    }

    private static String dia(DiaSemana dia) {
        return switch (dia) {
            case LUNES -> "Lun";
            case MARTES -> "Mar";
            case MIERCOLES -> "Mié";
            case JUEVES -> "Jue";
            case VIERNES -> "Vie";
            case SABADO -> "Sáb";
        };
    }
}
