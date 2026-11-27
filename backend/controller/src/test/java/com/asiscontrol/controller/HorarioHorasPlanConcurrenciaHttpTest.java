package com.asiscontrol.controller;

import com.asiscontrol.entity.Aula;
import com.asiscontrol.entity.Horario;
import com.asiscontrol.entity.enums.DiaSemana;
import com.asiscontrol.entity.enums.TipoAula;
import com.asiscontrol.repository.planificacion.AnioAcademicoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalTime;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** OBS-01: dos administradores a la vez no pueden pasar juntos las horas del plan. */
// Confirma datos reales: usa una base propia para no alterar las demás clases de prueba.
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:horario_horas_plan;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class HorarioHorasPlanConcurrenciaHttpTest extends ApiIntegrationSupport {
    @Autowired private AnioAcademicoRepository anios;
    @Autowired private PlatformTransactionManager transacciones;

    @Test
    void dosGuardadosSimultaneosNoPasanJuntosElTopeDelPlan() throws Exception {
        Aula aulaA = aula("Aula 601");
        Aula aulaB = aula("Aula 602");
        Horario programado = new Horario();
        programado.setAsignacionCurso(assignment);
        programado.setAula(aulaA);
        programado.setDiaSemana(DiaSemana.LUNES);
        programado.setHoraInicio(LocalTime.of(8, 0));
        programado.setHoraFin(LocalTime.of(11, 0));
        programado.setActivo(true);
        em.persist(programado);
        em.flush();
        // Las otras conexiones necesitan ver las 3 h ya programadas: se confirma la base propia de esta clase.
        TestTransaction.flagForCommit();
        TestTransaction.end();

        TransactionTemplate tx = new TransactionTemplate(transacciones);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        CountDownLatch intento = new CountDownLatch(1);
        try (var ejecutor = Executors.newSingleThreadExecutor()) {
            var segundo = tx.execute(estado -> {
                anios.bloquear(2090).orElseThrow();
                var pendiente = ejecutor.submit(() -> {
                    intento.countDown();
                    return request("POST", "/api/horarios", bloque(aulaB, DiaSemana.MIERCOLES), 422);
                });
                try {
                    assertThat(intento.await(10, TimeUnit.SECONDS)).isTrue();
                    // Debe esperar la planificación bloqueada en vez de sumar sobre una lectura antigua.
                    assertThatThrownBy(() -> pendiente.get(500, TimeUnit.MILLISECONDS))
                            .isInstanceOf(TimeoutException.class);
                    postOk("/api/horarios", bloque(aulaA, DiaSemana.MARTES));
                    return pendiente;
                } catch (Exception error) {
                    throw new IllegalStateException("No se pudo ejecutar el escenario concurrente", error);
                }
            });
            assertThat(segundo.get(20, TimeUnit.SECONDS).path("code").asText()).isEqualTo("HORARIO_EXCEDE_PLAN");
            Long vigentes = tx.execute(estado -> em.createQuery(
                    "select count(h) from Horario h where h.activo = true and h.asignacionCurso.id = :id",
                    Long.class).setParameter("id", assignment.getId()).getSingleResult());
            assertThat(vigentes).isEqualTo(2);
        }
    }

    private Map<String, Object> bloque(Aula aula, DiaSemana dia) {
        return data("asignacionCursoId", assignment.getId(), "aulaId", aula.getId(),
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
}