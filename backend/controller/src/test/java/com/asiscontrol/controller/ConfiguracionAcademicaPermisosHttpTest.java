package com.asiscontrol.controller;

import com.asiscontrol.entity.RegistroAuditoria;
import com.asiscontrol.entity.enums.EstadoAnioAcademico;
import com.asiscontrol.entity.enums.EstadoPeriodoAcademico;
import com.asiscontrol.entity.planificacion.DiaNoLectivo;
import com.asiscontrol.entity.planificacion.PeriodoAcademico;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ASISCTRL-242: solo el administrador edita períodos y elimina días no lectivos. Los demás roles
 * reciben 403 aunque tengan todos los permisos académicos, y nada cambia.
 */
class ConfiguracionAcademicaPermisosHttpTest extends ApiIntegrationSupport {
    private static final String BASE = "/api/configuracion-academica";

    private PeriodoAcademico periodo;
    private DiaNoLectivo dia;

    @BeforeEach void datos() {
        year.setEstado(EstadoAnioAcademico.BORRADOR);
        periodo = new PeriodoAcademico();
        periodo.setAnioAcademico(year);
        periodo.setNombre("Bimestre I");
        periodo.setOrden(1);
        periodo.setFechaInicio(LocalDate.of(2090, 3, 1));
        periodo.setFechaFin(LocalDate.of(2090, 5, 31));
        periodo.setEstado(EstadoPeriodoAcademico.PLANIFICADO);
        em.persist(periodo);
        dia = new DiaNoLectivo();
        dia.setAnioAcademico(year);
        dia.setFecha(LocalDate.of(2090, 7, 28));
        em.persist(dia);
        em.flush();
    }

    private List<MockHttpServletRequestBuilder> operaciones() throws Exception {
        var cuerpo = data("nombre", "Otro nombre", "fechaInicio", "2090-03-02", "fechaFin", "2090-05-30",
                "version", 0, "motivo", "Sin permiso");
        return List.of(
                put(BASE + "/periodos/" + periodo.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(cuerpo)),
                delete(BASE + "/dias-no-lectivos/" + dia.getId()).param("version", "0"));
    }

    private void nadaCambio() {
        em.clear();
        assertThat(em.find(PeriodoAcademico.class, periodo.getId()).getNombre()).isEqualTo("Bimestre I");
        assertThat(em.find(DiaNoLectivo.class, dia.getId())).isNotNull();
        assertThat(em.createQuery("from RegistroAuditoria r where r.accion in "
                        + "('ACTUALIZAR_PERIODO', 'ELIMINAR_DIA_NO_LECTIVO') and r.registroId in :ids",
                RegistroAuditoria.class)
                .setParameter("ids", List.of(periodo.getId().toString(), dia.getId().toString()))
                .getResultList())
                .noneMatch(registro -> registro.getResultado().name().equals("EXITOSO"));
    }

    @Test void sinSesionSeResponde401() throws Exception {
        for (var operacion : operaciones()) mvc.perform(operacion).andExpect(status().isUnauthorized());
        nadaCambio();
    }

    @ParameterizedTest
    @ValueSource(strings = {"DOCENTE", "ALUMNO", "APODERADO", "SECRETARIA"})
    void losDemasRolesReciben403(String rol) throws Exception {
        for (var operacion : operaciones())
            mvc.perform(operacion.with(user("sin-acceso").authorities(
                            new SimpleGrantedAuthority("ROLE_" + rol),
                            new SimpleGrantedAuthority("ACADEMICO_LEER"),
                            new SimpleGrantedAuthority("ACADEMICO_ESCRIBIR"))))
                    .andExpect(status().isForbidden());
        nadaCambio();
    }

    @Test void elAdministradorEditaYElimina() throws Exception {
        var operaciones = operaciones();
        mvc.perform(operaciones.get(0).with(asAdmin())).andExpect(status().isOk());
        mvc.perform(operaciones.get(1).with(asAdmin())).andExpect(status().isNoContent());
    }
}
