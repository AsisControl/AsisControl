package com.asiscontrol.service;

import static org.assertj.core.api.Assertions.*;

import com.asiscontrol.dto.academico.AcademicoDtos;
import com.asiscontrol.dto.academico.ConfiguracionDtos.*;
import com.asiscontrol.dto.matricula.MatriculaDtos.*;
import com.asiscontrol.dto.persona.PersonaDtos;
import com.asiscontrol.entity.enums.*;
import com.asiscontrol.exception.BusinessRuleException;
import com.asiscontrol.repository.planificacion.AnioAcademicoRepository;
import com.asiscontrol.repository.RegistroAuditoriaRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class MatriculaConcurrencyIntegrationTest {
    @Autowired jakarta.persistence.EntityManager em;
    @Autowired ConfiguracionAcademicaService configuracion;
    @Autowired SeccionService secciones;
    @Autowired PersonaService personas;
    @Autowired SolicitudMatriculaService solicitudes;
    @Autowired MatriculaService matriculas;
    @Autowired RegistroAuditoriaRepository auditoria;
    @Autowired AnioAcademicoRepository anios;
    @Autowired PlatformTransactionManager transactions;
    @Autowired PlanPensionService planes;
    @Autowired ConfiguracionPagosService pagos;

    @Test
    void unaTarifaFaltanteRevierteLaMatriculaYSuSolicitudSinDejarPensiones() {
        long seccion = prepararOfertaFinanciera(2083, false);
        var solicitud = solicitud(seccion);
        assertThatThrownBy(() -> matriculas.crear(new CrearMatriculaRequest(solicitud.id(), solicitud.version())))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        error -> assertThat(error.getCode()).isEqualTo("PLAN_PENSION_NO_CONFIGURADO"));
        verificarFinalizacionRevertida(solicitud);
    }

    @Test
    void unPlanCambiadoRevierteLaFinalizacionYExigeRevisarLaNuevaTarifa() {
        long seccion = prepararOfertaFinanciera(2084, true);
        var solicitud = solicitud(seccion);
        String firmaAnterior = planes.previewSolicitud(solicitud.id()).firmaPlan();
        var tarifa = pagos.tarifas(2084).getFirst();
        pagos.guardarTarifa(tarifa.id(), new com.asiscontrol.dto.ConfiguracionPagosDto.TarifaRequest(
                tarifa.idConcepto(), 2084, 1, new java.math.BigDecimal("450.00"), tarifa.version()));
        assertThatThrownBy(() -> matriculas.crear(new CrearMatriculaRequest(solicitud.id(), solicitud.version(), firmaAnterior)))
                .isInstanceOfSatisfying(com.asiscontrol.exception.ConflictException.class,
                        error -> assertThat(error.getCode()).isEqualTo("PLAN_ACTUALIZADO"));
        verificarFinalizacionRevertida(solicitud);
        String firmaActual = planes.previewSolicitud(solicitud.id()).firmaPlan();
        assertThat(firmaActual).isNotEqualTo(firmaAnterior);
        var matricula = matriculas.crear(new CrearMatriculaRequest(solicitud.id(), solicitud.version(), firmaActual));
        var plan = planes.previewMatricula(matricula.id());
        assertThat(plan.items()).hasSize(10).allSatisfy(item -> {
            assertThat(item.montoTotal()).isEqualByComparingTo("450.00");
            assertThat(item.idCompromiso()).isNotNull();
        });
    }

    private long prepararOfertaFinanciera(int anio, boolean tarifa) {
        configuracion.crear(new CrearAnio(anio, LocalDate.of(anio, 1, 1), LocalDate.of(anio, 12, 31)));
        configuracion.agregarPeriodo(anio, new CrearPeriodo("Periodo anual", 1, LocalDate.of(anio, 3, 1), LocalDate.of(anio, 12, 31)));
        configuracion.agregarGrado(anio, new CrearGrado(1, 2));
        long seccion = secciones.crear(new AcademicoDtos.CrearSeccionRequest("A", 1, anio, 2, null, null)).id();
        var actual = configuracion.obtener(anio);
        configuracion.estado(anio, new EstadoAnio(EstadoAnioAcademico.ACTIVO, actual.version(), "Apertura del año"));
        if (tarifa) new TransactionTemplate(transactions).executeWithoutResult(status ->
                com.asiscontrol.support.TarifasTestSupport.configurar(em, anio, 1));
        return seccion;
    }

    private void verificarFinalizacionRevertida(SolicitudMatriculaResponse solicitud) {
        var actual = solicitudes.obtener(solicitud.id());
        assertThat(actual.estado()).isEqualTo(EstadoSolicitudMatricula.EN_REVISION);
        assertThat(actual.version()).isEqualTo(solicitud.version());
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            assertThat(em.createQuery("select count(m) from Matricula m where m.solicitudMatricula.id=:id", Long.class)
                    .setParameter("id", solicitud.id()).getSingleResult()).isZero();
            assertThat(em.createQuery("select count(c) from CompromisoPago c where c.matricula.solicitudMatricula.id=:id", Long.class)
                    .setParameter("id", solicitud.id()).getSingleResult()).isZero();
        });
    }

    @Test
    void dosTransaccionesCompitenPorLaUltimaVacante() throws Exception {
        configuracion.crear(
                new CrearAnio(2080, LocalDate.of(2080, 1, 1), LocalDate.of(2080, 12, 31)));
        configuracion.agregarPeriodo(
                2080,
                new CrearPeriodo(
                        "Primero", 1, LocalDate.of(2080, 3, 1), LocalDate.of(2080, 5, 31)));
        configuracion.agregarGrado(2080, new CrearGrado(1, 1));
        long section =
                secciones
                        .crear(new AcademicoDtos.CrearSeccionRequest("A", 1, 2080, 1, null, null))
                        .id();
        var year = configuracion.obtener(2080);
        configuracion.estado(
                2080, new EstadoAnio(EstadoAnioAcademico.ACTIVO, year.version(), "Inicio"));
        new TransactionTemplate(transactions).executeWithoutResult(status ->
                com.asiscontrol.support.TarifasTestSupport.configurar(em, 2080, 1));
        var first = solicitud(section);
        var second = solicitud(section);
        CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<String>> results =
                    List.of(
                            executor.submit(() -> finalizar(first, ready, start)),
                            executor.submit(() -> finalizar(second, ready, start)));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(
                            List.of(
                                    results.get(0).get(20, TimeUnit.SECONDS),
                                    results.get(1).get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("CREADA", "SECCION_SIN_VACANTES");
        }
        var current = configuracion.obtener(2080).secciones().getFirst();
        assertThat(current.ocupacion()).isEqualTo(1);
        assertThat(current.vacantes()).isZero();
    }

    @Test
    void unaOperacionRevertidaNoDejaAuditoriaDeExito() {
        long before = auditoria.count();
        var tx = new TransactionTemplate(transactions);
        assertThatThrownBy(
                        () ->
                                tx.execute(
                                        status -> {
                                            configuracion.crear(
                                                    new CrearAnio(
                                                            2079,
                                                            LocalDate.of(2079, 1, 1),
                                                            LocalDate.of(2079, 12, 31)));
                                            throw new IllegalStateException(
                                                    "Forzar reversión de prueba");
                                        }))
                .isInstanceOf(IllegalStateException.class);
        assertThat(anios.findByAnio(2079)).isEmpty();
        assertThat(auditoria.count()).isEqualTo(before);
    }

    private SolicitudMatriculaResponse solicitud(long section) {
        String key = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        var person =
                new PersonaDtos.PersonaRequest(
                        "Estudiante",
                        "Concurrente",
                        "Prueba",
                        TipoDocumento.PASAPORTE,
                        key,
                        null,
                        Sexo.PREFIERE_NO_INDICAR,
                        LocalDate.of(2010, 1, 1),
                        null,
                        key + "@example.test",
                        null);
        long student =
                personas.createAlumno(new PersonaDtos.AlumnoCreateRequest(person, "EST" + key))
                        .id();
        var request = solicitudes.crear(new CrearSolicitudMatriculaRequest(student, section, null));
        request =
                solicitudes.actualizar(
                        request.id(),
                        new ActualizarSolicitudMatriculaRequest(
                                null,
                                EstadoSolicitudMatricula.ENVIADA,
                                null,
                                null,
                                request.version()));
        return solicitudes.actualizar(
                request.id(),
                new ActualizarSolicitudMatriculaRequest(
                        null, EstadoSolicitudMatricula.EN_REVISION, null, null, request.version()));
    }

    private String finalizar(
            SolicitudMatriculaResponse request, CountDownLatch ready, CountDownLatch start)
            throws InterruptedException {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS))
            throw new IllegalStateException("No comenzó la prueba");
        try {
            matriculas.crear(new CrearMatriculaRequest(request.id(), request.version()));
            return "CREADA";
        } catch (BusinessRuleException error) {
            return error.getCode();
        }
    }
}
