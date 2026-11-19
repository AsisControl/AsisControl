package com.asiscontrol.service;

import com.asiscontrol.dto.persona.PersonaDtos;
import com.asiscontrol.dto.seguridad.UsuarioDtos;
import com.asiscontrol.entity.enums.EstadoAcceso;
import com.asiscontrol.entity.enums.Sexo;
import com.asiscontrol.entity.enums.TipoDocumento;
import com.asiscontrol.exception.ConflictException;
import com.asiscontrol.repository.RolRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:ultimo_admin_auth;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
        "app.bootstrap.admin-document="
})
@ActiveProfiles("test")
class UltimoAdministradorIntegrationTest {

    @Autowired UsuarioService usuarios;
    @Autowired RolRepository roles;

    @Test
    void unaBajaDeCuentaYUnaRevocacionConcurrentesConservanUnAdministrador() throws Exception {
        var primero = registrar("34500001");
        var segundo = registrar("34500002");
        Long rolId = roles.findByNombreIgnoreCase("ADMINISTRADOR").orElseThrow().getId();
        var preparados = new CountDownLatch(2);
        var iniciar = new CountDownLatch(1);

        try (var tareas = Executors.newFixedThreadPool(2)) {
            var baja = tareas.submit(() -> operar(preparados, iniciar, () -> usuarios.update(
                    primero.id(), new UsuarioDtos.UpdateRequest(null, primero.correo(),
                            EstadoAcceso.DESACTIVADO, false, null, "Fin del acceso administrativo"))));
            var revocacion = tareas.submit(() -> operar(preparados, iniciar, () -> usuarios.cambiarRol(
                    segundo.id(), rolId, new UsuarioDtos.CambiarRolRequest(
                            false, "Fin de la asignación administrativa"))));

            boolean tareasPreparadas = preparados.await(5, TimeUnit.SECONDS);
            iniciar.countDown();
            assertThat(tareasPreparadas).isTrue();
            assertThat(List.of(baja.get(15, TimeUnit.SECONDS), revocacion.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("CONFIRMADA", "ULTIMO_ADMINISTRADOR");
        }

        assertThat(usuarios.list(null, EstadoAcceso.ACTIVO, rolId, 0, 20,
                "fechaCreacion", Sort.Direction.ASC).content()).hasSize(1);
    }

    private UsuarioDtos.RegistroAdministradorResponse registrar(String dni) {
        return usuarios.registrarAdministrador(new UsuarioDtos.RegistroAdministradorRequest(null,
                new PersonaDtos.PersonaRequest("Persona", "Prueba", null, TipoDocumento.DNI, dni,
                        null, Sexo.PREFIERE_NO_INDICAR, LocalDate.of(1985, 5, 10), null, null, null)));
    }

    private String operar(CountDownLatch preparados, CountDownLatch iniciar, Runnable accion)
            throws InterruptedException {
        preparados.countDown();
        if (!iniciar.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Las dos operaciones no pudieron iniciar juntas");
        }
        try {
            accion.run();
            return "CONFIRMADA";
        } catch (ConflictException rechazo) {
            return rechazo.getCode();
        }
    }
}
