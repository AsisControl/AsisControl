package com.asiscontrol.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.time.*;
import java.util.*;

class BackendErroresHttpTest extends ApiIntegrationSupport {
    @ParameterizedTest
    @ValueSource(strings={"alumnos","docentes","apoderados","usuarios","aulas","competencias","cursos",
            "cursos-competencias","asignaciones-cursos","horarios","tareas","entregas","evaluaciones",
            "calificaciones","solicitudes-matricula","matriculas","justificaciones-asistencia",
            "compromisos-pago","transacciones-pago","comprobantes","criterios-riesgo","alertas-riesgo","archivos",
            "asistencias/alumnos","asistencias/docentes/qr"})
    void recursoInexistenteDevuelve404(String resource) throws Exception {
        request("GET","/api/"+resource+"/9223372036854775807",null,404);
    }

    @Test void rechazaReferenciasInexistentesEnCatalogos() throws Exception {
        long missing=Long.MAX_VALUE;
        request("POST","/api/cursos-competencias",data("cursoId",missing,"competenciaId",competence.getId(),"orden",1),404);
        request("POST","/api/cursos-competencias",data("cursoId",course.getId(),"competenciaId",missing,"orden",1),404);
        request("POST","/api/asignaciones-cursos",data("cursoId",missing,"seccionId",section.getId(),"docenteId",teacher.getId()),404);
        request("POST","/api/asignaciones-cursos",data("cursoId",course.getId(),"seccionId",missing,"docenteId",teacher.getId()),404);
        var other=postOk("/api/cursos",data("codigo","NUEVO","nombre","Curso nuevo","horasSemanales",4));
        request("POST","/api/asignaciones-cursos",data("cursoId",other.path("id").asLong(),"seccionId",section.getId(),"docenteId",missing),404);
        request("POST","/api/horarios",data("asignacionCursoId",missing,"aulaId",missing,"diaSemana","LUNES","horaInicio","09:00:00","horaFin","10:00:00"),404);
        request("POST","/api/horarios",data("asignacionCursoId",assignment.getId(),"aulaId",missing,"diaSemana","LUNES","horaInicio","09:00:00","horaFin","10:00:00"),404);
    }

    @Test void versionesYReferenciasNoValidasEnTareasEvaluacionesYAsistencia() throws Exception {
        var invalidTask=data("asignacionCursoId",Long.MAX_VALUE,"nombre","Prueba","descripcion","Descripcion",
                "fechaLimite",LocalDateTime.now().plusDays(1),"puntajeMaximo",20,"maxArchivosEntrega",1,"archivoIds",Set.of());
        request("POST","/api/tareas",invalidTask,404);
        invalidTask.put("asignacionCursoId",assignment.getId()); invalidTask.put("fechaLimite",LocalDateTime.now().minusDays(1));
        request("POST","/api/tareas",invalidTask,422);
        request("POST","/api/evaluaciones",data("idAsignacionCurso",assignment.getId(),"titulo","Examen",
                "tipoEvaluacion","EXAMEN","periodoEvaluacion","ANUAL","fechaEvaluacion",LocalDate.now(),
                "ponderacionPorcentaje",100,"idsCompetencia",Set.of(Long.MAX_VALUE)),404);
        request("POST","/api/asistencias/alumnos",data("alumnoId",Long.MAX_VALUE,"asignacionCursoId",assignment.getId(),
                "fechaRegistro",LocalDate.now(),"estado","PRESENTE"),404);
        request("POST","/api/asistencias/alumnos",data("alumnoId",student.getId(),"asignacionCursoId",assignment.getId(),
                "docenteRegistradorId",Long.MAX_VALUE,"fechaRegistro",LocalDate.now(),"estado","PRESENTE"),422);
    }

    @Test void controlaFormatoJsonYTiposSinExponerDetallesInternos() throws Exception {
        request("POST","/api/aulas",data("codigo","INVALIDO","nombre","Aula","tipoAula","NO_EXISTE","capacidad",30,"ubicacion","A"),400);
        request("GET","/api/alumnos/no-es-un-numero",null,400);
        request("POST","/api/aulas",data("codigo","INVALIDO","nombre","Aula","tipoAula","AULA_REGULAR","capacidad",1.5,"ubicacion","A"),400);
    }
}
