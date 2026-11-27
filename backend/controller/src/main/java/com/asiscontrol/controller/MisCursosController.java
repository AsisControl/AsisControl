// inscripcion cursos
package com.asiscontrol.controller;

import com.asiscontrol.dto.academico.MisCursosDtos.MisCursosResponse;
import com.asiscontrol.dto.asistencia.PeriodoCurso;
import com.asiscontrol.service.MisCursosService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Cursos del estudiante autenticado (VIGENTES por defecto, PASADOS o TODOS); nunca recibe un id. */
@RestController
@RequestMapping("/api/alumnos/me/cursos")
@PreAuthorize("hasRole('ALUMNO') and hasAuthority('ACADEMICO_LEER')")
public class MisCursosController {

    private final MisCursosService misCursosService;

    public MisCursosController(MisCursosService misCursosService) {
        this.misCursosService = misCursosService;
    }

    @GetMapping
    public MisCursosResponse listar(@RequestParam(defaultValue = "VIGENTES") PeriodoCurso periodo) {
        return misCursosService.listar(periodo);
    }
}
