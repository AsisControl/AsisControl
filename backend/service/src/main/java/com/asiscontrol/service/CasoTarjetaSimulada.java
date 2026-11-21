package com.asiscontrol.service;

import com.asiscontrol.entity.enums.EstadoOrdenPago;
import com.asiscontrol.exception.BusinessRuleException;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Las referencias identifican exclusivamente tarjetas ficticias de AsisControl. */
enum CasoTarjetaSimulada {
    APROBADA("TP-001", EstadoOrdenPago.COMPLETADO, null),
    FONDOS_INSUFICIENTES("TP-002", "La tarjeta tiene fondos insuficientes. Usa otra tarjeta o elige otro medio de pago."),
    VENCIDA("TP-003", "La tarjeta está vencida. Usa otra tarjeta."),
    INVALIDA("TP-004", "No pudimos usar esta tarjeta. Prueba con otra o consulta con tu banco."),
    CODIGO_INCORRECTO("TP-005", "El código de seguridad no coincide. Revísalo e inténtalo de nuevo."),
    LIMITE_EXCEDIDO("TP-006", "El importe supera el límite de la tarjeta. Usa otro medio de pago o consulta con tu banco."),
    COMPRAS_EN_LINEA_DESHABILITADAS("TP-007", "La tarjeta no está habilitada para compras por internet. Activa esa opción o usa otra tarjeta."),
    RECHAZO_GENERICO("TP-008", "No se pudo autorizar el pago. Usa otro medio o consulta con tu banco."),
    REQUIERE_AUTENTICACION("TP-009", EstadoOrdenPago.REQUIERE_AUTENTICACION, "Confirma la autenticación para continuar."),
    SERVICIO_NO_DISPONIBLE("TP-010", EstadoOrdenPago.PENDIENTE, "El servicio de pagos no está disponible. Consulta el estado de esta operación."),
    CONFIRMACION_NO_RECIBIDA("TP-011", EstadoOrdenPago.COMPLETADO, "Todavía estamos verificando tu pago. Consulta el estado de esta operación.");

    private static final Map<String, CasoTarjetaSimulada> POR_REFERENCIA = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(caso -> caso.referencia, Function.identity()));

    private final String referencia;
    private final EstadoOrdenPago destino;
    private final String mensaje;

    CasoTarjetaSimulada(String referencia, String mensaje) {
        this(referencia, EstadoOrdenPago.RECHAZADO, mensaje);
    }

    CasoTarjetaSimulada(String referencia, EstadoOrdenPago destino, String mensaje) {
        this.referencia = referencia;
        this.destino = destino;
        this.mensaje = mensaje;
    }

    static CasoTarjetaSimulada buscar(String referencia) {
        CasoTarjetaSimulada caso = POR_REFERENCIA.get(referencia);
        if (caso == null) {
            throw new BusinessRuleException("TARJETA_PRUEBA_INVALIDA",
                    "Revisa el número e ingresa una tarjeta de prueba válida.", "tarjetaPrueba");
        }
        return caso;
    }

    EstadoOrdenPago destino() {
        return destino;
    }

    String mensaje() {
        return mensaje;
    }
}
