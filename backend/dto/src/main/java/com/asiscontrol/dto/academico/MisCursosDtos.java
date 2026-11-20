// inscripcion cursos
package com.asiscontrol.dto.academico;

import java.util.List;

/**
 * Cursos del estudiante autenticado. Versión temporal: se calculan desde el plan de estudios de su
 * matrícula activa; luego se leerán de la inscripción guardada, sin cambiar este contrato.
 */
public final class MisCursosDtos {

    private MisCursosDtos() {
    }

    /** anioAcademico y seccion son null cuando el estudiante no tiene una matrícula activa. */
    public record MisCursosResponse(Integer anioAcademico, String seccion, List<CursoInscrito> cursos) {
    }

    /** docente, horario y aula son null mientras la sección no tenga asignación u horario. */
    public record CursoInscrito(Long cursoId, String curso, String docente, String horario, String aula) {
    }
}
