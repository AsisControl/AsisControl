package com.asiscontrol.controller;

import com.asiscontrol.entity.CursoCompetencia;
import com.asiscontrol.entity.Tarea;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class AcademicoFlujosHttpTest extends ApiIntegrationSupport {
    @Test void mantieneCatalogosConCodigosUnicosYControlDeVersiones() throws Exception {
        var c = postOk("/api/cursos", data("codigo"," NUEVO ","nombre","Curso", "descripcion"," ","horasSemanales",4));
        String cp = "/api/cursos/" + c.path("id").asLong();
        assertThat(c.path("codigo").asText()).isEqualTo("NUEVO");
        request("POST","/api/cursos",data("codigo","NUEVO","nombre","Duplicado","horasSemanales",4),409);
        getOk("/api/cursos?busqueda=Curso"); getOk(cp);
        putOk(cp,data("codigo","NUEVO","nombre","Actualizado","descripcion","Detalle","horasSemanales",6,"version",0));
        request("PUT",cp,data("codigo","NUEVO","nombre","Antiguo","horasSemanales",4,"version",99),409);
        var co = postOk("/api/competencias",data("codigo","C-NUEVA","nombre","Competencia","descripcion","Detalle"));
        String cop = "/api/competencias/" + co.path("id").asLong();
        getOk("/api/competencias?busqueda=Competencia"); getOk(cop);
        putOk(cop,data("codigo","C-NUEVA","nombre","Actualizada","descripcion"," ","version",0));
        var rel = postOk("/api/cursos-competencias",data("cursoId",c.path("id").asLong(),"competenciaId",co.path("id").asLong(),"orden",1));
        String rp = "/api/cursos-competencias/" + rel.path("id").asLong();
        getOk("/api/cursos-competencias"); getOk(rp);
        putOk(rp,data("cursoId",c.path("id").asLong(),"competenciaId",co.path("id").asLong(),"orden",2,"version",0));
        deleteOk(rp); deleteOk(cop); deleteOk(cp);
        request("GET",cp,null,404);
    }

    @Test void asignaDocenteYAulaValidaCapacidadYEvitaCrucesDeHorario() throws Exception {
        var aula = postOk("/api/aulas",data("codigo","A-101","nombre","Aula 101","tipoAula","AULA_REGULAR","capacidad",35,"ubicacion","Pabellon A"));
        String ap = "/api/aulas/"+aula.path("id").asLong();
        getOk("/api/aulas"); getOk(ap);
        putOk(ap,data("codigo","A-101","nombre","Aula ampliada","tipoAula","AULA_REGULAR","capacidad",40,"ubicacion","Pabellon A","version",0));
        var newCourse = postOk("/api/cursos",data("codigo","ING-1","nombre","Ingles","horasSemanales",4));
        var as = postOk("/api/asignaciones-cursos",data("cursoId",newCourse.path("id").asLong(),"seccionId",section.getId(),"docenteId",teacher.getId()));
        String asp="/api/asignaciones-cursos/"+as.path("id").asLong();
        getOk("/api/asignaciones-cursos"); getOk(asp);
        putOk(asp,data("cursoId",newCourse.path("id").asLong(),"seccionId",section.getId(),"docenteId",teacher.getId(),"version",0));
        var h = postOk("/api/horarios",data("asignacionCursoId",as.path("id").asLong(),"aulaId",aula.path("id").asLong(),"diaSemana","LUNES","horaInicio","08:00:00","horaFin","09:00:00"));
        String hp="/api/horarios/"+h.path("id").asLong();
        getOk("/api/horarios"); getOk(hp);
        request("POST","/api/horarios",data("asignacionCursoId",as.path("id").asLong(),"aulaId",aula.path("id").asLong(),"diaSemana","LUNES","horaInicio","08:30:00","horaFin","09:30:00"),409);
        putOk(hp,data("asignacionCursoId",as.path("id").asLong(),"aulaId",aula.path("id").asLong(),"diaSemana","MARTES","horaInicio","08:00:00","horaFin","09:00:00","version",0));
        deleteOk(hp); deleteOk(asp);
        long versionAula = getOk(ap).path("version").asLong();
        request("PATCH", ap + "/inhabilitar", data("motivo", "Fin de prueba", "version", versionAula), 200);
        //deleteOk(hp); deleteOk(asp); deleteOk(ap);
    }

    @Test void completaCicloDeTareaEntregaCalificacionCierreYArchivado() throws Exception {
        var task = postOk("/api/tareas",data("asignacionCursoId",assignment.getId(),"nombre","Practica 1","descripcion","Resolver ejercicios","fechaLimite",LocalDateTime.now().plusDays(1),"puntajeMaximo",20,"maxArchivosEntrega",2,"archivoIds",Set.of()));
        long tid=task.path("id").asLong(); String tp="/api/tareas/"+tid;
        getOk("/api/tareas"); getOk(tp);
        putOk(tp,data("nombre","Practica actualizada","descripcion","Resolver ejercicios","fechaLimite",LocalDateTime.now().plusDays(1),"puntajeMaximo",20,"maxArchivosEntrega",2,"archivoIds",Set.of(),"version",0));
        request("POST",tp+"/publicar",null,200);
        request("POST",tp+"/cerrar",null,422);
        var delivery=postOk(tp+"/entregas",data("alumnoId",student.getId(),"archivoIds",Set.of(),"comentario","Entrega inicial"));
        String dp="/api/entregas/"+delivery.path("id").asLong();
        getOk(tp+"/entregas"); getOk(dp);
        putOk(dp,data("archivoIds",Set.of(),"comentario","Entrega final","version",0));
        long v=getOk(dp).path("version").asLong();
        var graded=request("POST",dp+"/calificar",data("nota",18,"comentario","Buen trabajo","version",v),200);
        assertThat(graded.path("estado").asText()).isEqualTo("CALIFICADO");
        var entity=em.find(Tarea.class,tid); entity.setFechaLimite(LocalDateTime.now().minusSeconds(1)); em.flush();
        request("POST",tp+"/cerrar",null,200); request("POST",tp+"/archivar",null,200); deleteOk(tp);
    }

    @Test void registraNivelesAdABCPorCompetenciaYConsolida() throws Exception {
        var evaluation=postOk("/api/evaluaciones",evaluacion("Diagnostico"));
        String ep="/api/evaluaciones/"+evaluation.path("id").asLong();
        getOk("/api/evaluaciones"); getOk(ep);
        putOk(ep,evaluacion("Evaluacion final"));
        putOk(ep+"/estado",data("estado","PUBLICADA"));
        var grade=postOk("/api/calificaciones",nota(evaluation.path("id").asLong(),"A"));
        long id=grade.path("id").asLong();
        assertThat(grade.path("nivelLogro").asText()).isEqualTo("A");
        getOk("/api/calificaciones"); getOk("/api/calificaciones/"+id);
        var batch=putOk("/api/calificaciones/lote",data("calificaciones",List.of(nota(evaluation.path("id").asLong(),"AD"))));
        assertThat(batch.get(0).path("nivelLogro").asText()).isEqualTo("AD");
        var consolidated=getOk("/api/calificaciones/consolidados?idAlumno="+student.getId());
        assertThat(consolidated).hasSize(1);
        assertThat(consolidated.get(0).path("nivelConsolidado").asText()).isEqualTo("AD");
        deleteOk("/api/calificaciones/"+id);
        putOk(ep+"/estado",data("estado","CERRADA"));
        request("POST","/api/calificaciones",nota(evaluation.path("id").asLong(),"C"),422);
        var draft=postOk("/api/evaluaciones",evaluacion("Borrador"));
        String dp="/api/evaluaciones/"+draft.path("id").asLong();
        putOk(dp+"/estado",data("estado","ANULADA")); deleteOk(dp);
    }

    @Test void rechazaEvaluacionCuandoLaAsociacionCursoCompetenciaEstaInactiva() throws Exception {
        var relation = em.createQuery(
                "from CursoCompetencia cc where cc.curso = :curso and cc.competencia = :competencia",
                CursoCompetencia.class)
                .setParameter("curso", course)
                .setParameter("competencia", competence)
                .getSingleResult();
        relation.setActivo(false);
        em.flush();

        var error = request("POST", "/api/evaluaciones", evaluacion("Sin asociacion activa"), 422);

        assertThat(error.path("code").asText()).isEqualTo("COMPETENCIA_NO_DISPONIBLE_PARA_CURSO");
    }

    private Map<String,Object> evaluacion(String title) {
        return data("idAsignacionCurso",assignment.getId(),"titulo",title,"descripcion","Prueba por competencias",
                "tipoEvaluacion","EXAMEN","periodoEvaluacion","BIMESTRAL","fechaEvaluacion",LocalDate.now(),
                "ponderacionPorcentaje",100,"idsCompetencia",Set.of(competence.getId()));
    }
    private Map<String,Object> nota(long evaluation,String level) {
        return data("idEvaluacion",evaluation,"idAlumno",student.getId(),"idCompetencia",competence.getId(),"nivelLogro",level,"observacion","Retroalimentacion");
    }
}
