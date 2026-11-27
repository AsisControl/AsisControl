// inscripcion cursos
package com.asiscontrol.repository;

import com.asiscontrol.entity.InscripcionCurso;
import com.asiscontrol.entity.enums.EstadoMatricula;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface InscripcionCursoRepository extends JpaRepository<InscripcionCurso, Long> {

    List<InscripcionCurso> findAllByMatriculaId(Long matriculaId);

    /** Inscripciones activas del estudiante en matrículas con alguno de los estados indicados. */
    @Query("""
            SELECT i FROM InscripcionCurso i
            JOIN FETCH i.matricula m JOIN FETCH m.seccion JOIN FETCH i.curso
            WHERE i.activo = true AND m.activo = true AND m.estado IN :estados
              AND m.alumno.persona.id = :personaId
            ORDER BY m.anioAcademico DESC
            """)
    List<InscripcionCurso> buscarDelEstudiante(@Param("personaId") Long personaId,
            @Param("estados") Collection<EstadoMatricula> estados);
}
