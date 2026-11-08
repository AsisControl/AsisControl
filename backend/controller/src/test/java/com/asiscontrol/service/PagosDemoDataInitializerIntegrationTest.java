package com.asiscontrol.service;

import com.asiscontrol.config.PagosDemoDataInitializer;
import com.asiscontrol.entity.AccesoUsuario;
import com.asiscontrol.entity.AlumnoApoderado;
import com.asiscontrol.entity.CompromisoPago;
import com.asiscontrol.entity.Persona;
import com.asiscontrol.entity.enums.TipoDocumento;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** La demo es opt-in y usa una BD aislada; no toca la base local ni la compartida de otras pruebas. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:pagos_demo_seed;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false",
        "app.pagos.demo.enabled=true", "app.pagos.demo.password=DemoPago2026Seguro",
        "app.bootstrap.enabled=true"
})
@ActiveProfiles({"test", "local"})
@Import(PagosDemoDataInitializerIntegrationTest.DocumentoReservadoFixture.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class PagosDemoDataInitializerIntegrationTest {
    @Autowired EntityManager em;
    @Autowired PagosDemoDataInitializer demo;
    @Autowired PasswordEncoder passwords;
    @Autowired PersonaPrevia personaPrevia;

    @Test
    void preparaDosFamiliasSeparadasConSaldosYComprobantesSinRestablecerDatosAlRepetir() {
        var accesos = em.createQuery("from AccesoUsuario a where a.username like 'demo.pagos.%' order by a.username",
                AccesoUsuario.class).getResultList();
        assertThat(accesos).hasSize(2).allSatisfy(acceso -> {
            assertThat(acceso.getRol().getNombre()).isEqualTo("APODERADO");
            assertThat(passwords.matches("DemoPago2026Seguro", acceso.getContrasenaHash())).isTrue();
            assertThat(acceso.isRequiereCambioContrasena()).isFalse();
        });
        var primera = vinculados(accesos.get(0).getPersona().getId());
        var segunda = vinculados(accesos.get(1).getPersona().getId());
        assertThat(primera).hasSize(2);
        assertThat(segunda).hasSize(1);
        assertThat(primera.stream().map(v -> v.getAlumno().getId()))
                .doesNotContainAnyElementsOf(segunda.stream().map(v -> v.getAlumno().getId()).toList());
        assertThat(contar("CompromisoPago")).isEqualTo(9);
        assertThat(contar("TransaccionPago")).isEqualTo(3);
        assertThat(contar("Comprobante")).isEqualTo(3);
        var comprometidos = em.createQuery("from CompromisoPago", CompromisoPago.class).getResultList();
        assertThat(comprometidos.stream().filter(c -> c.getMontoPagado().signum() > 0).count()).isEqualTo(3);
        // Una clave cambiada por el usuario y un pago posterior no deben resetearse al reiniciar la demo.
        String hashPropio = passwords.encode("MiNuevaClave2026");
        accesos.getFirst().setContrasenaHash(hashPropio);
        var pendiente = comprometidos.stream().filter(c -> c.getMontoPagado().signum() == 0).findFirst().orElseThrow();
        pendiente.setMontoPagado(new java.math.BigDecimal("25.00")); em.flush();
        demo.preparar(); em.flush(); em.clear();
        assertThat(em.find(AccesoUsuario.class, accesos.getFirst().getId()).getContrasenaHash()).isEqualTo(hashPropio);
        assertThat(em.find(CompromisoPago.class, pendiente.getId()).getMontoPagado()).isEqualByComparingTo("25.00");
        assertThat(contar("CompromisoPago")).isEqualTo(9);
        assertThat(contar("Comprobante")).isEqualTo(3);
    }

    @Test
    void siUnDniReservadoYaExisteConservaSuPersonaYAsignaCincoDocumentosLibresALaDemo() {
        var anterior = em.find(Persona.class, personaPrevia.id);
        assertThat(anterior.getTipoDocumento()).isEqualTo(TipoDocumento.DNI);
        assertThat(anterior.getNumeroDocumento()).isEqualTo("90000001");
        assertThat(anterior.getNombres()).isEqualTo("Persona existente");
        assertThat(anterior.getApellidoPaterno()).isEqualTo("Conservar");
        assertThat(anterior.getApellidoMaterno()).isEqualTo("Intacta");
        assertThat(anterior.getFechaNacimiento()).isEqualTo(LocalDate.of(1980, 2, 3));
        assertThat(anterior.getCorreo()).isEqualTo("persona.previa@example.test");
        assertThat(anterior.getTelefono()).isEqualTo("987654321");
        assertThat(anterior.getDireccion()).isEqualTo("Domicilio previo de prueba");
        assertThat(anterior.getFechaCreacion()).isEqualTo(personaPrevia.creadaEn);
        assertThat(anterior.getFechaActualizacion()).isEqualTo(personaPrevia.actualizadaEn);
        var accesos = em.createQuery("from AccesoUsuario a where a.username like 'demo.pagos.%' order by a.username",
                AccesoUsuario.class).getResultList();
        assertThat(accesos).hasSize(2);
        var personasDemo = new java.util.ArrayList<Persona>();
        accesos.forEach(acceso -> {
            personasDemo.add(acceso.getPersona());
            vinculados(acceso.getPersona().getId()).forEach(vinculo -> personasDemo.add(vinculo.getAlumno().getPersona()));
        });
        assertThat(personasDemo).hasSize(5);
        assertThat(personasDemo.stream().map(Persona::getId)).doesNotHaveDuplicates().doesNotContain(personaPrevia.id);
        assertThat(personasDemo.stream().map(Persona::getNumeroDocumento))
                .doesNotHaveDuplicates().doesNotContain("90000001")
                .allSatisfy(documento -> assertThat(documento).matches("[0-9]{8}"));
        assertThat(accesos.get(0).getPersona().getNumeroDocumento()).isEqualTo("90000002");
        assertThat(accesos.get(1).getPersona().getNumeroDocumento()).isEqualTo("90000003");
        assertThat(contar("Persona")).isEqualTo(6);
    }

    private java.util.List<AlumnoApoderado> vinculados(Long personaId) {
        return em.createQuery("from AlumnoApoderado v where v.apoderado.persona.id=:id", AlumnoApoderado.class)
                .setParameter("id", personaId).getResultList();
    }

    private long contar(String entity) {
        return em.createQuery("select count(e) from " + entity + " e", Long.class).getSingleResult();
    }

    static class PersonaPrevia {
        Long id;
        Instant creadaEn;
        Instant actualizadaEn;
    }

    /** Los runners terminan antes de ApplicationReadyEvent, que es cuando se prepara la demo. */
    @TestConfiguration(proxyBeanMethods = false)
    static class DocumentoReservadoFixture {
        @Bean
        PersonaPrevia personaPrevia() {
            return new PersonaPrevia();
        }

        @Bean
        ApplicationRunner ocuparDniAntesDeLaDemo(EntityManager em,
                PlatformTransactionManager transactions, PersonaPrevia previa) {
            return args -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
                var persona = new Persona();
                persona.setTipoDocumento(TipoDocumento.DNI);
                persona.setNumeroDocumento("90000001");
                persona.setNombres("Persona existente");
                persona.setApellidoPaterno("Conservar");
                persona.setApellidoMaterno("Intacta");
                persona.setFechaNacimiento(LocalDate.of(1980, 2, 3));
                persona.setCorreo("persona.previa@example.test");
                persona.setTelefono("987654321");
                persona.setDireccion("Domicilio previo de prueba");
                em.persist(persona);
                em.flush();
                em.refresh(persona);
                previa.id = persona.getId();
                previa.creadaEn = persona.getFechaCreacion();
                previa.actualizadaEn = persona.getFechaActualizacion();
            });
        }
    }
}
