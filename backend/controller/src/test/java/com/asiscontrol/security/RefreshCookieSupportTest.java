package com.asiscontrol.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import static org.assertj.core.api.Assertions.*;

class RefreshCookieSupportTest {
    @Test void cookieDeProduccionEsHttpOnlySecureYConRutaLimitada() {
        var cookies = new RefreshCookieSupport(true, "Strict", "https://colegio.test");
        var cookie = cookies.crear("secreto");
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Strict");
        assertThat(cookie.getDomain()).isNull();
        assertThat(cookie.getPath()).isEqualTo("/api/auth");
        assertThat(cookies.borrar().getMaxAge().getSeconds()).isZero();
    }

    @Test void cookieDeRenovacionEsDeSesionYSeBorraAlCerrarElNavegador() {
        var cookie = new RefreshCookieSupport(true, "Strict", "https://colegio.test").crear("secreto");
        assertThat(cookie.getMaxAge().isNegative()).isTrue();
        assertThat(cookie.toString()).doesNotContain("Max-Age").doesNotContain("Expires");
    }

    @Test void permiteSoloOrigenExactoOSameOriginYExigeOriginEnEndpointsConCookies() {
        var c = new RefreshCookieSupport(true, "Strict", "https://colegio.test, https://otro.test");
        var r = new MockHttpServletRequest(); r.setScheme("https"); r.setServerName("api.test"); r.setServerPort(443);
        assertThatThrownBy(() -> c.validarOrigen(r, true)).isInstanceOf(AccessDeniedException.class);
        assertThatCode(() -> c.validarOrigen(r, false)).doesNotThrowAnyException();
        r.addHeader("Origin", "https://colegio.test");
        assertThatCode(() -> c.validarOrigen(r, true)).doesNotThrowAnyException();
        r.removeHeader("Origin"); r.addHeader("Origin", "https://api.test");
        assertThatCode(() -> c.validarOrigen(r, true)).doesNotThrowAnyException();
        r.removeHeader("Origin"); r.addHeader("Origin", "https://colegio.test.evil.test");
        assertThatThrownBy(() -> c.validarOrigen(r, true)).isInstanceOf(AccessDeniedException.class);
    }

    @Test void rechazaConfiguracionInseguraDeSameSiteNone() {
        assertThatThrownBy(() -> new RefreshCookieSupport(false, "None", "*"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RefreshCookieSupport(true, "invalido", ""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> new RefreshCookieSupport(true, "None", "https://colegio.test"))
                .doesNotThrowAnyException();
    }
}
