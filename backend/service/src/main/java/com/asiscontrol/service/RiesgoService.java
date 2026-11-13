package com.asiscontrol.service;

import com.asiscontrol.dto.RiesgoDto;
import com.asiscontrol.entity.AlertaRiesgo;
import com.asiscontrol.entity.CriterioRiesgo;
import com.asiscontrol.entity.enums.EstadoAlerta;
import com.asiscontrol.entity.enums.IndicadorRiesgo;
import com.asiscontrol.entity.enums.NivelRiesgo;
import com.asiscontrol.entity.enums.ResultadoAuditoria;
import com.asiscontrol.exception.BusinessRuleException;
import com.asiscontrol.exception.ResourceNotFoundException;
import com.asiscontrol.repository.AlertaRiesgoRepository;
import com.asiscontrol.repository.CriterioRiesgoRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

@Service
public class RiesgoService {

    private static final Set<EstadoAlerta> ESTADOS_ABIERTOS = EnumSet.of(
            EstadoAlerta.ACTIVA,
            EstadoAlerta.EN_SEGUIMIENTO
    );

    private final CriterioRiesgoRepository criterioRiesgoRepository;
    private final AlertaRiesgoRepository alertaRiesgoRepository;
    private final AuditoriaService auditoria;
    private final EvidenciaRiesgoService evidencia;

    public RiesgoService(
            CriterioRiesgoRepository criterioRiesgoRepository,
            AlertaRiesgoRepository alertaRiesgoRepository,
            AuditoriaService auditoria,
            EvidenciaRiesgoService evidencia
    ) {
        this.criterioRiesgoRepository = criterioRiesgoRepository;
        this.alertaRiesgoRepository = alertaRiesgoRepository;
        this.auditoria = auditoria;
        this.evidencia = evidencia;
    }

    @Transactional(readOnly = true)
    public Page<RiesgoDto.CriterioResponse> listarCriterios(String busqueda, Pageable pageable) {
        String filtro = busqueda == null || busqueda.isBlank() ? null : busqueda.trim();
        return criterioRiesgoRepository.buscar(filtro, pageable).map(this::toCriterioResponse);
    }

    @Transactional(readOnly = true)
    public RiesgoDto.CriterioResponse obtenerCriterio(Long id) {
        return toCriterioResponse(buscarCriterioActivo(id));
    }

    @Transactional
    public RiesgoDto.CriterioResponse crearCriterio(RiesgoDto.CriterioRequest request) {
        CriterioRiesgo criterio = new CriterioRiesgo();
        aplicarCriterio(criterio, request);
        CriterioRiesgo saved = criterioRiesgoRepository.save(criterio);
        evidencia.configurarMinimo(saved.getId(), request.minimoDatos());
        auditoria.registrar("CREAR_CRITERIO_RIESGO", ModuloAuditoria.RIESGO, "CriterioRiesgo",
                saved.getId().toString(), ResultadoAuditoria.EXITOSO, null, null, describir(saved));
        return toCriterioResponse(saved);
    }

    @Transactional
    public RiesgoDto.CriterioResponse actualizarCriterio(
            Long id,
            RiesgoDto.CriterioRequest request
    ) {
        CriterioRiesgo criterio = buscarCriterioActivo(id);
        String anterior = describir(criterio);
        aplicarCriterio(criterio, request);
        CriterioRiesgo saved = criterioRiesgoRepository.save(criterio);
        evidencia.configurarMinimo(saved.getId(), request.minimoDatos());
        auditoria.registrar("ACTUALIZAR_CRITERIO_RIESGO", ModuloAuditoria.RIESGO, "CriterioRiesgo",
                id.toString(), ResultadoAuditoria.EXITOSO, null, anterior, describir(saved));
        return toCriterioResponse(saved);
    }

    @Transactional
    public void eliminarCriterio(Long id) {
        CriterioRiesgo criterio = buscarCriterioActivo(id);
        if (alertaRiesgoRepository.existsByCriterioIdAndEstadoInAndActivoTrue(id, ESTADOS_ABIERTOS)) {
            throw new BusinessRuleException(
                    "CRITERIO_CON_ALERTAS_ABIERTAS",
                    "No se puede eliminar un criterio que mantiene alertas abiertas"
            );
        }
        criterio.setActivo(false);
        criterioRiesgoRepository.save(criterio);
        auditoria.registrar("ELIMINAR_CRITERIO_RIESGO", ModuloAuditoria.RIESGO, "CriterioRiesgo",
                id.toString(), ResultadoAuditoria.EXITOSO, null, describir(criterio), null);
    }

    @Transactional(readOnly = true)
    public Page<RiesgoDto.AlertaResponse> listarAlertas(
            Long idAlumno,
            EstadoAlerta estado,
            NivelRiesgo nivel,
            Long personaDocenteId,
            Pageable pageable
    ) {
        return alertaRiesgoRepository.buscar(
                        idAlumno,
                        estado,
                        nivel,
                        personaDocenteId,
                        pageable)
                .map(this::toAlertaResponse);
    }

    @Transactional(readOnly = true)
    public RiesgoDto.AlertaResponse obtenerAlerta(Long id) {
        return toAlertaResponse(buscarAlertaActiva(id));
    }

    @Transactional
    public RiesgoDto.AlertaResponse cambiarEstadoAlerta(
            Long id,
            RiesgoDto.EstadoAlertaRequest request
    ) {
        AlertaRiesgo alerta = buscarAlertaActiva(id);
        EstadoAlerta actual = alerta.getEstado();
        EstadoAlerta destino = request.estado();
        if (actual != destino) {
            validarTransicion(actual, destino);
        }
        if (destino == EstadoAlerta.RESUELTA
                && (request.observacion() == null || request.observacion().isBlank())) {
            throw new BusinessRuleException(
                    "OBSERVACION_DE_CIERRE_REQUERIDA",
                    "Debe indicar una observacion al resolver la alerta"
            );
        }

        alerta.setEstado(destino);
        alerta.setObservacion(limpiar(request.observacion()));
        alerta.setFechaAtencion(destino == EstadoAlerta.RESUELTA ? Instant.now() : null);
        AlertaRiesgo saved = alertaRiesgoRepository.save(alerta);
        auditoria.registrar("CAMBIAR_ESTADO_ALERTA_RIESGO", ModuloAuditoria.RIESGO, "AlertaRiesgo",
                id.toString(), ResultadoAuditoria.EXITOSO, null,
                "estado=" + actual,
                "estado=" + destino + ",observacion=" + saved.getObservacion());
        return toAlertaResponse(saved);
    }

    private String describir(CriterioRiesgo criterio) {
        return "nombre=" + criterio.getNombre()
                + ",indicador=" + criterio.getIndicador()
                + ",operador=" + criterio.getOperador()
                + ",umbral=" + criterio.getUmbral()
                + ",nivel=" + criterio.getNivelRiesgo();
    }

    private void aplicarCriterio(CriterioRiesgo criterio, RiesgoDto.CriterioRequest request) {
        BigDecimal umbral = request.umbral();
        if (request.indicador() == IndicadorRiesgo.PROMEDIO_ACADEMICO) {
            if (umbral.compareTo(BigDecimal.ONE) < 0 || umbral.compareTo(BigDecimal.valueOf(4)) > 0)
                throw new BusinessRuleException("UMBRAL_INVALIDO", "El promedio usa la escala C=1, B=2, A=3, AD=4");
        } else if (umbral.stripTrailingZeros().scale() > 0) {
            throw new BusinessRuleException("UMBRAL_INVALIDO", "Los indicadores de cantidad requieren un umbral entero");
        }
        criterio.setNombre(request.nombre().trim());
        criterio.setDescripcion(limpiar(request.descripcion()));
        criterio.setIndicador(request.indicador());
        criterio.setOperador(request.operador());
        criterio.setUmbral(request.umbral());
        criterio.setNivelRiesgo(request.nivelRiesgo());
    }

    private void validarTransicion(EstadoAlerta actual, EstadoAlerta destino) {
        boolean permitida = switch (actual) {
            case ACTIVA -> destino == EstadoAlerta.EN_SEGUIMIENTO || destino == EstadoAlerta.RESUELTA;
            case EN_SEGUIMIENTO -> destino == EstadoAlerta.ACTIVA || destino == EstadoAlerta.RESUELTA;
            case RESUELTA -> false;
        };
        if (!permitida) {
            throw new BusinessRuleException(
                    "TRANSICION_ALERTA_INVALIDA",
                    "No se puede cambiar una alerta de " + actual + " a " + destino
            );
        }
    }

    private CriterioRiesgo buscarCriterioActivo(Long id) {
        return criterioRiesgoRepository.findByIdAndActivoTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "CRITERIO_RIESGO_NO_ENCONTRADO",
                        "No se encontro el criterio de riesgo con id " + id
                ));
    }

    private AlertaRiesgo buscarAlertaActiva(Long id) {
        return alertaRiesgoRepository.findByIdAndActivoTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "ALERTA_RIESGO_NO_ENCONTRADA",
                        "No se encontro la alerta de riesgo con id " + id
                ));
    }

    private RiesgoDto.CriterioResponse toCriterioResponse(CriterioRiesgo criterio) {
        return new RiesgoDto.CriterioResponse(
                criterio.getId(),
                criterio.getNombre(),
                criterio.getDescripcion(),
                criterio.getIndicador(),
                criterio.getOperador(),
                criterio.getUmbral(),
                criterio.getNivelRiesgo(),
                criterio.getFechaCreacion(),
                criterio.getFechaActualizacion(),
                evidencia.minimoDatos(criterio.getId())
        );
    }

    private RiesgoDto.AlertaResponse toAlertaResponse(AlertaRiesgo alerta) {
        var ultima = evidencia.ultima(alerta.getId());
        var deteccion = evidencia.ultimaDeteccion(alerta.getId());
        var regla = deteccion == null ? null : deteccion.evaluacion();
        return new RiesgoDto.AlertaResponse(
                alerta.getId(),
                alerta.getAlumno().getId(),
                alerta.getCriterio().getId(),
                regla == null ? alerta.getCriterio().getNombre() : regla.nombreCriterio(),
                regla == null ? alerta.getCriterio().getIndicador() : regla.indicador(),
                alerta.getValorDetectado(),
                regla == null ? null : regla.umbral(),
                alerta.getNivelRiesgo(),
                alerta.getEstado(),
                alerta.getMensaje(),
                alerta.getObservacion(),
                alerta.getFechaDeteccion(),
                alerta.getFechaAtencion(),
                alerta.getFechaActualizacion(),
                ultima == null ? null : ultima.idPeriodo(),
                ultima == null ? null : ultima.periodo(),
                regla == null ? null : regla.operador(),
                ultima != null && ultima.evaluacion().resultado() != RiesgoDto.Resultado.DATOS_INSUFICIENTES
        );
    }

    private String limpiar(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }
}
