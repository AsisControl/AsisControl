package com.asiscontrol.service;

import com.asiscontrol.dto.PagosFamiliaDto;
import com.asiscontrol.entity.*;
import com.asiscontrol.entity.enums.*;
import com.asiscontrol.exception.BusinessRuleException;
import com.asiscontrol.exception.ConflictException;
import com.asiscontrol.exception.InterrupcionPagoSimuladoException;
import com.asiscontrol.exception.ResourceNotFoundException;
import com.asiscontrol.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
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
    private final SaldoCompromisoService saldos;
    private final boolean habilitado;
    private final boolean escenariosClienteHabilitados;
    private final String frontendUrl;
    private final String institucion;

    public PagosFamiliaService(OrdenPagoRepository ordenes, OrdenPagoDetalleRepository detalles,
            CompromisoPagoRepository compromisos, TransaccionPagoRepository transacciones,
            ComprobanteRepository comprobantes, ConsultaObligacionesService acceso,
            TokenQrPagoService tokens, AuditoriaService auditoria, EntityManager em,
            SaldoCompromisoService saldos,
            @Value("${app.pagos.simulador.enabled:true}") boolean habilitado,
            @Value("${app.pagos.simulador.escenarios-cliente:false}") boolean escenariosClienteHabilitados,
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
        this.saldos = saldos;
        this.habilitado = habilitado;
        this.escenariosClienteHabilitados = escenariosClienteHabilitados;
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

    /** Seguimiento exclusivo de órdenes que todavía pueden continuarse. */
    public Page<PagosFamiliaDto.OrdenResponse> listar(Long idAlumno, Pageable pageable) {
        var apoderado = acceso.apoderadoActual();
        if (idAlumno != null) acceso.verificarAlumno(idAlumno, apoderado);
        return ordenes.buscarPropias(apoderado.getId(), idAlumno, Instant.now(), pageable).map(orden -> {
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

    @Transactional(noRollbackFor = {TransicionPagoInvalida.class, InterrupcionPagoSimuladoException.class})
    public PagosFamiliaDto.OrdenResponse confirmarTarjeta(Long id, PagosFamiliaDto.TarjetaRequest request) {
        verificarHabilitado();
        OrdenPago orden = propia(id);
        if (orden.getMetodo() != MetodoPagoSimulado.TARJETA) {
            throw new BusinessRuleException("METODO_PAGO_INCORRECTO", "La orden no corresponde a un pago con tarjeta.");
        }
        CasoTarjetaSimulada caso = resolverTarjeta(request);
        expirar(orden);
        if (orden.getEstado() == caso.destino() && orden.getEstado() != EstadoOrdenPago.PENDIENTE) {
            return respuesta(orden, false);
        }
        if (orden.getEstado() != EstadoOrdenPago.PENDIENTE) throw new TransicionPagoInvalida(orden.getEstado());
        switch (caso) {
            case APROBADA, CONFIRMACION_NO_RECIBIDA -> {
                aprobar(orden);
                // La aprobación se guarda aunque el cliente simule perder la respuesta.
                if (caso == CasoTarjetaSimulada.CONFIRMACION_NO_RECIBIDA
                        && orden.getEstado() == EstadoOrdenPago.COMPLETADO) {
                    throw new InterrupcionPagoSimuladoException(HttpStatus.GATEWAY_TIMEOUT,
                            "PAGO_CONFIRMACION_NO_RECIBIDA", caso.mensaje());
                }
            }
            case REQUIERE_AUTENTICACION -> cambiarEstado(orden, caso.destino(),
                    caso.mensaje(), "AUTENTICACION_PAGO_REQUERIDA");
            case SERVICIO_NO_DISPONIBLE -> throw new InterrupcionPagoSimuladoException(
                    HttpStatus.SERVICE_UNAVAILABLE, "PAGO_SERVICIO_NO_DISPONIBLE", caso.mensaje());
            default -> rechazar(orden, caso.mensaje());
        }
        return respuesta(orden, false);
    }

    private CasoTarjetaSimulada resolverTarjeta(PagosFamiliaDto.TarjetaRequest request) {
        if (request != null && request.escenario() != null) {
            verificarEscenariosCliente();
            if (request.tarjetaPrueba() != null) {
                throw new BusinessRuleException("TARJETA_PRUEBA_INVALIDA",
                        "Envía solo la referencia de la tarjeta de prueba.", "tarjetaPrueba");
            }
            return switch (request.escenario()) {
                case APROBADO -> CasoTarjetaSimulada.APROBADA;
                case FONDOS_INSUFICIENTES -> CasoTarjetaSimulada.FONDOS_INSUFICIENTES;
                case RECHAZADO -> CasoTarjetaSimulada.RECHAZO_GENERICO;
                case REQUIERE_AUTENTICACION -> CasoTarjetaSimulada.REQUIERE_AUTENTICACION;
            };
        }
        if (request == null || request.tarjetaPrueba() == null || request.tarjetaPrueba().isBlank()) {
            if (escenariosClienteHabilitados) return CasoTarjetaSimulada.APROBADA;
            throw new BusinessRuleException("TARJETA_PRUEBA_REQUERIDA",
                    "Ingresa los datos de una tarjeta de prueba para continuar.", "tarjetaPrueba");
        }
        return CasoTarjetaSimulada.buscar(request.tarjetaPrueba());
    }

    @Transactional(noRollbackFor = TransicionPagoInvalida.class)
    public PagosFamiliaDto.OrdenResponse autenticar(Long id, PagosFamiliaDto.AutenticacionRequest request) {
        verificarHabilitado();
        OrdenPago orden = propia(id);
        if (orden.getMetodo() != MetodoPagoSimulado.TARJETA) {
            throw new BusinessRuleException("AUTENTICACION_NO_REQUERIDA", "Esta orden no tiene una autenticación pendiente.");
        }
        expirar(orden);
        if (orden.getEstado() != EstadoOrdenPago.REQUIERE_AUTENTICACION) {
            throw new TransicionPagoInvalida(orden.getEstado());
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

    @Transactional(noRollbackFor = TransicionPagoInvalida.class)
    public PagosFamiliaDto.OrdenResponse confirmarQr(String token, PagosFamiliaDto.QrConfirmacionRequest request) {
        verificarHabilitado();
        OrdenPago orden = porQr(token);
        boolean aprobar = request.accion() == PagosFamiliaDto.AccionQr.APROBAR;
        if (!aprobar) verificarEscenariosCliente();
        expirar(orden);
        var destino = aprobar ? EstadoOrdenPago.COMPLETADO : EstadoOrdenPago.RECHAZADO;
        if (orden.getEstado() == destino) return respuesta(orden, true);
        if (orden.getEstado() != EstadoOrdenPago.PENDIENTE) throw new TransicionPagoInvalida(orden.getEstado());
        if (aprobar) aprobar(orden);
        else rechazar(orden, "La billetera rechazó el pago por fondos insuficientes.");
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
            // La vigencia también se controla tras la espera por un bloqueo de base de datos.
            expirar(orden);
            exigirTransicion(orden, EstadoOrdenPago.COMPLETADO);
            if (compromiso == null || !acceso.asociado(compromiso.getMatricula().getAlumno().getId(), orden.getApoderado())
                    || compromiso.getEstadoAdministrativo() != EstadoAdministrativoCompromiso.ACTIVO
                    || !"PEN".equals(compromiso.getMoneda())) {
                rechazar(orden, "Uno de los cargos ya no está disponible. Actualiza y revisa tu selección.");
                return;
            }
            if (obligacionPagada(compromiso)) {
                rechazar(orden, "Uno de los cargos ya está pagado. Consulta su constancia en Historial.");
                return;
            }
            BigDecimal saldoVigente = saldo(compromiso);
            if (saldoVigente.compareTo(fila.getMonto()) != 0 || saldoVigente.signum() <= 0) {
                rechazar(orden, "El importe disponible para pagar cambió. Actualiza y revisa tu selección.");
                return;
            }
            bloqueados.add(compromiso);
        }
        // No se modifica ninguna obligación hasta haber validado todo el lote.
        expirar(orden);
        exigirTransicion(orden, EstadoOrdenPago.COMPLETADO);
        Instant ahora = Instant.now();
        for (int indice = 0; indice < filas.size(); indice++) {
            var fila = filas.get(indice);
            var compromiso = bloqueados.get(indice);
            var transaccion = fila.getTransaccion();
            transaccion.setEstado(EstadoTransaccionPago.APROBADA);
            transaccion.setFechaTransaccion(ahora);
            // Al aprobarse la transacción, el saldo y el estado se recalculan desde los pagos aprobados.
            saldos.recalcular(compromiso);
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
        orden.setPagadaEn(ahora);
        cambiarEstado(orden, EstadoOrdenPago.COMPLETADO,
                "Pago aprobado. Tu constancia de pago está disponible.", "APROBAR_PAGO_SIMULADO");
    }

    private void rechazar(OrdenPago orden, String mensaje) {
        expirar(orden);
        exigirTransicion(orden, EstadoOrdenPago.RECHAZADO);
        actualizarTransacciones(orden, EstadoTransaccionPago.RECHAZADA, Instant.now());
        cambiarEstado(orden, EstadoOrdenPago.RECHAZADO, mensaje, "RECHAZAR_PAGO_SIMULADO");
    }

    private void expirar(OrdenPago orden) {
        if (!orden.getEstado().esTerminal() && !orden.getExpiraEn().isAfter(Instant.now())) {
            actualizarTransacciones(orden, EstadoTransaccionPago.EXPIRADA, orden.getExpiraEn());
            cambiarEstado(orden, EstadoOrdenPago.EXPIRADO,
                    "El tiempo para este pago terminó. Puedes iniciar uno nuevo.", "EXPIRAR_PAGO_SIMULADO");
        }
    }

    private void actualizarTransacciones(OrdenPago orden, EstadoTransaccionPago estado, Instant fecha) {
        for (var detalle : items(orden)) {
            detalle.getTransaccion().setEstado(estado);
            detalle.getTransaccion().setFechaTransaccion(fecha);
        }
    }

    private void cambiarEstado(OrdenPago orden, EstadoOrdenPago destino, String mensaje, String accion) {
        exigirTransicion(orden, destino);
        var antes = orden.getEstado();
        orden.setEstado(destino);
        orden.setMensaje(mensaje);
        auditar(accion, orden, antes);
    }

    private void exigirTransicion(OrdenPago orden, EstadoOrdenPago destino) {
        if (!orden.getEstado().permiteTransicionA(destino)) throw new TransicionPagoInvalida(orden.getEstado());
    }

    private void validarPagable(CompromisoPago compromiso) {
        if (obligacionPagada(compromiso)) {
            throw new BusinessRuleException("OBLIGACION_YA_PAGADA",
                    "Uno de los cargos ya está pagado. Consulta su constancia en Historial.");
        }
        if (compromiso.getEstadoAdministrativo() != EstadoAdministrativoCompromiso.ACTIVO
                || saldo(compromiso).signum() <= 0 || !"PEN".equals(compromiso.getMoneda())) {
            throw new BusinessRuleException("OBLIGACION_NO_PAGABLE", "Selecciona obligaciones en soles pendientes de pago.");
        }
    }

    private boolean obligacionPagada(CompromisoPago compromiso) {
        return EstadoFinancieroCompromiso.visible(compromiso, EstadoFinancieroCompromiso.hoy())
                == EstadoCompromisoPago.PAGADO;
    }

    private BigDecimal saldo(CompromisoPago compromiso) {
        return EstadoFinancieroCompromiso.saldo(compromiso.getMontoTotal(), compromiso.getMontoPagado());
    }

    private PagosFamiliaDto.OrdenResponse respuesta(OrdenPago orden, boolean publico) {
        boolean capacidadVencida = publico && !orden.getExpiraEn().isAfter(Instant.now());
        List<PagosFamiliaDto.ItemResponse> lineas = capacidadVencida ? List.of() : items(orden).stream()
                .map(fila -> presentarItem(fila, publico)).toList();
        boolean qrDisponible = !publico && orden.getMetodo() == MetodoPagoSimulado.QR && !orden.getEstado().esTerminal();
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

    private PagosFamiliaDto.ItemResponse presentarItem(OrdenPagoDetalle fila, boolean publico) {
        var compromiso = fila.getCompromiso();
        var matricula = compromiso.getMatricula();
        var concepto = publico ? null : compromiso.getConceptoCobro();
        var tipo = concepto == null ? null : concepto.isEsPension()
                ? PagosFamiliaDto.TipoObligacion.PENSION : PagosFamiliaDto.TipoObligacion.OTRO;
        return new PagosFamiliaDto.ItemResponse(compromiso.getId(), fila.getCodigoCompromiso(),
                publico ? matricula.getAlumno().getPersona().getNombres() : fila.getAlumno(),
                fila.getConcepto(), fila.getNumeroCuota(), fila.getMonto(), publico ? null : compromiso.getMes(),
                publico ? null : matricula.getAnioAcademico(), publico ? null : compromiso.getReferenciaCobro(), tipo);
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

    private void verificarEscenariosCliente() {
        if (!escenariosClienteHabilitados) {
            throw new BusinessRuleException("ESCENARIO_PAGO_NO_DISPONIBLE", "Este resultado de prueba no está disponible.");
        }
    }

    /** Conserva una expiración registrada al rechazar una confirmación tardía. */
    private static final class TransicionPagoInvalida extends ConflictException {
        private TransicionPagoInvalida(EstadoOrdenPago estado) {
            super("TRANSICION_PAGO_INVALIDA", switch (estado) {
                case PENDIENTE -> "Esta operación no está disponible mientras el pago espera confirmación.";
                case REQUIERE_AUTENTICACION -> "La orden espera una confirmación adicional.";
                case COMPLETADO -> "El pago ya fue aprobado y tiene una constancia disponible.";
                case RECHAZADO -> "El pago fue rechazado. Puedes iniciar otro intento.";
                case EXPIRADO -> "El tiempo para este pago terminó. Puedes iniciar uno nuevo.";
            });
        }
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
