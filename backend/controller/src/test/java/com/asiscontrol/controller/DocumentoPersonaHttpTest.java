package com.asiscontrol.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.Test;

import java.util.Map;

/** ASISCTRL-62: el servidor valida documento y teléfono igual que el formulario de persona. */
class DocumentoPersonaHttpTest extends ApiIntegrationSupport {

    private Map<String, Object> apoderado(String tipo, String numero, String telefono) {
        return data("persona", data("nombres", "Carmen", "apellidoPaterno", "Ramos",
                "apellidoMaterno", "Quispe", "tipoDocumento", tipo, "numeroDocumento", numero,
                "sexo", "FEMENINO", "fechaNacimiento", "1984-07-21", "telefono", telefono,
                "correo", key() + "@example.test"));
    }

    private void rechaza(Map<String, Object> body, String campo) throws Exception {
        JsonNode error = request("POST", "/api/apoderados", body, 400);
        assertThat(error.toString()).contains(campo);
    }

    @Test
    void elDniExigeOchoDigitos() throws Exception {
        for (String numero : new String[] {"1234567", "123456789", "AB123456", "1234-678"}) {
            rechaza(apoderado("DNI", numero, null), "numeroDocumento");
        }
        String dni = String.valueOf(10_000_000 + (int) (Math.random() * 89_999_999));
        postOk("/api/apoderados", apoderado("DNI", dni, null));
    }

    @Test
    void pasaporteYCarneConservanElPatronGeneral() throws Exception {
        rechaza(apoderado("PASAPORTE", "AB/123", null), "numeroDocumento");
        rechaza(apoderado("CARNET_EXTRANJERIA", "ABC12", null), "numeroDocumento");
        postOk("/api/apoderados", apoderado("PASAPORTE", "P-" + key().substring(0, 8), null));
        postOk("/api/apoderados", apoderado("CARNET_EXTRANJERIA", key().substring(0, 9).toUpperCase(), null));
    }

    @Test
    void elTelefonoSigueElFormatoDelFormulario() throws Exception {
        for (String telefono : new String[] {"123456", "123a456", "1".repeat(21)}) {
            rechaza(apoderado("PASAPORTE", "P" + key().substring(0, 8), telefono), "telefono");
        }
        postOk("/api/apoderados", apoderado("PASAPORTE", "P" + key().substring(0, 8), "+51 (01) 555-1234"));
    }
}
