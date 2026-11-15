package com.asiscontrol.service;

import com.asiscontrol.dto.PagosFamiliaDto;
import com.asiscontrol.entity.Comprobante;
import com.asiscontrol.entity.enums.EstadoTransaccionPago;
import com.asiscontrol.entity.enums.MedioPago;
import com.asiscontrol.repository.ComprobanteRepository;
import com.asiscontrol.repository.OrdenPagoDetalleRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/** Historial de comprobantes aprobados, incluidas operaciones anteriores al checkout. */
@Service
@Transactional(readOnly = true)
public class ComprobantesFamiliaService {
    private final ComprobanteRepository comprobantes;
    private final OrdenPagoDetalleRepository detalles;
    private final ConsultaObligacionesService acceso;
    private final String institucion;

    public ComprobantesFamiliaService(ComprobanteRepository comprobantes, OrdenPagoDetalleRepository detalles,
            ConsultaObligacionesService acceso, @Value("${app.pagos.institucion:Colegio PUCP}") String institucion) {
        this.comprobantes = comprobantes;
        this.detalles = detalles;
        this.acceso = acceso;
        this.institucion = institucion;
    }

    public Page<PagosFamiliaDto.HistorialComprobanteResponse> listar(Pageable pageable) {
        var apoderado = acceso.apoderadoActual();
        return comprobantes.buscarHistorialFamiliar(apoderado.getId(), pageable).map(this::respuesta);
    }

    public PagosFamiliaDto.HistorialComprobanteResponse obtener(Long id) {
        var apoderado = acceso.apoderadoActual();
        var comprobante = comprobantes.findByIdAndActivoTrue(id).orElseThrow(this::sinAcceso);
        var tx = comprobante.getTransaccionPago();
        if (!tx.isActivo() || tx.getEstado() != EstadoTransaccionPago.APROBADA) throw sinAcceso();
        acceso.verificarCompromiso(tx.getCompromisoPago().getId(), apoderado);
        return respuesta(comprobante);
    }

    public String descargar(Long id) {
        var c = obtener(id);
        var item = new PagosFamiliaDto.ItemResponse(null, c.codigoCompromiso(), c.alumno(),
                c.concepto(), c.numeroCuota(), c.montoTotal());
        return ConstanciaPagoHtml.crear(c.institucion(), c.codigo(), c.apoderado(), c.fechaEmision(),
                medio(c.medioPago()), c.moneda(), List.of(item), c.montoTotal(), List.of(c.codigo()));
    }

    private PagosFamiliaDto.HistorialComprobanteResponse respuesta(Comprobante c) {
        var tx = c.getTransaccionPago();
        var compromiso = tx.getCompromisoPago();
        var detalle = detalles.findByTransaccionId(tx.getId());
        var orden = detalle.map(d -> d.getOrden()).orElse(null);
        return new PagosFamiliaDto.HistorialComprobanteResponse(c.getId(), codigo(c.getId()),
                c.getFechaEmision(), c.getMontoTotal(), compromiso.getMoneda(),
                compromiso.getMatricula().getAlumno().getPersona().nombreCompleto(),
                compromiso.getConcepto(), compromiso.getNumeroCuota(), compromiso.getCodigoCompromiso(),
                tx.getMedioPago(), orden == null ? null : orden.getId(),
                orden == null ? null : PagosFamiliaService.codigoOrden(orden.getId()), institucion,
                tx.getApoderado() == null ? "Registro institucional" : tx.getApoderado().getPersona().nombreCompleto());
    }

    static String codigo(Long id) {
        return String.format(Locale.ROOT, "REC-%06d", id);
    }

    private String medio(MedioPago medio) {
        return switch (medio) {
            case TARJETA_SIMULADA, TARJETA_CREDITO, TARJETA_DEBITO, POS -> "Tarjeta";
            case QR_SIMULADO -> "Código QR";
            case TRANSFERENCIA_BANCARIA -> "Transferencia bancaria";
            case EFECTIVO -> "Efectivo";
            case YAPE -> "Yape";
            case PLIN -> "Plin";
        };
    }

    private AccessDeniedException sinAcceso() {
        return new AccessDeniedException("No tienes acceso al comprobante solicitado.");
    }
}
