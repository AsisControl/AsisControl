package com.asiscontrol.service;

import com.asiscontrol.dto.persona.PersonaDtos;
import com.asiscontrol.dto.seguridad.UsuarioDtos;
import com.asiscontrol.entity.enums.Sexo;
import com.asiscontrol.entity.enums.TipoDocumento;
import com.asiscontrol.repository.AccesoUsuarioRepository;
import com.asiscontrol.repository.RolRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RegeneracionIdentificadoresCuentaIntegrationTest {
    @Autowired UsuarioService usuarios;
    @Autowired RolRepository roles;
    @Autowired AccesoUsuarioRepository cuentas;
    @Autowired RegeneracionIdentificadoresCuenta regeneracion;
    @Autowired EntityManager em;

    private UsuarioDtos.Response alta(String nombres, String apellido) {
        String key = UUID.randomUUID().toString().replace("-", "").substring(0, 14);
        var persona = new PersonaDtos.PersonaRequest(nombres, apellido, "Regen",
                TipoDocumento.PASAPORTE, key, null, Sexo.PREFIERE_NO_INDICAR,
                LocalDate.of(1990, 1, 1), null, key + "@example.test", null);
        return usuarios.provision(new UsuarioDtos.ProvisionRequest(null, persona,
                roles.findByNombreIgnoreCase("ALUMNO").orElseThrow().getId(), "Temporal123!", null, null, Set.of()));
    }

    /** Deja la cuenta como la dejó la migración V25: el usuario es el código y el correo el antiguo. */
    private void comoMigrada(Long id, String correo, boolean verificado) {
        em.createNativeQuery("update AccesoUsuario set username = codigo, correo = :correo, "
                        + "correo_verificado = :verificado where id_acceso_usuario = :id")
                .setParameter("correo", correo).setParameter("verificado", verificado)
                .setParameter("id", id).executeUpdate();
        em.clear();
    }

    @Test void generaElUsuarioYElCorreoDeLasCuentasQueSoloTenianCodigo() {
        var cuenta = alta("Maria Jose", "Quispe");
        comoMigrada(cuenta.id(), "est20260001@asiscontrol.edu.pe", false);

        assertThat(regeneracion.regenerar()).isGreaterThanOrEqualTo(1);

        var actual = cuentas.findById(cuenta.id()).orElseThrow();
        assertThat(actual.getUsername()).startsWith("maria.quispe");
        assertThat(actual.getCorreo()).isEqualTo("a" + actual.getCodigo() + "@asiscontrol.edu.pe");
        assertThat(actual.getCodigo()).matches("[0-9]{8}");
        assertThat(regeneracion.regenerar()).isZero();
    }

    @Test void noTocaUnCorreoPersonalNiUnoVerificado() {
        var personal = alta("Ana", "Personal");
        comoMigrada(personal.id(), "ana@gmail.com", false);
        var verificado = alta("Luis", "Verificado");
        comoMigrada(verificado.id(), "luis.v@asiscontrol.edu.pe", true);

        regeneracion.regenerar();

        assertThat(cuentas.findById(personal.id()).orElseThrow().getCorreo()).isEqualTo("ana@gmail.com");
        assertThat(cuentas.findById(verificado.id()).orElseThrow().getCorreo())
                .isEqualTo("luis.v@asiscontrol.edu.pe");
        assertThat(cuentas.findById(personal.id()).orElseThrow().getUsername()).isEqualTo("ana.personal");
    }

    @Test void homonimosRecibenSufijo() {
        var uno = alta("Pedro", "Homonimo");
        var dos = alta("Pedro", "Homonimo");
        comoMigrada(uno.id(), "a@asiscontrol.edu.pe", false);
        comoMigrada(dos.id(), "b@asiscontrol.edu.pe", false);

        regeneracion.regenerar();

        var nombres = java.util.stream.Stream.of(uno.id(), dos.id())
                .map(id -> cuentas.findById(id).orElseThrow().getUsername()).toList();
        assertThat(nombres).doesNotHaveDuplicates().allSatisfy(n -> assertThat(n).startsWith("pedro.homonimo"));
    }
}
