package com.asiscontrol.controller;

import com.asiscontrol.entity.*;
import com.asiscontrol.entity.enums.*;
import com.asiscontrol.entity.planificacion.PeriodoAcademico;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RiesgoCriteriosHttpTest extends ApiIntegrationSupport {
    @Test void usaNivelesConsolidadosDelModeloCualitativoDinamico() throws Exception {
        var p = periodo(1);
        var inicial = nota(p, NivelLogro.B);
        inicial.getEvaluacion().setModeloCualitativo(true);
        var regla = putOk("/api/reglas-calificacion/borrador", data("idCurso", course.getId(), "idGrado", grade.getId(),
                "formula", "CUALITATIVA_DINAMICA_V1", "componentes", List.of(data(
                        "tipoEvaluacion", inicial.getEvaluacion().getTipoEvaluacion().name(), "nombre", "Evaluaciones", "peso", 100))));
        request("POST", "/api/reglas-calificacion/" + regla.path("id").asLong() + "/activar",
                data("version", regla.path("version").asLong(), "motivo", "Modelo cualitativo"), 200);
        postOk("/api/criterios-riesgo", criterio("PROMEDIO_ACADEMICO", "IGUAL", 2, 1));
        var riesgo = recalcular(p).path("reglas").get(0);
        assertThat(riesgo.path("resultado").asText()).isEqualTo("EN_RIESGO");
        assertThat(riesgo.path("valor").decimalValue()).isEqualByComparingTo("2");
        assertThat(riesgo.path("evidencias").toString()).contains("niveles consolidados");
        var requerida = nota(p, NivelLogro.C);
        requerida.getEvaluacion().setModeloCualitativo(true);
        requerida.setActivo(false);
        var pendiente = recalcular(p).path("reglas").get(0);
        assertThat(pendiente.path("resultado").asText()).isEqualTo("DATOS_INSUFICIENTES");
        assertThat(pendiente.path("idAlerta").asLong()).isEqualTo(riesgo.path("idAlerta").asLong());
    }
    @org.springframework.beans.factory.annotation.Autowired
    private com.asiscontrol.service.EvidenciaRiesgoService evidencia;

    @Test void conservaConfiguracionYEvidenciaUsandoSoloAuditoriaExistente() throws Exception {
        var p = periodo(1); nota(p, NivelLogro.C);
        var creado = postOk("/api/criterios-riesgo", criterio("PROMEDIO_ACADEMICO", "MENOR_QUE", 3, 2));
        long idCriterio = creado.path("id").asLong();
        em.flush(); em.clear();
        assertThat(getOk("/api/criterios-riesgo/" + idCriterio).path("minimoDatos").asInt()).isEqualTo(2);
        assertThat(recalcular(p).path("resultado").asText()).isEqualTo("DATOS_INSUFICIENTES");
        var edicion = criterio("PROMEDIO_ACADEMICO", "MENOR_QUE", 3, 2);
        edicion.remove("minimoDatos");
        assertThat(putOk("/api/criterios-riesgo/" + idCriterio, edicion).path("minimoDatos").asInt()).isEqualTo(2);
        assertThat(em.createQuery("select count(r) from RegistroAuditoria r where r.accion = 'EVALUAR_CRITERIO_RIESGO' and r.entidad = 'Alumno'", Long.class)
                .getSingleResult()).isEqualTo(1);
        assertThat(em.getMetamodel().getEntities().stream().map(jakarta.persistence.metamodel.EntityType::getName))
                .doesNotContain("HistorialRiesgo");
    }

    @Test void evidenciaGrandeSeConservaCompletaSinDesbordarLasColumnasText() throws Exception {
        var p = periodo(1); nota(p, NivelLogro.C);
        postOk("/api/criterios-riesgo", criterio("PROMEDIO_ACADEMICO", "MENOR_QUE", 3, 1));
        long id = recalcular(p).path("reglas").get(0).path("idAlerta").asLong();
        var original = evidencia.ultima(id);
        var r = original.evaluacion();
        var extensa = List.of("evidencia con acentos y unicode: áéíóú 😀 ".repeat(3000));
        var detalle = new com.asiscontrol.dto.RiesgoDto.ResultadoRegla(r.idCriterio(), r.nombreCriterio(), r.nivel(),
                r.indicador(), r.resultado(), r.valor(), r.umbral(), r.operador(), r.cantidadDatos(), r.minimoDatos(),
                r.motivo(), extensa, id);
        evidencia.guardar(new com.asiscontrol.service.EvidenciaRiesgoService.Instantanea(1, student.getId(),
                p.getId(), p.getNombre(), section.getId(), section.getGrado(), section.getNombre(), detalle));
        em.flush(); em.clear();
        assertThat(evidencia.ultima(id).evaluacion().evidencias()).isEqualTo(extensa);
        assertThat(getOk("/api/alertas-riesgo/" + id + "/historial")).hasSize(2);
        var partes = em.createQuery("from RegistroAuditoria r where r.accion = 'RIESGO_EVIDENCIA_PARTE'", RegistroAuditoria.class).getResultList();
        assertThat(partes).isNotEmpty();
        for (var parte : partes)
            assertThat(parte.getNuevoValor().getBytes(java.nio.charset.StandardCharsets.UTF_8).length).isLessThan(65536);
    }
    private AsistenciaAlumno asistencia(PeriodoAcademico p, EstadoAsistencia estado) {
        var aula = new Aula(); aula.setCodigo(key()); aula.setNombre("Aula"); aula.setTipoAula(TipoAula.values()[0]);
        aula.setUbicacion("Primer piso"); aula.setCapacidad(30); em.persist(aula);
        var horario = new Horario(); horario.setAsignacionCurso(assignment); horario.setAula(aula);
        horario.setDiaSemana(DiaSemana.values()[0]); horario.setHoraInicio(LocalTime.of(8,0));
        horario.setHoraFin(LocalTime.of(9,0)); em.persist(horario);
        var sesion = new com.asiscontrol.entity.asistencia.SesionClase(); sesion.setAsignacionCurso(assignment);
        sesion.setHorario(horario); sesion.setFecha(p.getFechaInicio()); sesion.setHoraInicio(LocalTime.of(8,0));
        sesion.setHoraFin(LocalTime.of(9,0)); sesion.setEstado(com.asiscontrol.entity.asistencia.EstadoSesionClase.TOMADA);
        em.persist(sesion);
        var a = new AsistenciaAlumno(); a.setAlumno(student); a.setAsignacionCurso(assignment);
        a.setSesionClase(sesion); a.setFechaRegistro(p.getFechaInicio()); a.setHoraRegistro(LocalTime.of(8,0));
        a.setEstadoAsistencia(estado); em.persist(a); return a;
    }

    @Test void recalculaCorreccionDeAsistenciaYNoCuentaJustificadasNiBorradores() throws Exception {
        var p = periodo(1); var a = asistencia(p, EstadoAsistencia.INASISTENCIA);
        postOk("/api/criterios-riesgo", criterio("INASISTENCIAS", "MAYOR_IGUAL", 1, 1));
        postOk("/api/criterios-riesgo", criterio("TARDANZAS", "MAYOR_IGUAL", 1, 1));
        long id = recalcular(p).path("reglas").get(0).path("idAlerta").asLong();
        a.setEstadoAsistencia(EstadoAsistencia.INASISTENCIA_JUSTIFICADA);
        assertThat(recalcular(p).path("resultado").asText()).isEqualTo("SIN_RIESGO");
        assertThat(getOk("/api/alertas-riesgo/" + id).path("estado").asText()).isEqualTo("RESUELTA");
        a.setEstadoAsistencia(EstadoAsistencia.TARDANZA);
        assertThat(recalcular(p).path("reglas").get(1).path("resultado").asText()).isEqualTo("EN_RIESGO");
        a.getSesionClase().setEstado(com.asiscontrol.entity.asistencia.EstadoSesionClase.BORRADOR);
        assertThat(recalcular(p).path("resultado").asText()).isEqualTo("DATOS_INSUFICIENTES");
    }

    @Test void tareasSoloCuentaVencidasPublicadasYReconoceEntregaTardia() throws Exception {
        var p = periodo(1);
        p.setFechaInicio(LocalDate.now().minusDays(10)); p.setFechaFin(LocalDate.now().plusDays(10));
        var t = new Tarea(); t.setNombre("Tarea"); t.setDescripcion("Prueba"); t.setAsignacionCurso(assignment);
        t.setFechaLimite(LocalDateTime.now().minusDays(1)); t.setPuntajeMaximo(20); t.setMaxArchivosEntrega(1);
        t.setEstadoPublicacion(EstadoPublicacionTarea.BORRADOR); em.persist(t);
        postOk("/api/criterios-riesgo", criterio("TAREAS_NO_ENTREGADAS", "MAYOR_IGUAL", 1, 1));
        assertThat(recalcular(p).path("resultado").asText()).isEqualTo("DATOS_INSUFICIENTES");
        t.setEstadoPublicacion(EstadoPublicacionTarea.PUBLICADA);
        assertThat(recalcular(p).path("resultado").asText()).isEqualTo("EN_RIESGO");
        var entrega = new EntregaTarea(); entrega.setAlumno(student); entrega.setTarea(t);
        entrega.setFechaHoraEntrega(LocalDateTime.now()); entrega.setEstado(EstadoTarea.ENTREGADO_TARDE); em.persist(entrega);
        assertThat(recalcular(p).path("resultado").asText()).isEqualTo("SIN_RIESGO");
        t.setFechaLimite(LocalDateTime.now().plusDays(1));
        assertThat(recalcular(p).path("resultado").asText()).isEqualTo("DATOS_INSUFICIENTES");
    }
    private PeriodoAcademico periodo(int orden) {
        var p = new PeriodoAcademico(); p.setAnioAcademico(year); p.setOrden(orden); p.setNombre("Periodo " + orden);
        p.setFechaInicio(LocalDate.of(2090, orden, 1)); p.setFechaFin(p.getFechaInicio().plusMonths(1).minusDays(1));
        em.persist(p); return p;
    }
    private Calificacion nota(PeriodoAcademico p, NivelLogro nivel) {
        var e = new Evaluacion(); e.setAsignacionCurso(assignment); e.setTitulo("Prueba");
        e.setTipoEvaluacion(TipoEvaluacion.values()[0]); e.setPeriodoEvaluacion(PeriodoEvaluacion.values()[0]);
        e.setEstado(EstadoEvaluacion.PUBLICADA); e.setFechaEvaluacion(p.getFechaInicio());
        e.setCompetencias(java.util.Set.of(competence));
        e.setPonderacionPorcentaje(new BigDecimal("100")); em.persist(e);
        var c = new Calificacion(); c.setAlumno(student); c.setCompetencia(competence); c.setEvaluacion(e);
        c.setNivelLogro(nivel); c.setFechaRegistro(Instant.now()); em.persist(c); return c;
    }
    private void activarReglaParaRiesgo(TipoEvaluacion tipo) throws Exception {
        var regla = putOk("/api/reglas-calificacion/borrador", data("idCurso", course.getId(), "idGrado", grade.getId(),
                "formula", "PROMEDIO_PONDERADO", "componentes", List.of(
                    data("tipoEvaluacion", tipo.name(), "nombre", "Evaluaciones", "peso", 100))));
        request("POST", "/api/reglas-calificacion/" + regla.path("id").asLong() + "/activar",
                data("version", regla.path("version").asLong(), "motivo", "Acuerdo académico"), 200);
    }

    private Map<String,Object> criterio(String indicador, String operador, int umbral, int minimo) {
        return data("nombre", "Criterio " + indicador, "indicador", indicador, "operador", operador,
                "umbral", umbral, "nivelRiesgo", "ALTO", "minimoDatos", minimo);
    }
    private JsonNode recalcular(PeriodoAcademico p) throws Exception {
        em.flush();
        return request("POST", "/api/evaluaciones-riesgo", data("idAlumno", student.getId(), "idPeriodo", p.getId()), 200);
    }

    private long alertasDashboard() throws Exception {
        for (var indicador : getOk("/api/dashboard").path("indicadores"))
            if ("ALERTAS_ACTIVAS".equals(indicador.path("codigo").asText())) return indicador.path("valor").asLong();
        throw new AssertionError("Falta el indicador ALERTAS_ACTIVAS");
    }
    @Test void detectaActualizaCierraYConservaEvidenciasSinDuplicados() throws Exception {
        var p = periodo(1); var n = nota(p, NivelLogro.C);
        var c = postOk("/api/criterios-riesgo", criterio("PROMEDIO_ACADEMICO", "MENOR_QUE", 3, 1));
        var primero = recalcular(p);
        assertThat(primero.path("resultado").asText()).isEqualTo("EN_RIESGO");
        long id = primero.path("reglas").get(0).path("idAlerta").asLong();
        String path = "/api/alertas-riesgo/" + id;
        assertThat(recalcular(p).path("reglas").get(0).path("idAlerta").asLong()).isEqualTo(id);
        assertThat(getOk(path).path("idPeriodo").asLong()).isEqualTo(p.getId());
        var alertas = getOk("/api/alertas-riesgo?idAlumno=" + student.getId() + "&nivel=ALTO").path("content");
        assertThat(alertas).hasSize(1);
        assertThat(alertas.get(0).path("nombreAlumno").asText())
                .isEqualTo(student.getPersona().nombreCompleto());
        request("DELETE", "/api/criterios-riesgo/" + c.path("id").asLong(), null, 422);
        putOk(path + "/estado", data("estado", "EN_SEGUIMIENTO"));
        request("PUT", path + "/estado", data("estado", "RESUELTA"), 422);
        n.setNivelLogro(NivelLogro.B); recalcular(p);
        assertThat(getOk(path).path("valorDetectado").decimalValue()).isEqualByComparingTo("2");
        n.setNivelLogro(NivelLogro.AD);
        assertThat(recalcular(p).path("resultado").asText()).isEqualTo("SIN_RIESGO");
        assertThat(getOk(path).path("estado").asText()).isEqualTo("RESUELTA");
        var historia = getOk(path + "/historial");
        assertThat(historia).hasSize(4);
        assertThat(historia.get(0).path("evaluacion").path("valor").decimalValue()).isEqualByComparingTo("1");
        assertThat(historia.get(3).path("evaluacion").path("valor").decimalValue()).isEqualByComparingTo("4");
        assertThat(historia.get(0).path("evaluacion").path("evidencias")).hasSize(1);
        assertThat(getOk("/api/alertas-riesgo/resumen?idPeriodo=" + p.getId())).isEmpty();
        request("PUT", path + "/estado", data("estado", "ACTIVA"), 422);
        n.setNivelLogro(NivelLogro.C);
        assertThat(recalcular(p).path("reglas").get(0).path("idAlerta").asLong()).isNotEqualTo(id);
    }
    @Test void faltaDeDatosNoEsSinRiesgoNiCierraLaAlertaAnterior() throws Exception {
        var p = periodo(1);
        postOk("/api/criterios-riesgo", criterio("PROMEDIO_ACADEMICO", "MENOR_QUE", 3, 2));
        assertThat(recalcular(p).path("resultado").asText()).isEqualTo("DATOS_INSUFICIENTES");
        nota(p, NivelLogro.C);
        assertThat(recalcular(p).path("resultado").asText()).isEqualTo("DATOS_INSUFICIENTES");
        var n = nota(p, NivelLogro.C);
        long id = recalcular(p).path("reglas").get(0).path("idAlerta").asLong();
        assertThat(alertasDashboard()).isEqualTo(1);
        n.setActivo(false);
        assertThat(recalcular(p).path("resultado").asText()).isEqualTo("DATOS_INSUFICIENTES");
        var alerta = getOk("/api/alertas-riesgo/" + id);
        assertThat(alerta.path("determinable").asBoolean()).isFalse();
        assertThat(alerta.path("estado").asText()).isEqualTo("ACTIVA");
        assertThat(getOk("/api/alertas-riesgo/resumen?idPeriodo=" + p.getId())).isEmpty();
        assertThat(alertasDashboard()).isZero();
        n.setActivo(true);
        assertThat(recalcular(p).path("reglas").get(0).path("idAlerta").asLong()).isEqualTo(id);
        assertThat(alertasDashboard()).isEqualTo(1);
    }
    @Test void mantienePendientesLosIndicadoresAcademicosDuranteLaTransicionNumerica() throws Exception {
        var periodo = periodo(1);
        nota(periodo, NivelLogro.C);
        nota(periodo, NivelLogro.A).setNotaNumerica(new BigDecimal("17.5"));
        activarReglaParaRiesgo(TipoEvaluacion.values()[0]);
        activarEscalaParaRiesgo();
        postOk("/api/criterios-riesgo", criterio("PROMEDIO_ACADEMICO", "MENOR_QUE", 3, 1));
        postOk("/api/criterios-riesgo", criterio("COMPETENCIAS_EN_INICIO", "MAYOR_IGUAL", 1, 1));
        var resultado = recalcular(periodo);
        assertThat(resultado.path("resultado").asText()).isEqualTo("DATOS_INSUFICIENTES");
        assertThat(resultado.path("reglas")).allSatisfy(regla -> {
            assertThat(regla.path("resultado").asText()).isEqualTo("DATOS_INSUFICIENTES");
            assertThat(regla.path("cantidadDatos").asInt()).isZero();
        });
        assertThat(resultado.path("reglas").get(0).path("evidencias").toString()).contains("Transición");
    }

    @Test void promedioNumericoPesaCadaCompetenciaPorIgualYCuentaCalificacionesElegibles() throws Exception {
        var p = periodo(1);
        var primera = nota(p, NivelLogro.A);
        primera.setNotaNumerica(new BigDecimal("0"));
        nota(p, NivelLogro.A).setNotaNumerica(new BigDecimal("20"));
        var segundaCompetencia = new Competencia();
        segundaCompetencia.setCodigo(key()); segundaCompetencia.setNombre("Argumenta"); em.persist(segundaCompetencia);
        var otra = nota(p, NivelLogro.C);
        otra.getEvaluacion().setCompetencias(java.util.Set.of(segundaCompetencia));
        otra.setCompetencia(segundaCompetencia);
        otra.setNotaNumerica(new BigDecimal("19"));
        var borrador = new Competencia();
        borrador.setCodigo(key()); borrador.setNombre("Competencia en borrador"); em.persist(borrador);
        var pruebaEnBorrador = nota(p, NivelLogro.C);
        pruebaEnBorrador.setCompetencia(borrador);
        pruebaEnBorrador.getEvaluacion().setCompetencias(java.util.Set.of(borrador));
        pruebaEnBorrador.getEvaluacion().setEstado(EstadoEvaluacion.BORRADOR);
        activarReglaParaRiesgo(primera.getEvaluacion().getTipoEvaluacion());
        activarEscalaParaRiesgo();
        var criterio = postOk("/api/criterios-riesgo", criterio("PROMEDIO_ACADEMICO", "IGUAL", 4, 4));

        var insuficiente = recalcular(p).path("reglas").get(0);
        assertThat(insuficiente.path("resultado").asText()).isEqualTo("DATOS_INSUFICIENTES");
        assertThat(insuficiente.path("cantidadDatos").asInt()).isEqualTo(3);
        assertThat(insuficiente.path("valor").decimalValue()).isEqualByComparingTo("2.5000");
        assertThat(insuficiente.path("evidencias").toString()).contains("Argumenta: AD")
                .doesNotContain("Competencia en borrador", "nota=19", "19.0000");

        putOk("/api/criterios-riesgo/" + criterio.path("id").asLong(), criterio("PROMEDIO_ACADEMICO", "MENOR_IGUAL", 3, 3));
        var detectado = recalcular(p).path("reglas").get(0);
        assertThat(detectado.path("resultado").asText()).isEqualTo("EN_RIESGO");
        long idAlerta = detectado.path("idAlerta").asLong();
        primera.setNotaNumerica(new BigDecimal("12"));
        var corregido = recalcular(p).path("reglas").get(0);
        assertThat(corregido.path("valor").decimalValue()).isEqualByComparingTo("3.5000");
        assertThat(corregido.path("resultado").asText()).isEqualTo("SIN_RIESGO");
        assertThat(getOk("/api/alertas-riesgo/" + idAlerta).path("estado").asText()).isEqualTo("RESUELTA");
        assertThat(getOk("/api/alertas-riesgo/" + idAlerta + "/historial")).hasSize(2);
    }

    @Test void periodoCerradoUsaVersionPublicadaSinReescribirLibreta() throws Exception {
        var p = periodo(1);
        var nota = nota(p, NivelLogro.C);
        nota.setNotaNumerica(new BigDecimal("13"));
        activarReglaParaRiesgo(nota.getEvaluacion().getTipoEvaluacion());
        long escalaOriginal = activarEscalaParaRiesgo();
        var informe = new InformeProgreso();
        informe.setAlumno(student);
        informe.setAsignacionCurso(assignment);
        informe.setDocente(assignment.getDocente());
        informe.setPeriodoAcademico(p);
        informe.setEstado(EstadoInformeProgreso.PUBLICADA);
        informe.setEscalaInstitucional(em.find(EscalaInstitucional.class, escalaOriginal));
        informe.setReglaCalificacion(em.createQuery("from ReglaCalificacion r where r.curso = :curso", ReglaCalificacion.class)
                .setParameter("curso", course).getSingleResult());
        informe.setConsolidadoRegla("[]");
        em.persist(informe);
        p.setEstado(EstadoPeriodoAcademico.CERRADO);

        var otra = putOk("/api/escala-institucional/borrador", data("rangos", List.of(
                data("nivel", "C", "minimo", 0, "maximo", 9), data("nivel", "B", "minimo", 10, "maximo", 12),
                data("nivel", "A", "minimo", 13, "maximo", 17), data("nivel", "AD", "minimo", 18, "maximo", 20))));
        request("POST", "/api/escala-institucional/" + otra.path("id").asLong() + "/activar",
                data("version", otra.path("version").asLong(), "motivo", "Nueva escala"), 200);
        postOk("/api/criterios-riesgo", criterio("PROMEDIO_ACADEMICO", "IGUAL", 2, 1));
        var resultado = recalcular(p).path("reglas").get(0);
        assertThat(resultado.path("valor").decimalValue()).isEqualByComparingTo("2.0000");
        assertThat(resultado.path("resultado").asText()).isEqualTo("EN_RIESGO");
        em.refresh(informe);
        assertThat(informe.getConsolidadoRegla()).isEqualTo("[]");
        assertThat(informe.getEscalaInstitucional().getId()).isEqualTo(escalaOriginal);
    }

    private long activarEscalaParaRiesgo() throws Exception {
        var escala = putOk("/api/escala-institucional/borrador", data("rangos", List.of(
                data("nivel", "C", "minimo", 0, "maximo", 10), data("nivel", "B", "minimo", 11, "maximo", 13),
                data("nivel", "A", "minimo", 14, "maximo", 17), data("nivel", "AD", "minimo", 18, "maximo", 20))));
        request("POST", "/api/escala-institucional/" + escala.path("id").asLong() + "/activar",
                data("version", escala.path("version").asLong(), "motivo", "Escala académica"), 200);
        return escala.path("id").asLong();
    }

    @Test void separaPeriodosYConservaUmbralAunqueSeEditeElCriterio() throws Exception {
        var p = periodo(1); var otro = periodo(2); nota(p, NivelLogro.C); nota(otro, NivelLogro.C);
        var c = postOk("/api/criterios-riesgo", criterio("PROMEDIO_ACADEMICO", "MENOR_QUE", 3, 1));
        long id = recalcular(p).path("reglas").get(0).path("idAlerta").asLong();
        assertThat(recalcular(otro).path("reglas").get(0).path("idAlerta").asLong()).isNotEqualTo(id);
        putOk("/api/criterios-riesgo/" + c.path("id").asLong(), criterio("PROMEDIO_ACADEMICO", "MENOR_QUE", 2, 1));
        assertThat(getOk("/api/alertas-riesgo/" + id).path("umbral").decimalValue()).isEqualByComparingTo("3");
        var resumen = getOk("/api/alertas-riesgo/resumen?idPeriodo=" + p.getId());
        assertThat(resumen).hasSize(1);
        assertThat(resumen.get(0).path("cantidadAlertas").asInt()).isEqualTo(1);
        assertThat(resumen.get(0).path("grado").asInt()).isEqualTo(section.getGrado());
    }
    @Test void noGeneraAlertasSinRiesgoYDetectaVariasCausas() throws Exception {
        var p = periodo(1); var n = nota(p, NivelLogro.AD);
        activarReglaParaRiesgo(n.getEvaluacion().getTipoEvaluacion());
        postOk("/api/criterios-riesgo", criterio("PROMEDIO_ACADEMICO", "MENOR_QUE", 3, 1));
        postOk("/api/criterios-riesgo", criterio("COMPETENCIAS_EN_INICIO", "MAYOR_IGUAL", 1, 1));
        assertThat(recalcular(p).path("resultado").asText()).isEqualTo("SIN_RIESGO");
        assertThat(em.createQuery("select count(a) from AlertaRiesgo a", Long.class).getSingleResult()).isZero();
        n.setNivelLogro(NivelLogro.C);
        var reglas = recalcular(p).path("reglas");
        assertThat(reglas).hasSize(2);
        for (var regla : reglas) assertThat(regla.path("resultado").asText()).isEqualTo("EN_RIESGO");
    }
    @Test void determinaElUmbralConResultadosVigentesSinContarLasCompetenciasPendientes() throws Exception {
        var p = periodo(1);
        var nota = nota(p, NivelLogro.C);
        activarReglaParaRiesgo(nota.getEvaluacion().getTipoEvaluacion());
        var escala = putOk("/api/escala-institucional/borrador", data("rangos", List.of(
                data("nivel", "C", "minimo", 0, "maximo", 10), data("nivel", "B", "minimo", 11, "maximo", 13),
                data("nivel", "A", "minimo", 14, "maximo", 17), data("nivel", "AD", "minimo", 18, "maximo", 20))));
        request("POST", "/api/escala-institucional/" + escala.path("id").asLong() + "/activar",
                data("version", escala.path("version").asLong(), "motivo", "Escala académica"), 200);
        nota.setNotaNumerica(new BigDecimal("8"));
        var adicional = new Competencia();
        adicional.setCodigo("RIESGO" + key()); adicional.setNombre("Argumenta"); em.persist(adicional);
        nota.getEvaluacion().setCompetencias(java.util.Set.of(competence, adicional));
        postOk("/api/criterios-riesgo", criterio("COMPETENCIAS_EN_INICIO", "MAYOR_IGUAL", 1, 1));
        var detectado = recalcular(p).path("reglas").get(0);
        assertThat(detectado.path("resultado").asText()).isEqualTo("EN_RIESGO");
        assertThat(detectado.path("valor").asInt()).isEqualTo(1);
        nota.setNotaNumerica(new BigDecimal("16"));
        nota.setNivelLogro(NivelLogro.A);
        assertThat(recalcular(p).path("resultado").asText()).isEqualTo("DATOS_INSUFICIENTES");
        long alerta = detectado.path("idAlerta").asLong();
        assertThat(getOk("/api/alertas-riesgo/" + alerta).path("estado").asText()).isEqualTo("ACTIVA");
        var segunda = new Calificacion();
        segunda.setAlumno(student); segunda.setCompetencia(adicional); segunda.setEvaluacion(nota.getEvaluacion());
        segunda.setNotaNumerica(new BigDecimal("15")); segunda.setNivelLogro(NivelLogro.A);
        segunda.setFechaRegistro(Instant.now()); em.persist(segunda);
        assertThat(recalcular(p).path("resultado").asText()).isEqualTo("SIN_RIESGO");
        assertThat(getOk("/api/alertas-riesgo/" + alerta).path("estado").asText()).isEqualTo("RESUELTA");
        assertThat(getOk("/api/alertas-riesgo/" + alerta + "/historial")).hasSize(3);
    }

    @Test void confirmaDosCompetenciasCConUnaTerceraPendiente() throws Exception {
        var p = periodo(1);
        var primera = nota(p, NivelLogro.C);
        activarReglaParaRiesgo(primera.getEvaluacion().getTipoEvaluacion());
        var escala = putOk("/api/escala-institucional/borrador", data("rangos", List.of(
                data("nivel", "C", "minimo", 0, "maximo", 10), data("nivel", "B", "minimo", 11, "maximo", 13),
                data("nivel", "A", "minimo", 14, "maximo", 17), data("nivel", "AD", "minimo", 18, "maximo", 20))));
        request("POST", "/api/escala-institucional/" + escala.path("id").asLong() + "/activar",
                data("version", escala.path("version").asLong(), "motivo", "Escala académica"), 200);
        primera.setNotaNumerica(new BigDecimal("8"));
        var segundaCompetencia = new Competencia();
        segundaCompetencia.setCodigo(key()); segundaCompetencia.setNombre("Comprende"); em.persist(segundaCompetencia);
        var pendiente = new Competencia();
        pendiente.setCodigo(key()); pendiente.setNombre("Argumenta"); em.persist(pendiente);
        var prueba = primera.getEvaluacion();
        prueba.setCompetencias(java.util.Set.of(competence, segundaCompetencia, pendiente));
        var segunda = new Calificacion();
        segunda.setAlumno(student); segunda.setCompetencia(segundaCompetencia); segunda.setEvaluacion(prueba);
        segunda.setNotaNumerica(new BigDecimal("7")); segunda.setNivelLogro(NivelLogro.C);
        segunda.setFechaRegistro(Instant.now()); em.persist(segunda);
        postOk("/api/criterios-riesgo", criterio("COMPETENCIAS_EN_INICIO", "MAYOR_IGUAL", 2, 2));
        postOk("/api/criterios-riesgo", criterio("PROMEDIO_ACADEMICO", "MENOR_QUE", 3, 1));
        var resultado = recalcular(p);
        assertThat(resultado.path("resultado").asText()).isEqualTo("EN_RIESGO");
        assertThat(resultado.path("reglas").get(0).path("valor").asInt()).isEqualTo(2);
        var promedio = resultado.path("reglas").get(1);
        assertThat(promedio.path("resultado").asText()).isEqualTo("DATOS_INSUFICIENTES");
        assertThat(promedio.path("cantidadDatos").asInt()).isEqualTo(2);
        assertThat(promedio.path("evidencias").toString()).contains("Argumenta: pendiente");
    }

    @Test void rechazaSolicitudesIncompletasYMinimosInvalidos() throws Exception {
        request("POST", "/api/evaluaciones-riesgo", data("idAlumno", student.getId()), 400);
        request("POST", "/api/criterios-riesgo", criterio("INASISTENCIAS", "MAYOR_QUE", 0, 0), 400);
        request("POST", "/api/criterios-riesgo", criterio("PROMEDIO_ACADEMICO", "MENOR_QUE", 20, 1), 422);
        request("POST", "/api/evaluaciones-riesgo", data("idAlumno", student.getId(), "idPeriodo", 999999), 404);
    }

    @Test void usaPonderacionesYDescartaNotasEnBorradorYFueraDelPeriodo() throws Exception {
        var p = periodo(1);
        var baja = nota(p, NivelLogro.C); baja.getEvaluacion().setPonderacionPorcentaje(new BigDecimal("25"));
        var alta = nota(p, NivelLogro.AD); alta.getEvaluacion().setPonderacionPorcentaje(new BigDecimal("75"));
        var borrador = nota(p, NivelLogro.C); borrador.getEvaluacion().setEstado(EstadoEvaluacion.BORRADOR);
        nota(periodo(2), NivelLogro.C);
        postOk("/api/criterios-riesgo", criterio("PROMEDIO_ACADEMICO", "MENOR_QUE", 3, 2));
        var regla = recalcular(p).path("reglas").get(0);
        assertThat(regla.path("valor").decimalValue()).isEqualByComparingTo("3.25");
        assertThat(regla.path("cantidadDatos").asInt()).isEqualTo(2);
        assertThat(regla.path("resultado").asText()).isEqualTo("SIN_RIESGO");
    }
    @Test void docenteSoloConsultaContextosAsignadosIncluidoHistorialYResumen() throws Exception {
        var p = periodo(1); nota(p, NivelLogro.C);
        postOk("/api/criterios-riesgo", criterio("PROMEDIO_ACADEMICO", "MENOR_QUE", 3, 1));
        long id = recalcular(p).path("reglas").get(0).path("idAlerta").asLong();
        var propio = jwt().jwt(j -> j.subject("docente").claim("personaId", teacher.getPersona().getId()))
                .authorities(new SimpleGrantedAuthority("ROLE_DOCENTE"), new SimpleGrantedAuthority("RIESGOS_LEER"));
        var ajeno = jwt().jwt(j -> j.subject("otro").claim("personaId", -1L))
                .authorities(new SimpleGrantedAuthority("ROLE_DOCENTE"), new SimpleGrantedAuthority("RIESGOS_LEER"));
        for (String sufijo : List.of("", "/historial")) {
            mvc.perform(get("/api/alertas-riesgo/" + id + sufijo).with(propio)).andExpect(status().isOk());
            mvc.perform(get("/api/alertas-riesgo/" + id + sufijo).with(ajeno)).andExpect(status().isForbidden());
        }
        var lista = mvc.perform(get("/api/alertas-riesgo").with(ajeno)).andExpect(status().isOk()).andReturn();
        assertThat(json.readTree(lista.getResponse().getContentAsString()).path("totalElements").asInt()).isZero();
        var resumen = mvc.perform(get("/api/alertas-riesgo/resumen?idPeriodo=" + p.getId()).with(ajeno))
                .andExpect(status().isOk()).andReturn();
        assertThat(json.readTree(resumen.getResponse().getContentAsString())).isEmpty();
    }
}
