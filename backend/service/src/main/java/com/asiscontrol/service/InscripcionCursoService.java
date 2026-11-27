// inscripcion cursos
package com.asiscontrol.service;

import com.asiscontrol.entity.Curso;
import com.asiscontrol.entity.InscripcionCurso;
import com.asiscontrol.entity.Matricula;
import com.asiscontrol.repository.InscripcionCursoRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Inscribe una matrícula en los cursos del plan de estudios de su año y grado, igual que se generan
 * sus cuotas. Es idempotente: solo crea lo que falta y reactiva lo que estaba inactivo, así que
 * reprocesar una matrícula nunca duplica inscripciones (la restricción única es la garantía final).
 * El plan de un año activo no cambia sus cursos, por eso todos los alumnos de un grado llevan lo mismo.
 */
@Service
@Transactional
public class InscripcionCursoService {

    private final InscripcionCursoRepository inscripciones;
    private final PlanEstudiosService planEstudios;
    private final EntityManager entityManager;

    public InscripcionCursoService(InscripcionCursoRepository inscripciones, PlanEstudiosService planEstudios,
            EntityManager entityManager) {
        this.inscripciones = inscripciones;
        this.planEstudios = planEstudios;
        this.entityManager = entityManager;
    }

    public void inscribir(Matricula matricula) {
        var seccion = matricula.getSeccion();
        var existentes = inscripciones.findAllByMatriculaId(matricula.getId()).stream()
                .collect(Collectors.toMap(i -> i.getCurso().getId(), Function.identity()));
        planEstudios.cursosDelPlan(seccion.getAnioAcademico(), seccion.getGrado()).forEach(curso -> {
            var actual = existentes.get(curso.id());
            if (actual == null) {
                inscripciones.save(new InscripcionCurso(matricula, entityManager.getReference(Curso.class, curso.id())));
            } else if (!actual.isActivo()) {
                actual.setActivo(true);
            }
        });
        inscripciones.flush();
    }

    /** La matrícula se retiró o anuló: sus inscripciones quedan inactivas, sin borrarse. */
    public void desactivar(Matricula matricula) {
        inscripciones.findAllByMatriculaId(matricula.getId()).forEach(i -> i.setActivo(false));
        inscripciones.flush();
    }
}
