package com.asiscontrol.service;

import com.asiscontrol.dto.persona.PersonaDtos;
import com.asiscontrol.entity.Alumno;
import com.asiscontrol.entity.Persona;
import com.asiscontrol.entity.enums.Especialidad;
import com.asiscontrol.entity.enums.Sexo;
import com.asiscontrol.entity.enums.TipoDocumento;
import com.asiscontrol.service.CodigoInstitucionalService.Prefijo;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Cada prueba usa un año propio para no compartir secuencias entre pruebas. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:codigo_institucional;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false"
})
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CodigoInstitucionalIntegrationTest {

    @Autowired CodigoInstitucionalService codigos;
    @Autowired PersonaService personas;
    @Autowired PlatformTransactionManager transactions;
    @Autowired EntityManager em;

    @Test
    void cadaPrefijoTieneSuSecuenciaYSeReiniciaCadaAnio() {
        assertThat(enTransaccion(() -> codigos.siguiente(Prefijo.EST, 2091))).isEqualTo("EST20910001");
        assertThat(enTransaccion(() -> codigos.siguiente(Prefijo.EST, 2091))).isEqualTo("EST20910002");
        assertThat(enTransaccion(() -> codigos.siguiente(Prefijo.DOC, 2091))).isEqualTo("DOC20910001");
        assertThat(enTransaccion(() -> codigos.siguiente(Prefijo.APO, 2091))).isEqualTo("APO20910001");
        assertThat(enTransaccion(() -> codigos.siguiente(Prefijo.ADM, 2091))).isEqualTo("ADM20910001");
        assertThat(enTransaccion(() -> codigos.siguiente(Prefijo.EST, 2092))).isEqualTo("EST20920001");
        assertThat(enTransaccion(() -> codigos.siguiente(Prefijo.EST, 2091))).isEqualTo("EST20910003");
    }

    @Test
    void unAltaDeshechaNoConsumeSuNumero() {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            assertThat(codigos.siguiente(Prefijo.DOC, 2093)).isEqualTo("DOC20930001");
            status.setRollbackOnly();
        });
        assertThat(enTransaccion(() -> codigos.siguiente(Prefijo.DOC, 2093))).isEqualTo("DOC20930001");
    }

    @Test
    void saltaUnCodigoQueYaEstaRegistrado() {
        enTransaccion(() -> {
            var alumno = new Alumno();
            alumno.setPersona(persona());
            alumno.setCodigoAlumno("EST20940001");
            em.persist(alumno);
            return null;
        });
        assertThat(enTransaccion(() -> codigos.siguiente(Prefijo.EST, 2094))).isEqualTo("EST20940002");
    }

    @Test
    void exigeLaTransaccionDelAlta() {
        assertThatThrownBy(() -> codigos.siguiente(Prefijo.EST, 2095))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void dosAltasSimultaneasNoRepitenCodigo() throws Exception {
        int hilos = 4;
        CountDownLatch listos = new CountDownLatch(hilos);
        CountDownLatch inicio = new CountDownLatch(1);
        List<String> emitidos = new ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(hilos)) {
            List<Future<String>> resultados = new ArrayList<>();
            for (int i = 0; i < hilos; i++) {
                resultados.add(executor.submit(() -> new TransactionTemplate(transactions).execute(status -> {
                    listos.countDown();
                    esperar(inicio);
                    return codigos.siguiente(Prefijo.APO, 2096);
                })));
            }
            assertThat(listos.await(10, TimeUnit.SECONDS)).isTrue();
            inicio.countDown();
            for (var resultado : resultados) emitidos.add(resultado.get(30, TimeUnit.SECONDS));
        }
        assertThat(emitidos).containsExactlyInAnyOrder(
                "APO20960001", "APO20960002", "APO20960003", "APO20960004");
    }

    @Test
    void lasAltasDePerfilesRecibenElCodigoDelAnioEnCurso() {
        int anio = LocalDate.now(CodigoInstitucionalService.ZONA).getYear();
        var docente = personas.createDocente(new PersonaDtos.DocenteCreateRequest(
                solicitud(), LocalDate.of(2020, 3, 1), Especialidad.MATEMATICA));
        var apoderado = personas.createApoderado(new PersonaDtos.ApoderadoCreateRequest(solicitud()));
        var alumno = personas.createAlumno(new PersonaDtos.AlumnoCreateRequest(solicitud()));
        assertThat(docente.codigoDocente()).matches("DOC" + anio + "\\d{4}");
        assertThat(apoderado.codigoApoderado()).matches("APO" + anio + "\\d{4}");
        assertThat(alumno.codigoAlumno()).matches("EST" + anio + "\\d{4}");
    }

    private <T> T enTransaccion(java.util.function.Supplier<T> accion) {
        return new TransactionTemplate(transactions).execute(status -> accion.get());
    }

    private static void esperar(CountDownLatch inicio) {
        try {
            if (!inicio.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Sin inicio");
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(error);
        }
    }

    private Persona persona() {
        String clave = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        var persona = new Persona();
        persona.setNombres("Prueba");
        persona.setApellidoPaterno("Codigo");
        persona.setTipoDocumento(TipoDocumento.PASAPORTE);
        persona.setNumeroDocumento(clave);
        persona.setFechaNacimiento(LocalDate.of(2010, 1, 1));
        em.persist(persona);
        return persona;
    }

    private static PersonaDtos.PersonaRequest solicitud() {
        String documento = String.valueOf(10_000_000 + (int) (Math.random() * 89_999_999));
        return new PersonaDtos.PersonaRequest(
                "Lucia", "Ramos", "Vega", TipoDocumento.DNI, documento, null, Sexo.FEMENINO,
                LocalDate.of(2011, 4, 2), null, "lucia" + documento + "@example.test", null);
    }
}
