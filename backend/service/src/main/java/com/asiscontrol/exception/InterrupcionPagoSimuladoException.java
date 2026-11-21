package com.asiscontrol.exception;

import org.springframework.http.HttpStatus;

/** Interrupciones de respuesta de la pasarela simulada, consultables desde la misma orden. */
public class InterrupcionPagoSimuladoException extends RuntimeException {
    private final String codigo;
    private final HttpStatus estadoHttp;

    public InterrupcionPagoSimuladoException(HttpStatus estadoHttp, String codigo, String mensaje) {
        super(mensaje);
        this.estadoHttp = estadoHttp;
        this.codigo = codigo;
    }

    public HttpStatus getEstadoHttp() {
        return estadoHttp;
    }

    public String getCodigo() {
        return codigo;
    }
}
