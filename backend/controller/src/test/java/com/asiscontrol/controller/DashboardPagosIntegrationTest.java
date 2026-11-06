package com.asiscontrol.controller;

import com.asiscontrol.dto.dashboard.DashboardDtos.DashboardResponse;
import com.asiscontrol.entity.Alumno;
import com.asiscontrol.entity.CompromisoPago;
import com.asiscontrol.entity.Matricula;
import com.asiscontrol.entity.SolicitudMatricula;
import com.asiscontrol.entity.enums.EstadoAdministrativoCompromiso;
import com.asiscontrol.entity.enums.EstadoCompromisoPago;
import com.asiscontrol.entity.enums.EstadoMatricula;
import com.asiscontrol.entity.enums.EstadoSolicitudMatricula;
import com.asiscontrol.service.DashboardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DashboardPagosIntegrationTest extends ApiIntegrationSupport {

    @Autowired
    private DashboardService dashboardService;

    @Test
    void derivaVencimientoYSaldoSinDependerDelEstadoGuardadoNiContarAnulados() {
        Matricula matricula = matriculaEstudiante();
        LocalDate hoy = LocalDate.now(ZoneId.of("America/Lima"));
        CompromisoPago vencido = compromiso(matricula, hoy.minusDays(1), "20.00");
        compromiso(matricula, hoy, "0.00").setEstado(EstadoCompromisoPago.VENCIDO);
        compromiso(matricula, hoy.plusDays(1), "30.00").setEstado(EstadoCompromisoPago.PAGADO);
        compromiso(matricula, hoy.minusDays(1), "100.00");
        compromiso(matricula, hoy.minusDays(1), "0.00")
                .setEstadoAdministrativo(EstadoAdministrativoCompromiso.ANULADO);
        compromiso(matricula, hoy.minusDays(1), "0.00").setActivo(false);
        em.flush();

        DashboardResponse tesoreria = dashboardService.obtener(authentication("TESORERIA", admin.getPersona().getId()));
        assertThat(indicador(tesoreria, "PAGOS_PENDIENTES")).isEqualTo(2);
        assertThat(indicador(tesoreria, "PAGOS_VENCIDOS")).isEqualTo(1);
        assertThat(indicador(dashboardService.obtener(authentication("ADMINISTRADOR", admin.getPersona().getId())),
                "PAGOS_VENCIDOS")).isEqualTo(1);
        assertThat(indicador(dashboardService.obtener(authentication("DIRECTOR", admin.getPersona().getId())),
                "PAGOS_VENCIDOS")).isEqualTo(1);

        em.refresh(vencido);
        assertThat(vencido.getEstado()).isEqualTo(EstadoCompromisoPago.PENDIENTE);
    }

    @Test
    void cuentaObligacionesConSaldoSoloDelEstudianteOVinculoConsultado() {
        Matricula matricula = matriculaEstudiante();
        LocalDate hoy = LocalDate.now(ZoneId.of("America/Lima"));
        compromiso(matricula, hoy.minusDays(1), "20.00");
        compromiso(matricula, hoy.plusDays(1), "0.00").setEstado(EstadoCompromisoPago.PAGADO);
        compromiso(matricula, hoy, "100.00");
        compromiso(matricula, hoy, "0.00")
                .setEstadoAdministrativo(EstadoAdministrativoCompromiso.ANULADO);

        Alumno otroEstudiante = new Alumno();
        otroEstudiante.setPersona(person());
        otroEstudiante.setCodigoAlumno("EST" + key());
        em.persist(otroEstudiante);
        compromiso(matricula(otroEstudiante), hoy, "0.00");
        em.flush();

        DashboardResponse alumno = dashboardService.obtener(authentication("ALUMNO", student.getPersona().getId()));
        DashboardResponse apoderado = dashboardService.obtener(authentication("APODERADO", guardian.getPersona().getId()));
        assertThat(indicador(alumno, "PAGOS_PENDIENTES")).isEqualTo(2);
        assertThat(indicador(apoderado, "PAGOS_PENDIENTES")).isEqualTo(2);
        assertThat(indicador(dashboardService.obtener(authentication("APODERADO", otroEstudiante.getPersona().getId())),
                "PAGOS_PENDIENTES")).isZero();
    }

    private CompromisoPago compromiso(Matricula matricula, LocalDate vencimiento, String pagado) {
        CompromisoPago compromiso = new CompromisoPago();
        compromiso.setMatricula(matricula);
        compromiso.setCodigoCompromiso("CP-" + key());
        compromiso.setConcepto("Pensión");
        compromiso.setMontoTotal(new BigDecimal("100.00"));
        compromiso.setMontoPagado(new BigDecimal(pagado));
        compromiso.setFechaVencimiento(vencimiento);
        em.persist(compromiso);
        return compromiso;
    }

    private Matricula matriculaEstudiante() {
        return em.createQuery("SELECT m FROM Matricula m WHERE m.alumno.id = :id", Matricula.class)
                .setParameter("id", student.getId()).getSingleResult();
    }

    private Matricula matricula(Alumno alumno) {
        SolicitudMatricula solicitud = new SolicitudMatricula();
        solicitud.setAlumno(alumno);
        solicitud.setSeccion(section);
        solicitud.setEstado(EstadoSolicitudMatricula.MATRICULA_FINALIZADA);
        em.persist(solicitud);
        Matricula matricula = new Matricula();
        matricula.setAlumno(alumno);
        matricula.setSeccion(section);
        matricula.setSolicitudMatricula(solicitud);
        matricula.setCodigoMatricula("MAT-" + key());
        matricula.setAnioAcademico(2090);
        matricula.setFechaMatricula(Instant.now());
        matricula.setEstado(EstadoMatricula.ACTIVA);
        em.persist(matricula);
        return matricula;
    }

    private long indicador(DashboardResponse dashboard, String codigo) {
        return dashboard.indicadores().stream()
                .filter(indicador -> indicador.codigo().equals(codigo))
                .findFirst().orElseThrow().valor();
    }

    private JwtAuthenticationToken authentication(String rol, Long personaId) {
        Instant ahora = Instant.now();
        Jwt jwt = Jwt.withTokenValue("token-de-prueba")
                .header("alg", "none").subject("usuario-prueba")
                .issuedAt(ahora).expiresAt(ahora.plusSeconds(600))
                .claim("usuarioId", admin.getId()).claim("personaId", personaId).build();
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_" + rol)));
    }
}
