package com.asiscontrol.service;

import com.asiscontrol.entity.AsignacionCurso;
import com.asiscontrol.entity.Aula;
import com.asiscontrol.entity.Curso;
import com.asiscontrol.entity.Docente;
import com.asiscontrol.entity.Seccion;
import com.asiscontrol.exception.BusinessRuleException;
import com.asiscontrol.repository.AsignacionCursoRepository;
import com.asiscontrol.repository.AulaRepository;
import com.asiscontrol.repository.CursoRepository;
import com.asiscontrol.repository.DocenteRepository;
import com.asiscontrol.repository.SeccionRepository;
import org.springframework.stereotype.Component;

/**
 * OBS-05: las plantillas identifican los registros por códigos legibles (código de curso, de
 * docente o de aula; año, grado y nombre de sección), nunca por ids internos. Cada búsqueda
 * fallida indica la columna que hay que corregir.
 */
@Component
class ReferenciasImportacion {

    private final SeccionRepository secciones;
    private final CursoRepository cursos;
    private final DocenteRepository docentes;
    private final AulaRepository aulas;
    private final AsignacionCursoRepository asignaciones;

    ReferenciasImportacion(
            SeccionRepository secciones,
            CursoRepository cursos,
            DocenteRepository docentes,
            AulaRepository aulas,
            AsignacionCursoRepository asignaciones) {
        this.secciones = secciones;
        this.cursos = cursos;
        this.docentes = docentes;
        this.aulas = aulas;
        this.asignaciones = asignaciones;
    }

    /** Sección activa a partir de las columnas anioAcademico, grado y seccion. */
    Seccion seccion(LectorExcel.Fila fila) {
        int anio = fila.entero("anioAcademico");
        int grado = fila.entero("grado");
        String nombre = fila.obligatorio("seccion");
        return secciones.findByAnioAcademicoAndGradoAndNombreIgnoreCase(anio, grado, nombre)
                .filter(Seccion::isActivo)
                .orElseThrow(() -> new BusinessRuleException(
                        "SECCION_NO_ENCONTRADA",
                        "No hay una sección activa " + grado + "° " + nombre + " en " + anio,
                        "seccion"));
    }

    Curso curso(String codigo) {
        return cursos.findByCodigoIgnoreCaseAndActivoTrue(codigo)
                .orElseThrow(() -> new BusinessRuleException(
                        "CURSO_NO_ENCONTRADO",
                        "No hay un curso activo con el código " + codigo,
                        "codigoCurso"));
    }

    Docente docente(String codigo) {
        return docentes.findByCodigoDocenteIgnoreCase(codigo)
                .filter(Docente::isActivo)
                .orElseThrow(() -> new BusinessRuleException(
                        "DOCENTE_NO_ENCONTRADO",
                        "No hay un docente activo con el código " + codigo,
                        "codigoDocente"));
    }

    Aula aula(String codigo) {
        return aulas.findByCodigoIgnoreCaseAndActivoTrue(codigo.trim().replaceAll("\\s+", " "))
                .orElseThrow(() -> new BusinessRuleException(
                        "AULA_NO_ENCONTRADA",
                        "No hay un aula habilitada con el código " + codigo,
                        "codigoAula"));
    }

    /** Asignación activa del curso en la sección: el horario se programa sobre ella. */
    AsignacionCurso asignacion(Curso curso, Seccion seccion) {
        return asignaciones.findByCursoIdAndSeccionIdAndActivoTrue(curso.getId(), seccion.getId())
                .orElseThrow(() -> new BusinessRuleException(
                        "ASIGNACION_NO_ENCONTRADA",
                        "El curso " + curso.getCodigo() + " no tiene docente asignado en "
                                + seccion.getGrado() + "° " + seccion.getNombre()
                                + "; cárguelo primero en asignaciones docentes",
                        "codigoCurso"));
    }

    boolean asignado(Curso curso, Seccion seccion) {
        return asignaciones.findByCursoIdAndSeccionIdAndActivoTrue(curso.getId(), seccion.getId())
                .isPresent();
    }
}
