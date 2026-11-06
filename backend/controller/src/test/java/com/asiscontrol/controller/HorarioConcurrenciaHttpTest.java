package com.asiscontrol.controller;

import com.asiscontrol.entity.AsignacionCurso;
import com.asiscontrol.entity.Aula;
import com.asiscontrol.entity.Curso;
import com.asiscontrol.entity.Docente;
import com.asiscontrol.entity.Seccion;
import com.asiscontrol.entity.enums.DiaSemana;
import com.asiscontrol.entity.enums.Especialidad;
import com.asiscontrol.entity.enums.TipoAula;
import com.asiscontrol.repository.planificacion.AnioAcademicoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Dos conexiones reales: el segundo administrador espera y revalida tras el commit del primero. */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class HorarioConcurrenciaHttpTest extends ApiIntegrationSupport {
    @Autowired private AnioAcademicoRepository anios;
    @Autowired private PlatformTransactionManager transacciones;

    @Test
    void serializaLaUltimaValidacionDeAulaDocenteYSeccion() throws Exception {
        Aula aulaA = aula("Aula 401");
        Aula aulaB = aula("Aula 402");
        Docente otroDocente = new Docente();
        otroDocente.setPersona(person());
        otroDocente.setCodigoDocente("DOC" + key());
        otroDocente.setEspecialidad(Especialidad.COMUNICACION);
        otroDocente.setFechaIngreso(LocalDate.of(2025, 1, 1));
        em.persist(otroDocente);
        Seccion seccionB = new Seccion();
        seccionB.setNombre("B");
        seccionB.setAnioAcademico(2090);
        seccionB.setGrado(1);
        seccionB.setCapacidadMaxima(30);
        em.persist(seccionB);
        AsignacionCurso otroContexto = asignacion(seccionB, otroDocente);
        AsignacionCurso mismoDocente = asignacion(seccionB, teacher);
        AsignacionCurso mismaSeccion = asignacion(section, otroDocente);
        em.flush();
        // Las otras conexiones necesitan ver el escenario: aquí se confirma SOLO la BD de pruebas.
        TestTransaction.flagForCommit();
        TestTransaction.end();

        comprobarCompetencia("AULA", DiaSemana.LUNES, otroContexto, aulaA, aulaA);
        comprobarCompetencia("DOCENTE", DiaSemana.MARTES, mismoDocente, aulaA, aulaB);
        comprobarCompetencia("SECCION", DiaSemana.MIERCOLES, mismaSeccion, aulaA, aulaB);
    }

    private void comprobarCompetencia(String recurso, DiaSemana dia, AsignacionCurso segunda,
            Aula aulaPrimera, Aula aulaSegunda) throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transacciones);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        CountDownLatch intento = new CountDownLatch(1);
        try (var ejecutor = Executors.newSingleThreadExecutor()) {
            var resultado = tx.execute(estado -> {
                anios.bloquear(2090).orElseThrow();
                var segundo = ejecutor.submit(() -> {
                    intento.countDown();
                    return request("POST", "/api/horarios", bloque(segunda, aulaSegunda, dia), 409);
                });
                try {
                    assertThat(intento.await(10, TimeUnit.SECONDS)).isTrue();
                    // Debe esperar la fila bloqueada, en vez de aprobar sobre una lectura antigua.
                    assertThatThrownBy(() -> segundo.get(500, TimeUnit.MILLISECONDS))
                            .isInstanceOf(TimeoutException.class);
                    postOk("/api/horarios", bloque(assignment, aulaPrimera, dia));
                    return segundo;
                } catch (Exception error) {
                    throw new IllegalStateException("No se pudo ejecutar el escenario concurrente", error);
                }
            });
            String codigo = resultado.get(20, TimeUnit.SECONDS).path("code").asText();
            assertThat(codigo).isEqualTo("HORARIO_" + recurso + "_OCUPAD"
                    + (recurso.equals("DOCENTE") ? "O" : "A"));
            Long cantidad = tx.execute(estado -> em.createQuery(
                    "select count(h) from Horario h where h.activo = true and h.diaSemana = :dia",
                    Long.class).setParameter("dia", dia).getSingleResult());
            assertThat(cantidad).isEqualTo(1);
        }
    }

    private Map<String, Object> bloque(AsignacionCurso asignacion, Aula aula, DiaSemana dia) {
        return data("asignacionCursoId", asignacion.getId(), "aulaId", aula.getId(),
                "diaSemana", dia, "horaInicio", "08:00", "horaFin", "09:00");
    }

    private Aula aula(String nombre) {
        Aula aula = new Aula();
        aula.setCodigo("AU" + key());
        aula.setNombre(nombre);
        aula.setCapacidad(30);
        aula.setTipoAula(TipoAula.AULA_REGULAR);
        aula.setUbicacion("Pabellon de pruebas");
        em.persist(aula);
        return aula;
    }

    private AsignacionCurso asignacion(Seccion seccion, Docente docente) {
        Curso curso = new Curso();
        curso.setCodigo("CUR" + key());
        curso.setNombre("Curso de prueba");
        curso.setGradoAcademico(grade);
        curso.setHorasSemanales(4);
        em.persist(curso);
        AsignacionCurso asignacion = new AsignacionCurso();
        asignacion.setCurso(curso);
        asignacion.setSeccion(seccion);
        asignacion.setDocente(docente);
        em.persist(asignacion);
        return asignacion;
    }
}
