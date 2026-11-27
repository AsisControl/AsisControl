// criterios US-02
package com.asiscontrol.controller;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** US-02: un acceso no se elimina por las funciones ordinarias y el administrador no se desactiva a sí mismo. */
class AccesosCriteriosUs02HttpTest extends ApiIntegrationSupport {

    @Test void unAccesoNoSeEliminaSoloCambiaDeEstado() throws Exception {
        assertThat(request("DELETE", "/api/usuarios/" + admin.getId(), null, 422).path("code").asText())
                .isEqualTo("HISTORIAL_PROTEGIDO");
        assertThat(getOk("/api/usuarios/" + admin.getId()).path("id").asLong()).isEqualTo(admin.getId());
    }

    @Test void elAdministradorNoDesactivaSuPropioAcceso() throws Exception {
        var cuenta = getOk("/api/usuarios/" + admin.getId());
        assertThat(request("PUT", "/api/usuarios/" + admin.getId(), data("correo", cuenta.path("correo").asText(),
                "estado", "DESACTIVADO", "motivo", "Prueba de autodesactivación"), 409).path("code").asText())
                .isEqualTo("AUTO_DESACTIVACION");
        assertThat(getOk("/api/usuarios/" + admin.getId()).path("estado").asText()).isEqualTo("ACTIVO");
    }

    @Test void laActualizacionDeUnAccesoSeAuditaSinRegistrarLaContrasena() throws Exception {
        String correo = "admin." + key() + "@asiscontrol.edu.pe";
        putOk("/api/usuarios/" + admin.getId(), data("correo", correo, "estado", "ACTIVO"));
        var registros = getOk("/api/auditoria/registros?modulo=SEGURIDAD&size=50").path("content");
        var evento = java.util.stream.StreamSupport.stream(registros.spliterator(), false)
                .filter(r -> r.path("accion").asText().equals("ACTUALIZAR_USUARIO")
                        && r.path("registroId").asText().equals(String.valueOf(admin.getId())))
                .findFirst().orElseThrow();
        assertThat(evento.toString()).doesNotContain(admin.getContrasenaHash());
    }
}
