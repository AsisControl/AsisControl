package com.asiscontrol.service;

import com.asiscontrol.dto.PagoDto;
import com.asiscontrol.entity.Alumno;
import com.asiscontrol.entity.Matricula;
import com.asiscontrol.entity.Persona;
import com.asiscontrol.entity.Seccion;
import com.asiscontrol.entity.SolicitudMatricula;
import com.asiscontrol.entity.enums.EstadoAnioAcademico;
import com.asiscontrol.entity.enums.EstadoMatricula;
import com.asiscontrol.entity.enums.EstadoSolicitudMatricula;
import com.asiscontrol.entity.enums.Sexo;
import com.asiscontrol.entity.enums.TipoDocumento;
import com.asiscontrol.entity.planificacion.AnioAcademico;
import com.asiscontrol.repository.CompromisoPagoRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Matrículas distintas evitan que su bloqueo oculte una carrera del correlativo global. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:compromiso_codigo_concurrency;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false"
})
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CompromisoCodigoConcurrencyIntegrationTest {

    @Autowired CompromisoPagoService compromisos;
    @Autowired CompromisoPagoRepository repository;
    @Autowired PlatformTransactionManager transactions;
    @Autowired EntityManager em;

    @Test
    void dosMatriculasEmitenCorrelativosUnicosYElSiguienteSeConservaTrasConfirmar() throws Exception {
        var tx = new TransactionTemplate(transactions);
        List<Long> matriculas = tx.execute(status -> prepararMatriculas());
        assertThat(matriculas).hasSize(2);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<String> codigos;
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<String>> results = List.of(
                    executor.submit(() -> emitir(matriculas.get(0), ready, start)),
                    executor.submit(() -> emitir(matriculas.get(1), ready, start)));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            codigos = List.of(
                    results.get(0).get(30, TimeUnit.SECONDS),
                    results.get(1).get(30, TimeUnit.SECONDS));
        }

        assertThat(codigos).containsExactlyInAnyOrder("CP-000001", "CP-000002");
        for (String codigo : codigos) {
            assertThat(repository.findByCodigoCompromisoIgnoreCase(codigo)).isPresent();
        }
        // Una transacción posterior debe continuar donde quedaron las dos emisiones confirmadas.
        var siguiente = compromisos.emitir(solicitud(matriculas.getFirst(), 2));
        assertThat(siguiente.codigoCompromiso()).isEqualTo("CP-000003");
        assertThat(repository.count()).isEqualTo(3);
    }

    private String emitir(Long matriculaId, CountDownLatch ready, CountDownLatch start) {
        return new TransactionTemplate(transactions).execute(status -> {
            ready.countDown();
            try {
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("No comenzó la prueba de emisión concurrente");
                }
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Se interrumpió la prueba de emisión", error);
            }
            return compromisos.emitir(solicitud(matriculaId, 1)).codigoCompromiso();
        });
    }

    private PagoDto.CompromisoRequest solicitud(Long matriculaId, int cuota) {
        return new PagoDto.CompromisoRequest(
                matriculaId, null, "Pensión concurrente", null,
                new BigDecimal("350.00"), "PEN", LocalDate.of(2081, 4, 30), cuota);
    }

    private List<Long> prepararMatriculas() {
        var year = new AnioAcademico();
        year.setAnio(2081);
        year.setFechaInicio(LocalDate.of(2081, 1, 1));
        year.setFechaFin(LocalDate.of(2081, 12, 31));
        year.setEstado(EstadoAnioAcademico.ACTIVO);
        em.persist(year);

        var section = new Seccion();
        section.setNombre("Concurrente");
        section.setGrado(1);
        section.setAnioAcademico(2081);
        section.setCapacidadMaxima(2);
        em.persist(section);

        var first = crearMatricula(section);
        var second = crearMatricula(section);
        em.flush();
        return List.of(first.getId(), second.getId());
    }

    private Matricula crearMatricula(Seccion section) {
        String key = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        var person = new Persona();
        person.setNombres("Estudiante");
        person.setApellidoPaterno("Concurrente");
        person.setApellidoMaterno("Prueba");
        person.setTipoDocumento(TipoDocumento.PASAPORTE);
        person.setNumeroDocumento(key);
        person.setSexo(Sexo.PREFIERE_NO_INDICAR);
        person.setFechaNacimiento(LocalDate.of(2010, 1, 1));
        person.setCorreo(key + "@example.test");
        em.persist(person);

        var student = new Alumno();
        student.setPersona(person);
        student.setCodigoAlumno("EST" + key);
        em.persist(student);

        var request = new SolicitudMatricula();
        request.setAlumno(student);
        request.setSeccion(section);
        request.setAnioAcademico(2081);
        request.setGrado(1);
        request.setEstado(EstadoSolicitudMatricula.MATRICULA_FINALIZADA);
        em.persist(request);

        var enrollment = new Matricula();
        enrollment.setAlumno(student);
        enrollment.setSeccion(section);
        enrollment.setSolicitudMatricula(request);
        enrollment.setCodigoMatricula("MAT" + key);
        enrollment.setAnioAcademico(2081);
        enrollment.setFechaMatricula(Instant.now());
        enrollment.setEstado(EstadoMatricula.ACTIVA);
        em.persist(enrollment);
        return enrollment;
    }
}
