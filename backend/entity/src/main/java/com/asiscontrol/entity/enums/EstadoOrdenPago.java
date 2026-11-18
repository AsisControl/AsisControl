package com.asiscontrol.entity.enums;

public enum EstadoOrdenPago {
    PENDIENTE,
    REQUIERE_AUTENTICACION,
    COMPLETADO,
    RECHAZADO,
    EXPIRADO;

    public boolean esTerminal() {
        return this == COMPLETADO || this == RECHAZADO || this == EXPIRADO;
    }

    public boolean permiteTransicionA(EstadoOrdenPago destino) {
        return switch (this) {
            case PENDIENTE -> destino == REQUIERE_AUTENTICACION || destino == COMPLETADO
                    || destino == RECHAZADO || destino == EXPIRADO;
            case REQUIERE_AUTENTICACION -> destino == COMPLETADO || destino == RECHAZADO
                    || destino == EXPIRADO;
            case COMPLETADO, RECHAZADO, EXPIRADO -> false;
        };
    }
}
