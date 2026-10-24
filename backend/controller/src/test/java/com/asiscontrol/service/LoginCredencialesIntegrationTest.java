package com.asiscontrol.service;

import com.asiscontrol.dto.persona.PersonaDtos;
import com.asiscontrol.dto.seguridad.UsuarioDtos;
import com.asiscontrol.entity.enums.Sexo;
import com.asiscontrol.entity.enums.TipoDocumento;
import com.asiscontrol.repository.RolRepository;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class LoginCredencialesIntegrationTest {
    private static final String ORIGIN = "http://localhost:3000";
    @Autowired WebApplicationContext context;
    @Autowired UsuarioService usuarios;
    @Autowired RolRepository roles;
    private MockMvc mvc;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private org.springframework.test.web.servlet.ResultActions login(String usuario, String clave)
            throws Exception {
        return mvc.perform(post("/api/auth/login").header("Origin", ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"identifier\":\"" + usuario + "\",\"password\":\"" + clave + "\"}"));
    }

    // Sin transaccion de test a proposito: el error original (500) solo aparecia al confirmar
    // la transaccion del login, y una transaccion de test lo ocultaria.
    @Test void usuarioInexistenteRecibeErrorDeCredenciales() throws Exception {
        login("usuario-que-no-existe-" + UUID.randomUUID(), "Cualquiera123!")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("CREDENCIALES_INVALIDAS"))
                .andExpect(jsonPath("$.message").value("Usuario o contraseña incorrectos."));
    }

    // Se deshace al terminar para no dejar el usuario creado: otros tests, como el del
    // dashboard, esperan no encontrar datos de una persona concreta.
    @Test
    @org.springframework.transaction.annotation.Transactional
    void claveIncorrectaRecibeLaMismaRespuestaQueUnUsuarioInexistente() throws Exception {
        String key = UUID.randomUUID().toString().replace("-", "").substring(0, 14);
        var persona = new PersonaDtos.PersonaRequest("Persona", "Prueba", "Login",
                TipoDocumento.PASAPORTE, key, null, Sexo.PREFIERE_NO_INDICAR,
                LocalDate.of(1990, 1, 1), null, key + "@example.test", null);
        var usuario = usuarios.provision(new UsuarioDtos.ProvisionRequest(null, persona,
                roles.findByNombreIgnoreCase("ALUMNO").orElseThrow().getId(), "usr" + key,
                "login" + key + "@example.test", "Temporal123!", "EST" + key,
                null, null, null, Set.of()));

        login(usuario.username(), "Incorrecta123!")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("CREDENCIALES_INVALIDAS"))
                .andExpect(jsonPath("$.message").value("Usuario o contraseña incorrectos."));
    }

    @Test void unaPeticionSinSesionExplicaQueLaSesionExpiro() throws Exception {
        mvc.perform(get("/api/auth/me").header("Origin", ORIGIN))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTENTICACION_REQUERIDA"))
                .andExpect(jsonPath("$.message")
                        .value("Tu sesión expiró o no es válida. Inicia sesión nuevamente."));
    }
}
