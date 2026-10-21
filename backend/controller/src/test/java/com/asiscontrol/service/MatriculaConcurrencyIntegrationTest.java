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
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MatriculaConcurrencyIntegrationTest {
    @Autowired ConfiguracionAcademicaService configuracion;
    @Autowired SeccionService secciones;
    @Autowired PersonaService personas;
    @Autowired SolicitudMatriculaService solicitudes;
    @Autowired MatriculaService matriculas;
    @Autowired RegistroAuditoriaRepository auditoria;
    @Autowired AnioAcademicoRepository anios;
    @Autowired PlatformTransactionManager transactions;

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
