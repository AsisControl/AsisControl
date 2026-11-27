// inscripcion cursos
package com.asiscontrol.dto.academico;

import java.util.List;

/** Cursos en los que está inscrito el estudiante autenticado, del año vigente y de años anteriores. */
public final class MisCursosDtos {

    private MisCursosDtos() {
    }

    public record MisCursosResponse(List<CursoInscrito> cursos) {
    }

    /**
     * docente, horario y aula son null mientras la sección no tenga asignación u horario. vigente es
     * true para el año ACTIVO y false para un año CERRADO.
     */
    public record CursoInscrito(Long cursoId, String curso, String docente, String horario, String aula,
                                int anioAcademico, String seccion, boolean vigente) {
    }
}
