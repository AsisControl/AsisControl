package com.asiscontrol.service;

import com.asiscontrol.dto.PagosFamiliaDto;
import com.asiscontrol.entity.*;
import com.asiscontrol.entity.enums.*;
import com.asiscontrol.exception.BusinessRuleException;
import com.asiscontrol.exception.ConflictException;
import com.asiscontrol.exception.ResourceNotFoundException;
import com.asiscontrol.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/** Checkout simulado: el importe, la autorización y la liquidación se resuelven en servidor. */
@Service
@Transactional
public class PagosFamiliaService {
    private static final BigDecimal CERO = new BigDecimal("0.00");
    private static final BigDecimal MONTO_MAXIMO = new BigDecimal("9999999999.99");
    private final OrdenPagoRepository ordenes;
    private final OrdenPagoDetalleRepository detalles;
    private final CompromisoPagoRepository compromisos;
    private final TransaccionPagoRepository transacciones;
    private final ComprobanteRepository comprobantes;
    private final ConsultaObligacionesService acceso;
    private final TokenQrPagoService tokens;
    private final AuditoriaService auditoria;
    private final EntityManager em;
    private final boolean habilitado;
    private final String frontendUrl;
    private final String institucion;

    public PagosFamiliaService(OrdenPagoRepository ordenes, OrdenPagoDetalleRepository detalles,
            CompromisoPagoRepository compromisos, TransaccionPagoRepository transacciones,
            ComprobanteRepository comprobantes, ConsultaObligacionesService acceso,
            TokenQrPagoService tokens, AuditoriaService auditoria, EntityManager em,
            @Value("${app.pagos.simulador.enabled:true}") boolean habilitado,
            @Value("${app.frontend-url:http://localhost:3000}") String frontendUrl,
            @Value("${app.pagos.institucion:Colegio PUCP}") String institucion) {
        this.ordenes = ordenes;
        this.detalles = detalles;
        this.compromisos = compromisos;
        this.transacciones = transacciones;
        this.comprobantes = comprobantes;
        this.acceso = acceso;
        this.tokens = tokens;
        this.auditoria = auditoria;
        this.em = em;
        this.habilitado = habilitado;
        URI url = URI.create(frontendUrl.trim());
        if (!("http".equalsIgnoreCase(url.getScheme()) || "https".equalsIgnoreCase(url.getScheme()))
                || url.getHost() == null || url.getUserInfo() != null || url.getQuery() != null || url.getFragment() != null) {
            throw new IllegalStateException("APP_FRONTEND_URL debe ser una dirección HTTP o HTTPS sin credenciales.");
        }
        this.frontendUrl = frontendUrl.trim().replaceAll("/+$", "");
        this.institucion = institucion;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PagosFamiliaDto.OrdenResponse iniciar(PagosFamiliaDto.InicioRequest request, String clave) {
        verificarHabilitado();
        String claveCanonica = claveIdempotencia(clave);
        var apoderado = acceso.apoderadoActual();
        // Serializa reintentos de un mismo apoderado, también si aún no existe la fila de orden.
        em.lock(apoderado, LockModeType.PESSIMISTIC_WRITE);
        List<Long> ids = request.obligacionesIds().stream().distinct().sorted().toList();
        if (ids.isEmpty() || ids.size() > 20 || ids.size() != request.obligacionesIds().size()) {
            throw new BusinessRuleException("SELECCION_INVALIDA", "Selecciona entre una y veinte obligaciones distintas.");
        }
        String fingerprint = seleccionHash(ids, request.metodo());
        var anterior = ordenes.findByApoderadoIdAndClaveIdempotencia(apoderado.getId(), claveCanonica);
        if (anterior.isPresent()) {
            var orden = bloquear(anterior.get().getId());
            verificarPropiedad(orden, apoderado);
            if (!orden.getSeleccionHash().equals(fingerprint)) {
                throw new ConflictException("IDEMPOTENCIA_REUTILIZADA", "Esta solicitud ya corresponde a otra selección de pago.");
            }
            expirar(orden);
            return respuesta(orden, false);
        }
        // Se comprueba todo el lote antes de crear registros o revelar información de un ID.
        ids.forEach(id -> acceso.verificarCompromiso(id, apoderado));
        List<CompromisoPago> seleccion = new ArrayList<>();
        BigDecimal total = CERO;
        for (Long id : ids) {
            var compromiso = bloquearCompromiso(id);
            if (compromiso == null) throw sinAcceso();
            validarPagable(compromiso);
            seleccion.add(compromiso);
            total = total.add(saldo(compromiso));
        }
        if (total.compareTo(MONTO_MAXIMO) > 0) {
            throw new BusinessRuleException("TOTAL_PAGO_FUERA_RANGO", "El total excede el importe permitido por operación.");
        }
        Instant ahora = Instant.now();
        // Los compromisos bloqueados serializan cualquier selección superpuesta, incluso de
        // distintos apoderados. READ_COMMITTED ve la orden que el ganador acaba de confirmar
        // en base, sin tomar un bloqueo de orden en sentido inverso al flujo de aprobación.
        if (ordenes.existePagoVigente(ids, ahora)) {
            throw new ConflictException("PAGO_EN_CURSO",
                    "Una o más cuotas tienen un pago en curso. Revisa Seguimiento de pagos o espera a que termine su vigencia antes de intentarlo nuevamente.");
        }
        OrdenPago orden = new OrdenPago();
        orden.setCodigo("OP-" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT));
        orden.setQrReferencia(orden.getCodigo());
        orden.setApoderado(apoderado);
        orden.setClaveIdempotencia(claveCanonica);
        orden.setSeleccionHash(fingerprint);
        orden.setMetodo(request.metodo());
        orden.setEstado(EstadoOrdenPago.PENDIENTE);
        orden.setMontoBase(total.setScale(2));
        // No se inventa una tasa: la institución todavía no ha aprobado una política de mora.
        orden.setMora(CERO);
        orden.setMontoTotal(total.setScale(2));
        orden.setMoneda("PEN");
        orden.setCreadaEn(ahora);
        orden.setExpiraEn(ahora.plus(5, ChronoUnit.MINUTES));
        orden.setMensaje("Pago pendiente de confirmación.");
        if (request.metodo() == MetodoPagoSimulado.TARJETA) {
            orden.setPaymentIntentId("pi_sim_" + UUID.randomUUID().toString().replace("-", ""));
        }
        ordenes.saveAndFlush(orden);
        orden.setCodigo(codigoOrden(orden.getId()));
        for (CompromisoPago compromiso : seleccion) {
            TransaccionPago transaccion = new TransaccionPago();
            transaccion.setCompromisoPago(compromiso);
            transaccion.setApoderado(apoderado);
            transaccion.setNumeroOperacion(orden.getCodigo() + "-" + compromiso.getId());
            transaccion.setMonto(saldo(compromiso));
            transaccion.setMedioPago(request.metodo() == MetodoPagoSimulado.QR
                    ? MedioPago.QR_SIMULADO : MedioPago.TARJETA_SIMULADA);
            transaccion.setEstado(EstadoTransaccionPago.PENDIENTE);
            transaccion.setFechaTransaccion(ahora);
            transaccion.setObservacion("Pago simulado de la orden " + orden.getCodigo());
            transacciones.save(transaccion);
            OrdenPagoDetalle detalle = new OrdenPagoDetalle();
            detalle.setOrden(orden);
            detalle.setCompromiso(compromiso);
            detalle.setTransaccion(transaccion);
            detalle.setMonto(saldo(compromiso));
            detalle.setCodigoCompromiso(compromiso.getCodigoCompromiso());
            detalle.setAlumno(compromiso.getMatricula().getAlumno().getPersona().nombreCompleto());
            detalle.setConcepto(compromiso.getConcepto());
            detalle.setNumeroCuota(compromiso.getNumeroCuota());
            detalles.save(detalle);
        }
        em.flush();
        auditar("INICIAR_PAGO_SIMULADO", orden, null);
        return respuesta(orden, false);
    }

    public Page<PagosFamiliaDto.OrdenResponse> listar(Pageable pageable) {
        var apoderado = acceso.apoderadoActual();
        return ordenes.buscarPropias(apoderado.getId(), pageable).map(orden -> {
            var bloqueada = bloquear(orden.getId());
            expirar(bloqueada);
            return respuesta(bloqueada, false);
        });
    }

    public PagosFamiliaDto.OrdenResponse obtener(Long id) {
        var orden = propia(id);
        expirar(orden);
        return respuesta(orden, false);
    }

    public PagosFamiliaDto.OrdenResponse confirmarTarjeta(Long id, PagosFamiliaDto.TarjetaRequest request) {
        verificarHabilitado();
        OrdenPago orden = propia(id);
        if (orden.getMetodo() != MetodoPagoSimulado.TARJETA) {
            throw new BusinessRuleException("METODO_PAGO_INCORRECTO", "La orden no corresponde a un pago con tarjeta.");
        }
        expirar(orden);
        if (terminal(orden) || orden.getEstado() == EstadoOrdenPago.REQUIERE_AUTENTICACION) {
            return respuesta(orden, false);
        }
        switch (request.escenario()) {
            case APROBADO -> aprobar(orden);
            case FONDOS_INSUFICIENTES -> rechazar(orden, "Pago rechazado por fondos insuficientes.");
            case RECHAZADO -> rechazar(orden, "La tarjeta fue rechazada. Puedes iniciar otro intento.");
            case REQUIERE_AUTENTICACION -> {
                var antes = orden.getEstado();
                orden.setEstado(EstadoOrdenPago.REQUIERE_AUTENTICACION);
                orden.setMensaje("Confirma la autenticación para continuar.");
                auditar("AUTENTICACION_PAGO_REQUERIDA", orden, antes);
            }
        }
        return respuesta(orden, false);
    }

    public PagosFamiliaDto.OrdenResponse autenticar(Long id, PagosFamiliaDto.AutenticacionRequest request) {
        verificarHabilitado();
        OrdenPago orden = propia(id);
        expirar(orden);
        if (terminal(orden)) return respuesta(orden, false);
        if (orden.getMetodo() != MetodoPagoSimulado.TARJETA || orden.getEstado() != EstadoOrdenPago.REQUIERE_AUTENTICACION) {
            throw new BusinessRuleException("AUTENTICACION_NO_REQUERIDA", "Esta orden no tiene una autenticación pendiente.");
        }
        if (Boolean.TRUE.equals(request.aprobar())) aprobar(orden);
        else rechazar(orden, "La autenticación fue cancelada.");
        return respuesta(orden, false);
    }

    public PagosFamiliaDto.OrdenResponse qr(String token) {
        verificarHabilitado();
        OrdenPago orden = porQr(token);
        expirar(orden);
        return respuesta(orden, true);
    }

    public PagosFamiliaDto.OrdenResponse confirmarQr(String token, PagosFamiliaDto.QrConfirmacionRequest request) {
        verificarHabilitado();
        OrdenPago orden = porQr(token);
        expirar(orden);
        if (!terminal(orden)) {
            if (request.accion() == PagosFamiliaDto.AccionQr.APROBAR) aprobar(orden);
            else rechazar(orden, "La billetera rechazó el pago por fondos insuficientes.");
        }
        return respuesta(orden, true);
    }

    public PagosFamiliaDto.ReciboResponse recibo(Long id) {
        OrdenPago orden = propia(id);
        expirar(orden);
        if (orden.getEstado() != EstadoOrdenPago.COMPLETADO) {
            throw new BusinessRuleException("PAGO_SIN_COMPROBANTE", "El comprobante estará disponible cuando el pago sea aprobado.");
        }
        var emitidos = items(orden).stream().map(item -> comprobantes
                        .findByTransaccionPagoIdAndActivoTrue(item.getTransaccion().getId())
                        .orElseThrow(() -> new IllegalStateException("Falta un comprobante interno de una orden completada.")))
                .map(c -> new PagosFamiliaDto.ComprobanteResponse(c.getId(), c.getTransaccionPago().getId(),
                        c.getTipoComprobante(), c.getSerie(), c.getNumero(), c.getFechaEmision(),
                        c.getMontoTotal(), null, c.getFechaCreacion())).toList();
        return new PagosFamiliaDto.ReciboResponse(respuesta(orden, false), emitidos);
    }

    public String comprobanteHtml(Long id) {
        var recibo = recibo(id);
        var orden = recibo.orden();
        return ConstanciaPagoHtml.crear(orden.institucion(), orden.codigo(), orden.apoderado(),
                orden.pagadaEn(), orden.metodo() == MetodoPagoSimulado.QR ? "Código QR" : "Tarjeta", orden.moneda(),
                orden.items(), orden.montoTotal(), recibo.comprobantes().stream()
                        .map(c -> ComprobantesFamiliaService.codigo(c.id())).toList());
    }

    private OrdenPago propia(Long id) {
        Apoderado apoderado = acceso.apoderadoActual();
        // No tomar bloqueos de órdenes ajenas ni distinguir su existencia.
        var referencia = ordenes.findById(id).orElseThrow(this::sinAcceso);
        verificarPropiedad(referencia, apoderado);
        var orden = bloquear(id);
        verificarPropiedad(orden, apoderado);
        return orden;
    }

    private void verificarPropiedad(OrdenPago orden, Apoderado apoderado) {
        if (!orden.getApoderado().getId().equals(apoderado.getId()) || !orden.isActivo()) throw sinAcceso();
        for (var item : items(orden)) {
            acceso.verificarAlumno(item.getCompromiso().getMatricula().getAlumno().getId(), apoderado);
        }
    }

    private OrdenPago porQr(String token) {
        String codigo = tokens.validar(token);
        var referencia = ordenes.findByQrReferenciaOrCodigo(codigo, codigo).orElseThrow(this::qrNoEncontrado);
        if (referencia.getMetodo() != MetodoPagoSimulado.QR || !referencia.isActivo()) throw qrNoEncontrado();
        var orden = bloquear(referencia.getId());
        for (var item : items(orden)) {
            if (!acceso.asociado(item.getCompromiso().getMatricula().getAlumno().getId(), orden.getApoderado())) {
                throw qrNoEncontrado();
            }
        }
        return orden;
    }

    private OrdenPago bloquear(Long id) {
        var orden = ordenes.findById(id).orElseThrow(this::sinAcceso);
        // Refresh descarta la versión gestionada y obtiene la vigente bajo el mismo bloqueo.
        // Un JPQL @Lock previo compararía la versión antigua antes de poder recargarla.
        em.refresh(orden, LockModeType.PESSIMISTIC_WRITE);
        if (!orden.isActivo()) throw sinAcceso();
        return orden;
    }

    private CompromisoPago bloquearCompromiso(Long id) {
        var compromiso = compromisos.findByIdAndActivoTrue(id).orElse(null);
        if (compromiso != null) em.refresh(compromiso, LockModeType.PESSIMISTIC_WRITE);
        return compromiso != null && compromiso.isActivo() ? compromiso : null;
    }

    private List<OrdenPagoDetalle> items(OrdenPago orden) {
        return detalles.findByOrdenIdOrderByCompromisoIdAsc(orden.getId());
    }

    private void aprobar(OrdenPago orden) {
        var filas = items(orden);
        List<CompromisoPago> bloqueados = new ArrayList<>();
        // Orden estable para que selecciones superpuestas no entren en un interbloqueo.
        for (var fila : filas) {
            var compromiso = bloquearCompromiso(fila.getCompromiso().getId());
            if (compromiso == null || !acceso.asociado(compromiso.getMatricula().getAlumno().getId(), orden.getApoderado())
                    || compromiso.getEstadoAdministrativo() != EstadoAdministrativoCompromiso.ACTIVO
                    || !"PEN".equals(compromiso.getMoneda()) || saldo(compromiso).compareTo(fila.getMonto()) != 0
                    || saldo(compromiso).signum() <= 0) {
                rechazar(orden, "Las obligaciones cambiaron. Revisa los importes pendientes e inicia un nuevo pago.");
                return;
            }
            bloqueados.add(compromiso);
        }
        // No se modifica ninguna obligación hasta haber validado todo el lote.
        Instant ahora = Instant.now();
        for (int indice = 0; indice < filas.size(); indice++) {
            var fila = filas.get(indice);
            var compromiso = bloqueados.get(indice);
            compromiso.setMontoPagado(compromiso.getMontoPagado().add(fila.getMonto()).setScale(2));
            compromiso.setEstado(EstadoCompromisoPago.PAGADO);
            var transaccion = fila.getTransaccion();
            transaccion.setEstado(EstadoTransaccionPago.APROBADA);
            transaccion.setFechaTransaccion(ahora);
            Comprobante comprobante = new Comprobante();
            comprobante.setTransaccionPago(transaccion);
            comprobante.setTipoComprobante(TipoComprobante.CONSTANCIA_PAGO_DIGITAL);
            comprobante.setSerie("REC");
            // Reserva un valor transitorio único hasta obtener el ID definitivo de la constancia.
            comprobante.setNumero("N-" + UUID.randomUUID().toString().replace("-", "").substring(0, 24));
            comprobante.setFechaEmision(ahora);
            comprobante.setMontoTotal(fila.getMonto());
            comprobantes.saveAndFlush(comprobante);
            comprobante.setNumero(String.format(Locale.ROOT, "%06d", comprobante.getId()));
        }
        var antes = orden.getEstado();
        orden.setEstado(EstadoOrdenPago.COMPLETADO);
        orden.setPagadaEn(ahora);
        orden.setMensaje("Pago aprobado. Tu constancia de pago está disponible.");
        em.flush();
        auditar("APROBAR_PAGO_SIMULADO", orden, antes);
    }

    private void rechazar(OrdenPago orden, String mensaje) {
        var antes = orden.getEstado();
        orden.setEstado(EstadoOrdenPago.RECHAZADO);
        orden.setMensaje(mensaje);
        items(orden).forEach(item -> item.getTransaccion().setEstado(EstadoTransaccionPago.RECHAZADA));
        auditar("RECHAZAR_PAGO_SIMULADO", orden, antes);
    }

    private void expirar(OrdenPago orden) {
        if (!terminal(orden) && !orden.getExpiraEn().isAfter(Instant.now())) {
            var antes = orden.getEstado();
            orden.setEstado(EstadoOrdenPago.EXPIRADO);
            orden.setMensaje("El tiempo para este pago terminó. Puedes iniciar uno nuevo.");
            items(orden).forEach(item -> item.getTransaccion().setEstado(EstadoTransaccionPago.EXPIRADA));
            auditar("EXPIRAR_PAGO_SIMULADO", orden, antes);
        }
    }

    private boolean terminal(OrdenPago orden) {
        return orden.getEstado() == EstadoOrdenPago.COMPLETADO || orden.getEstado() == EstadoOrdenPago.RECHAZADO
                || orden.getEstado() == EstadoOrdenPago.EXPIRADO;
    }

    private void validarPagable(CompromisoPago compromiso) {
        if (compromiso.getEstadoAdministrativo() != EstadoAdministrativoCompromiso.ACTIVO
                || saldo(compromiso).signum() <= 0 || !"PEN".equals(compromiso.getMoneda())) {
            throw new BusinessRuleException("OBLIGACION_NO_PAGABLE", "Selecciona obligaciones en soles pendientes de pago.");
        }
    }

    private BigDecimal saldo(CompromisoPago compromiso) {
        return compromiso.getMontoTotal().subtract(compromiso.getMontoPagado()).max(CERO).setScale(2);
    }

    private PagosFamiliaDto.OrdenResponse respuesta(OrdenPago orden, boolean publico) {
        boolean capacidadVencida = publico && !orden.getExpiraEn().isAfter(Instant.now());
        List<PagosFamiliaDto.ItemResponse> lineas = capacidadVencida ? List.of() : items(orden).stream().map(fila -> new PagosFamiliaDto.ItemResponse(
                fila.getCompromiso().getId(), fila.getCodigoCompromiso(),
                publico ? fila.getCompromiso().getMatricula().getAlumno().getPersona().getNombres() : fila.getAlumno(),
                fila.getConcepto(), fila.getNumeroCuota(), fila.getMonto())).toList();
        boolean qrDisponible = !publico && orden.getMetodo() == MetodoPagoSimulado.QR && !terminal(orden);
        String qrReferencia = orden.getQrReferencia() == null ? orden.getCodigo() : orden.getQrReferencia();
        String qrUrl = qrDisponible ? frontendUrl + "/pagar-qr/" + tokens.generar(qrReferencia) : null;
        return new PagosFamiliaDto.OrdenResponse(orden.getId(), codigoOrden(orden.getId()), orden.getMetodo(), orden.getEstado(),
                orden.getMoneda(), capacidadVencida ? CERO : orden.getMontoBase(),
                capacidadVencida ? CERO : orden.getMora(), capacidadVencida ? CERO : orden.getMontoTotal(), orden.getCreadaEn(),
                orden.getExpiraEn(), orden.getPagadaEn(), institucion,
                capacidadVencida ? "" : publico ? orden.getApoderado().getPersona().getNombres() : orden.getApoderado().getPersona().nombreCompleto(),
                lineas, qrUrl, publico ? null : orden.getPaymentIntentId(),
                publico || orden.getPaymentIntentId() == null ? null : tokens.secretoTarjeta(orden.getPaymentIntentId()),
                mensajePresentacion(orden), true);
    }

    private String mensajePresentacion(OrdenPago orden) {
        return switch (orden.getEstado()) {
            case PENDIENTE -> "Pago pendiente de confirmación.";
            case REQUIERE_AUTENTICACION -> "Confirma la autenticación para continuar.";
            case COMPLETADO -> "Pago aprobado. Tu constancia de pago está disponible.";
            case EXPIRADO -> "El tiempo para este pago terminó. Puedes iniciar uno nuevo.";
            case RECHAZADO -> Objects.toString(orden.getMensaje(), "El pago fue rechazado.")
                    .replace(" de prueba", "").replace(" en el simulador", "");
        };
    }

    static String codigoOrden(Long id) {
        return String.format(Locale.ROOT, "OP-%06d", id);
    }

    private void auditar(String accion, OrdenPago orden, EstadoOrdenPago anterior) {
        // Nunca incluye QR, clientSecret, claves de idempotencia, tarjetas ni datos de contacto.
        auditoria.registrar(accion, ModuloAuditoria.FINANZAS, "OrdenPago", orden.getId().toString(),
                ResultadoAuditoria.EXITOSO, "Operación de prueba; sin dinero real",
                anterior == null ? null : anterior.name(),
                "codigo=" + orden.getCodigo() + ",estado=" + orden.getEstado() + ",metodo=" + orden.getMetodo()
                        + ",monto=" + orden.getMontoTotal() + ",moneda=PEN");
        // La transición y su auditoría quedan sincronizadas incluso si un llamador mantiene
        // una transacción externa y vuelve a consultar mediante refresh antes del commit.
        em.flush();
    }

    private void verificarHabilitado() {
        if (!habilitado) throw new BusinessRuleException("SIMULADOR_NO_DISPONIBLE", "El servicio de pagos no está disponible en este momento.");
    }

    private String claveIdempotencia(String clave) {
        if (clave == null || !clave.matches("[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}")) {
            throw new BusinessRuleException("CLAVE_PAGO_INVALIDA", "No se pudo identificar la solicitud de pago. Vuelve a intentarlo.");
        }
        return UUID.fromString(clave).toString();
    }

    private String seleccionHash(List<Long> ids, MetodoPagoSimulado metodo) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((metodo.name() + ":" + ids).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 no está disponible.", error);
        }
    }

    private AccessDeniedException sinAcceso() {
        return new AccessDeniedException("No tienes acceso al pago solicitado.");
    }

    private ResourceNotFoundException qrNoEncontrado() {
        return new ResourceNotFoundException("QR_NO_DISPONIBLE", "Este enlace de pago no está disponible.");
    }

}
