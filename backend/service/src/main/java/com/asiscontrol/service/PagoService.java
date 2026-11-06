package com.asiscontrol.service;

import com.asiscontrol.dto.PagoDto;
import com.asiscontrol.entity.Administrador;
import com.asiscontrol.entity.Apoderado;
import com.asiscontrol.entity.ArchivoAdjunto;
import com.asiscontrol.entity.Comprobante;
import com.asiscontrol.entity.CompromisoPago;
import com.asiscontrol.entity.TransaccionPago;
import com.asiscontrol.entity.enums.EstadoCompromisoPago;
import com.asiscontrol.entity.enums.EstadoTransaccionPago;
import com.asiscontrol.exception.BusinessRuleException;
import com.asiscontrol.exception.ConflictException;
import com.asiscontrol.exception.ResourceNotFoundException;

import com.asiscontrol.entity.enums.ResultadoAuditoria;
import com.asiscontrol.repository.AdministradorRepository;
import com.asiscontrol.repository.ApoderadoRepository;
import com.asiscontrol.repository.ArchivoAdjuntoRepository;
import com.asiscontrol.repository.ComprobanteRepository;
import com.asiscontrol.repository.CompromisoPagoRepository;
import com.asiscontrol.repository.TransaccionPagoRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class PagoService {

    private static final int ESCALA_DINERO = 2;
    private static final MathContext CALCULO_FINANCIERO = new MathContext(20, RoundingMode.HALF_UP);

    private final CompromisoPagoRepository compromisoPagoRepository;
    private final TransaccionPagoRepository transaccionPagoRepository;
    private final ComprobanteRepository comprobanteRepository;
    private final ApoderadoRepository apoderadoRepository;
    private final AdministradorRepository administradorRepository;
    private final ArchivoAdjuntoRepository archivoAdjuntoRepository;
    private final AuditoriaService auditoria;

    public PagoService(
            CompromisoPagoRepository compromisoPagoRepository,
            TransaccionPagoRepository transaccionPagoRepository,
            ComprobanteRepository comprobanteRepository,
            ApoderadoRepository apoderadoRepository,
            AdministradorRepository administradorRepository,
            ArchivoAdjuntoRepository archivoAdjuntoRepository,
            AuditoriaService auditoria
    ) {
        this.compromisoPagoRepository = compromisoPagoRepository;
        this.transaccionPagoRepository = transaccionPagoRepository;
        this.comprobanteRepository = comprobanteRepository;
        this.apoderadoRepository = apoderadoRepository;
        this.administradorRepository = administradorRepository;
        this.archivoAdjuntoRepository = archivoAdjuntoRepository;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    public Page<PagoDto.TransaccionResponse> listarTransacciones(
            Long idCompromisoPago,
            Long personaApoderadoId,
            Pageable pageable
    ) {
        return transaccionPagoRepository.buscarVisibles(
                        idCompromisoPago,
                        personaApoderadoId,
                        pageable)
                .map(this::toTransaccionResponse);
    }

    @Transactional(readOnly = true)
    public PagoDto.TransaccionResponse obtenerTransaccion(Long id) {
        return toTransaccionResponse(buscarTransaccionActiva(id));
    }

    @Transactional
    public PagoDto.TransaccionResponse registrarTransaccion(PagoDto.TransaccionRequest request) {
        String numeroOperacion = request.numeroOperacion().trim().toUpperCase();
        var existente = transaccionPagoRepository
                .findByNumeroOperacionIgnoreCaseAndActivoTrue(numeroOperacion);
        if (existente.isPresent()) {
            TransaccionPago transaccion = existente.get();
            if (esMismaOperacion(transaccion, request)) {
                return toTransaccionResponse(transaccion);
            }
            throw new ConflictException(
                    "NUMERO_OPERACION_REUTILIZADO",
                    "El numero de operacion ya fue usado con datos diferentes"
            );
        }

        CompromisoPago compromiso = compromisoPagoRepository
                .bloquearPorId(request.idCompromisoPago())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "COMPROMISO_PAGO_NO_ENCONTRADO",
                        "No se encontro el compromiso de pago con id " + request.idCompromisoPago()
                ));
        if (compromiso.getEstadoAdministrativo() == com.asiscontrol.entity.enums.EstadoAdministrativoCompromiso.ANULADO
                || compromiso.getEstado() == EstadoCompromisoPago.PAGADO) {
            throw new BusinessRuleException(
                    "COMPROMISO_NO_ADMITE_PAGOS",
                    "El compromiso se encuentra " + compromiso.getEstado()
            );
        }
        if (request.idApoderado() == null && request.idAdministrador() == null) {
            throw new BusinessRuleException(
                    "RESPONSABLE_PAGO_REQUERIDO",
                    "Debe indicarse el apoderado que paga o el administrador que registra"
            );
        }

        BigDecimal monto = dinero(request.monto());
        BigDecimal saldo = saldo(compromiso);
        if (monto.compareTo(saldo) > 0) {
            throw new BusinessRuleException(
                    "PAGO_EXCEDE_SALDO",
                    "El pago excede el saldo pendiente de " + saldo
            );
        }

        Apoderado apoderado = request.idApoderado() == null
                ? null
                : apoderadoRepository.findByIdAndActivoTrue(request.idApoderado())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "APODERADO_NO_ENCONTRADO",
                                "No se encontro el apoderado con id " + request.idApoderado()
                        ));
        Administrador administrador = request.idAdministrador() == null
                ? null
                : administradorRepository.findByIdAndActivoTrue(request.idAdministrador())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "ADMINISTRADOR_NO_ENCONTRADO",
                                "No se encontro el administrador con id " + request.idAdministrador()
                        ));

        TransaccionPago transaccion = new TransaccionPago();
        transaccion.setCompromisoPago(compromiso);
        transaccion.setApoderado(apoderado);
        transaccion.setAdministrador(administrador);
        transaccion.setNumeroOperacion(numeroOperacion);
        transaccion.setMonto(monto);
        transaccion.setMedioPago(request.medioPago());
        transaccion.setEstado(EstadoTransaccionPago.APROBADA);
        transaccion.setFechaTransaccion(
                request.fechaTransaccion() == null ? Instant.now() : request.fechaTransaccion()
        );
        transaccion.setObservacion(limpiar(request.observacion()));
        TransaccionPago guardada = transaccionPagoRepository.save(transaccion);

        String compromisoAnterior = "compromisoId=" + compromiso.getId()
                + ",montoPagado=" + compromiso.getMontoPagado()
                + ",estado=" + compromiso.getEstado();
        compromiso.setMontoPagado(dinero(compromiso.getMontoPagado().add(monto)));
        compromiso.setEstado(calcularEstado(compromiso));
        compromisoPagoRepository.save(compromiso);
        auditoria.registrar(
                "REGISTRAR_TRANSACCION",
                ModuloAuditoria.FINANZAS,
                "TransaccionPago",
                guardada.getId().toString(),
                ResultadoAuditoria.EXITOSO,
                null,
                compromisoAnterior,
                "numeroOperacion=" + guardada.getNumeroOperacion()
                        + ",monto=" + guardada.getMonto()
                        + ",medioPago=" + guardada.getMedioPago()
                        + ",apoderadoId=" + (apoderado == null ? null : apoderado.getId())
                        + ",administradorId=" + (administrador == null ? null : administrador.getId())
                        + ",compromisoId=" + compromiso.getId()
                        + ",montoPagado=" + compromiso.getMontoPagado()
                        + ",estado=" + compromiso.getEstado());
        return toTransaccionResponse(guardada);
    }

    @Transactional(readOnly = true)
    public Page<PagoDto.ComprobanteResponse> listarComprobantes(
            Long personaApoderadoId,
            Pageable pageable
    ) {
        return comprobanteRepository.buscarVisibles(personaApoderadoId, pageable)
                .map(this::toComprobanteResponse);
    }

    @Transactional(readOnly = true)
    public PagoDto.ComprobanteResponse obtenerComprobante(Long id) {
        Comprobante comprobante = comprobanteRepository.findByIdAndActivoTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "COMPROBANTE_NO_ENCONTRADO",
                        "No se encontro el comprobante con id " + id
                ));
        return toComprobanteResponse(comprobante);
    }

    @Transactional
    public PagoDto.ComprobanteResponse emitirComprobante(PagoDto.ComprobanteRequest request) {
        TransaccionPago transaccion = buscarTransaccionActiva(request.idTransaccionPago());
        if (transaccion.getEstado() != EstadoTransaccionPago.APROBADA) {
            throw new BusinessRuleException(
                    "TRANSACCION_NO_APROBADA",
                    "Solo se puede emitir comprobante para una transaccion aprobada"
            );
        }

        String serie = request.serie().trim().toUpperCase();
        String numero = request.numero().trim().toUpperCase();
        var existente = comprobanteRepository
                .findByTransaccionPagoIdAndActivoTrue(transaccion.getId());
        if (existente.isPresent()) {
            Comprobante comprobante = existente.get();
            if (comprobante.getSerie().equalsIgnoreCase(serie)
                    && comprobante.getNumero().equalsIgnoreCase(numero)
                    && comprobante.getTipoComprobante() == request.tipoComprobante()) {
                return toComprobanteResponse(comprobante);
            }
            throw new ConflictException(
                    "TRANSACCION_CON_COMPROBANTE",
                    "La transaccion ya cuenta con un comprobante diferente"
            );
        }
        if (comprobanteRepository.existsBySerieIgnoreCaseAndNumeroIgnoreCaseAndActivoTrue(
                serie,
                numero
        )) {
            throw new ConflictException(
                    "COMPROBANTE_DUPLICADO",
                    "La serie y numero del comprobante ya estan registrados"
            );
        }

        ArchivoAdjunto archivo = request.idArchivoAdjunto() == null
                ? null
                : archivoAdjuntoRepository.findByIdAndActivoTrue(request.idArchivoAdjunto())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "ARCHIVO_NO_ENCONTRADO",
                                "No se encontro el archivo adjunto con id " + request.idArchivoAdjunto()
                        ));

        Comprobante comprobante = new Comprobante();
        comprobante.setTransaccionPago(transaccion);
        comprobante.setArchivo(archivo);
        comprobante.setTipoComprobante(request.tipoComprobante());
        comprobante.setSerie(serie);
        comprobante.setNumero(numero);
        comprobante.setFechaEmision(Instant.now());
        comprobante.setMontoTotal(transaccion.getMonto());
        Comprobante guardado = comprobanteRepository.save(comprobante);
        auditoria.registrar(
                "EMITIR_COMPROBANTE",
                ModuloAuditoria.FINANZAS,
                "Comprobante",
                guardado.getId().toString(),
                ResultadoAuditoria.EXITOSO,
                null,
                null,
                "tipo=" + guardado.getTipoComprobante()
                        + ",serie=" + guardado.getSerie()
                        + ",numero=" + guardado.getNumero()
                        + ",montoTotal=" + guardado.getMontoTotal()
                        + ",transaccionId=" + transaccion.getId()
                        + ",archivoId=" + (archivo == null ? null : archivo.getId()));
        return toComprobanteResponse(guardado);
    }

    public PagoDto.SimulacionResponse simular(PagoDto.SimulacionRequest request) {
        BigDecimal montoTotal = dinero(request.montoTotal());
        BigDecimal cuotaInicial = dinero(request.cuotaInicial());
        if (cuotaInicial.compareTo(montoTotal) > 0) {
            throw new BusinessRuleException(
                    "CUOTA_INICIAL_EXCEDE_TOTAL",
                    "La cuota inicial no puede exceder el monto total"
            );
        }

        BigDecimal financiado = dinero(montoTotal.subtract(cuotaInicial));
        if (financiado.signum() == 0) {
            return new PagoDto.SimulacionResponse(
                    montoTotal,
                    cuotaInicial,
                    financiado,
                    BigDecimal.ZERO.setScale(ESCALA_DINERO),
                    montoTotal,
                    List.of()
            );
        }

        BigDecimal tasa = request.tasaInteresMensual()
                .divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP);
        BigDecimal cuota;
        if (tasa.signum() == 0) {
            cuota = financiado.divide(
                    BigDecimal.valueOf(request.numeroCuotas()),
                    ESCALA_DINERO,
                    RoundingMode.HALF_UP
            );
        } else {
            BigDecimal factor = BigDecimal.ONE.add(tasa)
                    .pow(request.numeroCuotas(), CALCULO_FINANCIERO);
            cuota = financiado.multiply(tasa, CALCULO_FINANCIERO)
                    .multiply(factor, CALCULO_FINANCIERO)
                    .divide(factor.subtract(BigDecimal.ONE), ESCALA_DINERO, RoundingMode.HALF_UP);
        }

        BigDecimal saldo = financiado;
        BigDecimal totalInteres = BigDecimal.ZERO;
        List<PagoDto.CuotaSimulada> cronograma = new ArrayList<>();
        for (int numero = 1; numero <= request.numeroCuotas(); numero++) {
            BigDecimal interes = dinero(saldo.multiply(tasa, CALCULO_FINANCIERO));
            BigDecimal capital = dinero(cuota.subtract(interes));
            BigDecimal montoCuota = cuota;
            if (numero == request.numeroCuotas() || capital.compareTo(saldo) > 0) {
                capital = saldo;
                montoCuota = dinero(capital.add(interes));
            }
            saldo = dinero(saldo.subtract(capital).max(BigDecimal.ZERO));
            totalInteres = dinero(totalInteres.add(interes));
            cronograma.add(new PagoDto.CuotaSimulada(
                    numero,
                    request.fechaPrimeraCuota().plusMonths(numero - 1L),
                    capital,
                    interes,
                    montoCuota,
                    saldo
            ));
        }

        BigDecimal totalPagar = dinero(montoTotal.add(totalInteres));
        return new PagoDto.SimulacionResponse(
                montoTotal,
                cuotaInicial,
                financiado,
                totalInteres,
                totalPagar,
                List.copyOf(cronograma)
        );
    }

    private TransaccionPago buscarTransaccionActiva(Long id) {
        return transaccionPagoRepository.findByIdAndActivoTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "TRANSACCION_PAGO_NO_ENCONTRADA",
                        "No se encontro la transaccion de pago con id " + id
                ));
    }

    private boolean esMismaOperacion(
            TransaccionPago existente,
            PagoDto.TransaccionRequest request
    ) {
        Long idApoderado = existente.getApoderado() == null ? null : existente.getApoderado().getId();
        Long idAdministrador = existente.getAdministrador() == null
                ? null
                : existente.getAdministrador().getId();
        return existente.getCompromisoPago().getId().equals(request.idCompromisoPago())
                && existente.getMonto().compareTo(request.monto()) == 0
                && existente.getMedioPago() == request.medioPago()
                && Objects.equals(idApoderado, request.idApoderado())
                && Objects.equals(idAdministrador, request.idAdministrador());
    }

    private EstadoCompromisoPago calcularEstado(CompromisoPago compromiso) {
        if (compromiso.getEstado() == EstadoCompromisoPago.ANULADO) {
            return EstadoCompromisoPago.ANULADO;
        }
        if (saldo(compromiso).signum() == 0) {
            return EstadoCompromisoPago.PAGADO;
        }
        if (compromiso.getFechaVencimiento().isBefore(LocalDate.now(java.time.ZoneId.of("America/Lima")))) {
            return EstadoCompromisoPago.VENCIDO;
        }
        if (compromiso.getMontoPagado().signum() > 0) {
            return EstadoCompromisoPago.PARCIAL;
        }
        return EstadoCompromisoPago.PENDIENTE;
    }

    private BigDecimal saldo(CompromisoPago compromiso) {
        return dinero(compromiso.getMontoTotal().subtract(compromiso.getMontoPagado()));
    }

    private PagoDto.TransaccionResponse toTransaccionResponse(TransaccionPago transaccion) {
        return new PagoDto.TransaccionResponse(
                transaccion.getId(),
                transaccion.getCompromisoPago().getId(),
                transaccion.getApoderado() == null ? null : transaccion.getApoderado().getId(),
                transaccion.getAdministrador() == null ? null : transaccion.getAdministrador().getId(),
                transaccion.getNumeroOperacion(),
                transaccion.getMonto(),
                transaccion.getMedioPago(),
                transaccion.getEstado(),
                transaccion.getFechaTransaccion(),
                transaccion.getObservacion(),
                transaccion.getFechaCreacion()
        );
    }

    private PagoDto.ComprobanteResponse toComprobanteResponse(Comprobante comprobante) {
        return new PagoDto.ComprobanteResponse(
                comprobante.getId(),
                comprobante.getTransaccionPago().getId(),
                comprobante.getArchivo() == null ? null : comprobante.getArchivo().getId(),
                comprobante.getTipoComprobante(),
                comprobante.getSerie(),
                comprobante.getNumero(),
                comprobante.getFechaEmision(),
                comprobante.getMontoTotal()
        );
    }

    private BigDecimal dinero(BigDecimal monto) {
        return monto.setScale(ESCALA_DINERO, RoundingMode.HALF_UP);
    }

    private String limpiar(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }
}
