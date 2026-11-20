package com.asiscontrol.service;

import com.asiscontrol.dto.PagosFamiliaDto;
import com.asiscontrol.dto.PagosFamiliaDto.ResultadoHistorial;
import com.asiscontrol.entity.TransaccionPago;
import com.asiscontrol.entity.enums.EstadoOrdenPago;
import com.asiscontrol.entity.enums.EstadoTransaccionPago;
import com.asiscontrol.exception.BusinessRuleException;
import com.asiscontrol.repository.ComprobanteRepository;
import com.asiscontrol.repository.OrdenPagoDetalleRepository;
import com.asiscontrol.repository.TransaccionPagoRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class HistorialPagosFamiliaService {
    private final TransaccionPagoRepository transacciones;
    private final ComprobanteRepository comprobantes;
    private final OrdenPagoDetalleRepository detalles;
    private final ConsultaObligacionesService acceso;

    public HistorialPagosFamiliaService(TransaccionPagoRepository transacciones,
            ComprobanteRepository comprobantes, OrdenPagoDetalleRepository detalles,
            ConsultaObligacionesService acceso) {
        this.transacciones = transacciones;
        this.comprobantes = comprobantes;
        this.detalles = detalles;
        this.acceso = acceso;
    }

    public Page<PagosFamiliaDto.HistorialPagoResponse> listar(Long idAlumno, ResultadoHistorial estado, Pageable pageable) {
        var apoderado = acceso.apoderadoActual();
        if (idAlumno != null) acceso.verificarAlumno(idAlumno, apoderado);
        if (pageable.getPageSize() > 100) throw new BusinessRuleException("PAGINA_INVALIDA", "Solicita hasta 100 registros por página.");
        var pagina = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Order.desc("fechaTransaccion"), Sort.Order.desc("id")));
        return transacciones.buscarHistorialFamiliar(apoderado.getId(), idAlumno,
                estado == null ? null : estado.name(), Instant.now(), pagina).map(this::respuesta);
    }

    private PagosFamiliaDto.HistorialPagoResponse respuesta(TransaccionPago tx) {
        var cp = tx.getCompromisoPago();
        var orden = detalles.findByTransaccionId(tx.getId()).map(d -> d.getOrden()).orElse(null);
        boolean aprobado = tx.getEstado() == EstadoTransaccionPago.APROBADA;
        var comprobante = aprobado ? comprobantes.findByTransaccionPagoIdAndActivoTrue(tx.getId()).orElseThrow() : null;
        var estado = aprobado ? ResultadoHistorial.APROBADO
                : orden.getEstado() == EstadoOrdenPago.RECHAZADO ? ResultadoHistorial.RECHAZADO : ResultadoHistorial.VENCIDO;
        var codigoOrden = orden == null ? null : PagosFamiliaService.codigoOrden(orden.getId());
        return new PagosFamiliaDto.HistorialPagoResponse(tx.getId(), comprobante == null ? null : comprobante.getId(),
                comprobante == null ? codigoOrden : ComprobantesFamiliaService.codigo(comprobante.getId()),
                comprobante == null ? tx.getFechaTransaccion() : comprobante.getFechaEmision(),
                aprobado ? comprobante.getMontoTotal() : tx.getMonto(), cp.getMoneda(), cp.getMatricula().getAlumno().getPersona().nombreCompleto(),
                cp.getConcepto(), cp.getNumeroCuota(), cp.getCodigoCompromiso(), tx.getMedioPago(),
                orden == null ? null : orden.getId(), codigoOrden, estado,
                aprobado ? null : estado == ResultadoHistorial.VENCIDO ? "La orden venció sin completarse." : orden.getMensaje());
    }
}
