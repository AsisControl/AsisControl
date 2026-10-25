package com.asiscontrol.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.asiscontrol.dto.dashboard.DashboardDtos.DashboardResponse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;

@SpringBootTest(
        properties =
                "spring.datasource.url=jdbc:h2:mem:asiscontrol_dashboard_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false")
@ActiveProfiles("test")
class DashboardServiceIntegrationTest {

    @Autowired private DashboardService dashboardService;

    @Test
    void ejecutaConsultasDeTodosLosRolesSinExponerDatosGlobalesPorDefecto() {
        List<String> roles =
                List.of(
                        "ADMINISTRADOR",
                        "DIRECTOR",
                        "SECRETARIA",
                        "TESORERIA",
                        "DOCENTE",
                        "ALUMNO",
                        "APODERADO");

        for (String rol : roles) {
            DashboardResponse response = dashboardService.obtener(authentication(rol));

            assertThat(response.rol()).isEqualTo(rol);
            assertThat(response.personaId()).isEqualTo(101L);
            assertThat(response.indicadores()).isNotEmpty();
            assertThat(response.indicadores())
                    .allSatisfy(indicador -> assertThat(indicador.valor()).isZero());
        }
    }

    private JwtAuthenticationToken authentication(String rol) {
        Instant now = Instant.now();
        Jwt jwt =
                Jwt.withTokenValue("token-de-prueba")
                        .header("alg", "none")
                        .subject("usuario-prueba")
                        .issuedAt(now)
                        .expiresAt(now.plusSeconds(600))
                        .claim("usuarioId", 100L)
                        .claim("personaId", 101L)
                        .claim("rol", rol)
                        .build();
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_" + rol)));
    }
}
