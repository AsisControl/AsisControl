package com.asiscontrol.controller;

import com.asiscontrol.dto.CargaMasivaDto;
import com.asiscontrol.dto.DemostracionDtos.*;
import com.asiscontrol.dto.persona.PersonaDtos;
import com.asiscontrol.dto.asistencia.ReporteAsistenciaDtos;
import com.asiscontrol.entity.*;
import com.asiscontrol.exception.ConflictException;
import com.asiscontrol.service.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:demostracion_it;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
        "app.demostracion.enabled=true", "app.demostracion.sql-manifest=../../demo/sql/manifest.properties", "app.asistencia.fecha-simulada=2026-11-27",
        "app.bootstrap.enabled=true", "app.bootstrap.admin-document=99000001",
        "app.bootstrap.admin-email=demo.admin@asiscontrol.test", "app.bootstrap.admin-password=PruebaDemo12345",
        "app.tareas.cierre-automatico.enabled=false"})
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class DemostracionIntegrationTest {
    @Autowired DemostracionService demo;
    @Autowired EntityManager em;
    @Autowired TransactionTemplate tx;
    @Autowired ApplicationContext contexto;
    @Autowired WebApplicationContext web;

    @BeforeEach void autenticar() {
        var auth = new JwtAuthenticationToken(Jwt.withTokenValue("prueba")
                .header("alg", "none").subject("administrador.general")
                .claim("rol", "ADMINISTRADOR").claim("requiereCambioContrasena", false)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(3600)).build(),
                List.of(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
    @AfterEach void limpiarAutenticacion() { SecurityContextHolder.clearContext(); }

    @Test void recorreLosExcelRealesYConservaLoHechoAlRepetir() throws Exception {
        var inicial = preparar(Etapa.INICIAL);
        assertThat(inicial.yaEjecutada()).isFalse();
        assertThat(preparar(Etapa.INICIAL).recursos()).isEqualTo(inicial.recursos());
        importarPersonas();
        importar("ImportacionCompetenciaService", "05_competencias.xlsx", 4);
        importar("ImportacionPlanEstudiosService", "06_plan_estudios.xlsx", 4);
        importar("ImportacionSeccionService", "07_secciones.xlsx", 2);
        importar("ImportacionAulaService", "08_aulas.xlsx", 2);
        importar("ImportacionTarifaService", "09_tarifas.xlsx", 2);
        preparar(Etapa.HABILITAR_MATRICULAS);
        importar("ImportacionMatriculaService", "10_matriculas.xlsx", 10);
        importar("ImportacionAsignacionService", "11_asignaciones_docentes.xlsx", 4);
        importar("ImportacionHorarioService", "12_horarios.xlsx", 20);
        // Un conflicto tardío debe deshacer también evaluaciones y notas ya preparadas en esa etapa.
        Long sesionAjena = tx.execute(s -> {
            var horario = em.createQuery("select h from Horario h where h.asignacionCurso.curso.codigo = 'DEMO-MAT-2' and h.diaSemana = :dia", Horario.class)
                    .setParameter("dia", com.asiscontrol.entity.enums.DiaSemana.VIERNES).getSingleResult();
            var sesion = new com.asiscontrol.entity.asistencia.SesionClase();
            sesion.setAsignacionCurso(horario.getAsignacionCurso()); sesion.setHorario(horario);
            sesion.setFecha(java.time.LocalDate.of(2026, 11, 13));
            sesion.setHoraInicio(horario.getHoraInicio()); sesion.setHoraFin(horario.getHoraFin());
            em.persist(sesion);
            return sesion.getId();
        });
        assertThatThrownBy(() -> preparar(Etapa.ESCENARIOS)).isInstanceOf(ConflictException.class)
                .hasMessageContaining("sesión demo ya existe");
        assertThat(contar("Evaluacion")).isZero();
        assertThat(contar("Calificacion")).isZero();
        assertThat(contar("CargaDemostracion")).isEqualTo(2);
        tx.executeWithoutResult(s -> em.remove(em.find(com.asiscontrol.entity.asistencia.SesionClase.class, sesionAjena)));
        var resultado = preparar(Etapa.ESCENARIOS);
        var ids = resultado.recursos();
        assertThat(contar("AsistenciaAlumno")).isEqualTo(40);
        assertThat(contar("AsistenciaDocente")).isEqualTo(2);
        assertThat(contar("JornadaMarcacionDocente")).isEqualTo(3);
        assertThat(contar("CompromisoPago")).isEqualTo(100);
        assertThat(contar("EntregaTarea")).isEqualTo(2);
        assertThat(contar("Matricula")).isEqualTo(10);
        assertThat(contar("InscripcionCurso")).isEqualTo(20);
        assertThat(contar("Evaluacion")).isEqualTo(8);
        assertThat(contar("TransaccionPago")).isEqualTo(1);
        assertThat(contar("Comprobante")).isEqualTo(1);
        assertThat(contar("AlertaRiesgo")).isEqualTo(1);
        Long idAlerta = tx.execute(s -> em.createQuery("select a.id from AlertaRiesgo a", Long.class).getSingleResult());
        var evidencia = contexto.getBean(EvidenciaRiesgoService.class).ultimaDeteccion(idAlerta);
        assertThat(evidencia.idAlumno()).isEqualTo(ids.get("alumnoNicolas"));
        assertThat(evidencia.evaluacion().evidencias()).hasSize(4);
        assertThat(evidencia.evaluacion().valor()).isEqualByComparingTo("4");
        var estados = tx.execute(s -> em.createQuery("select a.estadoAsistencia from AsistenciaAlumno a where a.alumno.id = :id order by a.fechaRegistro", com.asiscontrol.entity.enums.EstadoAsistencia.class)
                .setParameter("id", ids.get("alumnoMia")).getResultList());
        assertThat(estados).extracting(Enum::name).containsExactly("PRESENTE", "PRESENTE", "TARDANZA", "INASISTENCIA");
        Long personaMia = tx.execute(s -> em.find(Alumno.class, ids.get("alumnoMia")).getPersona().getId());
        var reportes = contexto.getBean(ReporteAsistenciaService.class);
        var reporte = reportes.consultar(ReporteAsistenciaDtos.Tipo.ESTUDIANTES, ids.get("periodo2026"),
                ids.get("seccion2"), personaMia, null, null, null, null, 0, 20);
        assertThat(reporte.total()).isEqualTo(4);
        assertThat(reporte.indicadores()).extracting(ReporteAsistenciaDtos.Indicador::cantidad).containsExactly(2L, 1L, 1L, 0L);
        var vacio = reportes.consultar(ReporteAsistenciaDtos.Tipo.ESTUDIANTES, ids.get("periodo2026"),
                ids.get("seccion2"), personaMia, null, resultado.fechaReferencia().minusDays(21),
                resultado.fechaReferencia().minusDays(20), null, 0, 20);
        assertThat(vacio.total()).isZero();
        assertThat(vacio.registros()).isEmpty();
        var importador = contexto.getBean(ImportacionNotasService.class);
        assertResultado(importador.importar(ids.get("evaluacionMat2"), archivo("13_notas_matematica_2A.xlsx"), true), 9);
        assertResultado(importador.importar(ids.get("evaluacionMat3"), archivo("14_notas_matematica_3A.xlsx"), true), 10);
        // Simula una modificación durante la exposición: repetir no la borra ni vuelve a crear filas.
        tx.executeWithoutResult(s -> em.find(Tarea.class, ids.get("tareaMia")).setNombre("Editada durante la exposición"));
        assertThat(preparar(Etapa.ESCENARIOS).yaEjecutada()).isTrue();
        assertThat(contar("AsistenciaAlumno")).isEqualTo(40);
        assertThat(contar("CompromisoPago")).isEqualTo(100);
        String nombre = tx.execute(s -> em.find(Tarea.class, ids.get("tareaMia")).getNombre());
        assertThat(nombre).isEqualTo("Editada durante la exposición");
        assertThat(demo.estado().etapas()).hasSize(3);
    }

    @Test void exigeDependenciasYRevierteLaEtapaFallida() {
        assertThatThrownBy(() -> demo.preparar(Etapa.INICIAL, new MockMultipartFile("archivo",
                "00_configuracion_inicial.sql", "application/sql", "DELETE FROM Persona".getBytes(java.nio.charset.StandardCharsets.UTF_8))))
                .isInstanceOf(ConflictException.class).hasMessageContaining("no coincide");
        assertThatThrownBy(() -> demo.preparar(Etapa.INICIAL, new MockMultipartFile("archivo",
                "otro.sql", "application/sql", sql(Etapa.INICIAL).getBytes())))
                .isInstanceOf(ConflictException.class).hasMessageContaining("no coincide");
        assertThat(contar("AnioAcademico")).isZero();
        assertThatThrownBy(() -> preparar(Etapa.ESCENARIOS)).isInstanceOf(ConflictException.class);
        assertThat(contar("CargaDemostracion")).isZero();
        Long anioAjeno = tx.execute(s -> {
            var anio = new com.asiscontrol.entity.planificacion.AnioAcademico();
            anio.setAnio(2030); anio.setFechaInicio(java.time.LocalDate.of(2030, 1, 1));
            anio.setFechaFin(java.time.LocalDate.of(2030, 12, 31)); em.persist(anio); return anio.getId();
        });
        assertThatThrownBy(() -> preparar(Etapa.INICIAL)).isInstanceOf(ConflictException.class)
                .hasMessageContaining("instalación limpia");
        assertThat(contar("AnioAcademico")).isEqualTo(1);
        assertThat(contar("CargaDemostracion")).isZero();
        tx.executeWithoutResult(s -> em.remove(em.find(com.asiscontrol.entity.planificacion.AnioAcademico.class, anioAjeno)));
        preparar(Etapa.INICIAL);
        assertThatThrownBy(() -> preparar(Etapa.HABILITAR_MATRICULAS)).isInstanceOf(ConflictException.class)
                .hasMessageContaining("Excel 01");
        assertThat(contar("CargaDemostracion")).isEqualTo(1);
        assertThat(contar("Evaluacion")).isZero();
    }

    @Test void restringeLaOperacionYQuedaDesactivadaPorDefecto() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(web).apply(springSecurity()).build();
        for (String rol : List.of("DOCENTE", "ALUMNO", "APODERADO"))
            mvc.perform(multipart("/api/administracion/demostracion").file(sql(Etapa.INICIAL)).param("etapa", "INICIAL")
                    .with(jwt().jwt(j -> j.claim("rol", rol).claim("requiereCambioContrasena", false))
                            .authorities(new SimpleGrantedAuthority("ROLE_" + rol)))
                    )
                    .andExpect(status().isForbidden());
        assertThat(contar("CargaDemostracion")).isZero();
        mvc.perform(multipart("/api/administracion/demostracion").file(sql(Etapa.INICIAL)).param("etapa", "INICIAL")
                .with(jwt().jwt(j -> j.subject("administrador.general").claim("rol", "ADMINISTRADOR")
                                .claim("requiereCambioContrasena", false))
                        .authorities(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR")))
                )
                .andExpect(status().isOk()).andExpect(jsonPath("$.etapa").value("INICIAL"))
                .andExpect(jsonPath("$.yaEjecutada").value(false));
        new ApplicationContextRunner().withUserConfiguration(DemostracionController.class)
                .run(c -> assertThat(c).doesNotHaveBean(DemostracionController.class));
        new ApplicationContextRunner().withUserConfiguration(DemostracionController.class)
                .withPropertyValues("app.demostracion.enabled=false")
                .run(c -> assertThat(c).doesNotHaveBean(DemostracionController.class));
    }

    private Resultado preparar(Etapa etapa) { return demo.preparar(etapa, sql(etapa)); }

    private MockMultipartFile sql(Etapa etapa) {
        String nombre = switch (etapa) {
            case INICIAL -> "00_configuracion_inicial.sql";
            case HABILITAR_MATRICULAS -> "09_habilitar_matriculas.sql";
            case ESCENARIOS -> "12_escenarios.sql";
        };
        try {
            return new MockMultipartFile("archivo", nombre, "application/sql",
                    java.nio.file.Files.readAllBytes(java.nio.file.Path.of("../../demo/sql", nombre)));
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }
    private void importarPersonas() throws Exception {
        assertPersonas(contexto.getBean(ImportacionAlumnoService.class).importStudents(archivo("01_estudiantes.xlsx")), 12);
        assertPersonas(contexto.getBean(ImportacionFamiliaService.class).importGuardians(archivo("02_apoderados.xlsx")), 11);
        assertPersonas(contexto.getBean(ImportacionFamiliaService.class).importRelationships(archivo("03_parentescos.xlsx")), 12);
        assertPersonas(contexto.getBean(ImportacionDocenteService.class).importar(archivo("04_docentes.xlsx"), true), 2);
    }
    private void importar(String clase, String nombre, int esperadas) throws Exception {
        var tipo = Class.forName("com.asiscontrol.service." + clase);
        var resultado = tipo.getMethod("importar", org.springframework.web.multipart.MultipartFile.class, boolean.class)
                .invoke(contexto.getBean(tipo), archivo(nombre), true);
        if (resultado instanceof CargaMasivaDto.Resultado comun) assertResultado(comun, esperadas);
        else {
            assertThat(resultado).isInstanceOf(ImportacionCompetenciaService.Resultado.class);
            var competencias = (ImportacionCompetenciaService.Resultado) resultado;
            assertThat(competencias.errores()).isEmpty();
            assertThat(competencias.creadas()).isEqualTo(esperadas);
        }
    }
    private void assertResultado(CargaMasivaDto.Resultado resultado, int cantidad) {
        assertThat(resultado.errores()).isEmpty();
        assertThat(resultado.creadas()).isEqualTo(cantidad);
    }
    private void assertPersonas(PersonaDtos.ImportResult resultado, int cantidad) {
        assertThat(resultado.issues()).isEmpty();
        assertThat(resultado.importedRows()).isEqualTo(cantidad);
    }
    private long contar(String entidad) {
        return tx.execute(s -> em.createQuery("select count(e) from " + entidad + " e", Long.class).getSingleResult());
    }
    private MockMultipartFile archivo(String nombre) throws Exception {
        try (var contenido = getClass().getResourceAsStream("/demostracion/" + nombre)) {
            assertThat(contenido).as(nombre).isNotNull();
            return new MockMultipartFile("archivo", nombre,
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", contenido.readAllBytes());
        }
    }
}
