package com.asiscontrol.service;

import com.asiscontrol.dto.RiesgoDto;
import com.asiscontrol.entity.AlertaRiesgo;
import com.asiscontrol.entity.Alumno;
import com.asiscontrol.entity.CriterioRiesgo;
import com.asiscontrol.entity.enums.EstadoAlerta;
import com.asiscontrol.entity.enums.IndicadorRiesgo;
import com.asiscontrol.entity.enums.NivelRiesgo;
import com.asiscontrol.entity.enums.ResultadoAuditoria;
import com.asiscontrol.entity.enums.OperadorComparacion;
import com.asiscontrol.exception.BusinessRuleException;
import com.asiscontrol.exception.ConflictException;
import com.asiscontrol.exception.ResourceNotFoundException;
import com.asiscontrol.repository.AlertaRiesgoRepository;
import com.asiscontrol.repository.AlumnoRepository;
import com.asiscontrol.repository.CriterioRiesgoRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class RiesgoService {

    private static final Set<EstadoAlerta> ESTADOS_ABIERTOS = EnumSet.of(
            EstadoAlerta.ACTIVA,
            EstadoAlerta.EN_SEGUIMIENTO
    );

    private final CriterioRiesgoRepository criterioRiesgoRepository;
    private final AlertaRiesgoRepository alertaRiesgoRepository;
    private final AlumnoRepository alumnoRepository;
    private final AuditoriaService auditoria;

    public RiesgoService(
            CriterioRiesgoRepository criterioRiesgoRepository,
            AlertaRiesgoRepository alertaRiesgoRepository,
            AlumnoRepository alumnoRepository,
            AuditoriaService auditoria
    ) {
        this.criterioRiesgoRepository = criterioRiesgoRepository;
        this.alertaRiesgoRepository = alertaRiesgoRepository;
        this.alumnoRepository = alumnoRepository;
        this.auditoria = auditoria;
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
    public List<RiesgoDto.AlertaResponse> evaluar(RiesgoDto.EvaluacionRequest request) {
        Alumno alumno = alumnoRepository.findByIdAndActivoTrue(request.idAlumno())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "ALUMNO_NO_ENCONTRADO",
                        "No se encontro el alumno con id " + request.idAlumno()
                ));
        Map<IndicadorRiesgo, BigDecimal> valores = construirMapaIndicadores(request.indicadores());
        List<AlertaRiesgo> afectadas = new ArrayList<>();

        for (CriterioRiesgo criterio : criterioRiesgoRepository.findAllByActivoTrue()) {
            BigDecimal valor = valores.get(criterio.getIndicador());
            if (valor == null) {
                continue;
            }
            AlertaRiesgo alertaAbierta = alertaRiesgoRepository
                    .findFirstByAlumnoIdAndCriterioIdAndEstadoInAndActivoTrue(
                            alumno.getId(),
                            criterio.getId(),
                            ESTADOS_ABIERTOS
                    )
                    .orElse(null);

            if (cumple(criterio.getOperador(), valor, criterio.getUmbral())) {
                AlertaRiesgo alerta = alertaAbierta == null ? new AlertaRiesgo() : alertaAbierta;
                if (alertaAbierta == null) {
                    alerta.setAlumno(alumno);
                    alerta.setCriterio(criterio);
                    alerta.setEstado(EstadoAlerta.ACTIVA);
                    alerta.setFechaDeteccion(Instant.now());
                }
                alerta.setValorDetectado(valor);
                alerta.setNivelRiesgo(criterio.getNivelRiesgo());
                alerta.setMensaje(construirMensaje(criterio, valor));
                alerta.setFechaAtencion(null);
                AlertaRiesgo guardada = alertaRiesgoRepository.save(alerta);
                auditoria.registrar(
                        alertaAbierta == null ? "GENERAR_ALERTA_RIESGO" : "ACTUALIZAR_ALERTA_RIESGO",
                        ModuloAuditoria.RIESGO, "AlertaRiesgo", guardada.getId().toString(),
                        ResultadoAuditoria.EXITOSO, "Evaluacion de indicadores", null,
                        "alumnoId=" + alumno.getId() + ",criterioId=" + criterio.getId()
                                + ",valor=" + valor + ",nivel=" + guardada.getNivelRiesgo()
                                + ",estado=" + guardada.getEstado());
                afectadas.add(guardada);
            } else if (alertaAbierta != null) {
                EstadoAlerta estadoPrevio = alertaAbierta.getEstado();
                alertaAbierta.setValorDetectado(valor);
                alertaAbierta.setEstado(EstadoAlerta.RESUELTA);
                alertaAbierta.setFechaAtencion(Instant.now());
                alertaAbierta.setObservacion(
                        "Resuelta automaticamente: el indicador dejo de cumplir el criterio"
                );
                AlertaRiesgo guardada = alertaRiesgoRepository.save(alertaAbierta);
                auditoria.registrar("RESOLVER_ALERTA_RIESGO_AUTOMATICA", ModuloAuditoria.RIESGO, "AlertaRiesgo",
                        guardada.getId().toString(), ResultadoAuditoria.EXITOSO,
                        "El indicador dejo de cumplir el criterio",
                        "estado=" + estadoPrevio,
                        "estado=" + guardada.getEstado() + ",valor=" + valor);
                afectadas.add(guardada);
            }
        }

        return afectadas.stream().map(this::toAlertaResponse).toList();
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
        criterio.setNombre(request.nombre().trim());
        criterio.setDescripcion(limpiar(request.descripcion()));
        criterio.setIndicador(request.indicador());
        criterio.setOperador(request.operador());
        criterio.setUmbral(request.umbral());
        criterio.setNivelRiesgo(request.nivelRiesgo());
    }

    private Map<IndicadorRiesgo, BigDecimal> construirMapaIndicadores(
            List<RiesgoDto.IndicadorValor> indicadores
    ) {
        Map<IndicadorRiesgo, BigDecimal> valores = new EnumMap<>(IndicadorRiesgo.class);
        for (RiesgoDto.IndicadorValor indicador : indicadores) {
            if (valores.putIfAbsent(indicador.indicador(), indicador.valor()) != null) {
                throw new ConflictException(
                        "INDICADOR_DUPLICADO",
                        "El indicador " + indicador.indicador() + " aparece mas de una vez"
                );
            }
        }
        return valores;
    }

    private boolean cumple(
            OperadorComparacion operador,
            BigDecimal valor,
            BigDecimal umbral
    ) {
        int comparacion = valor.compareTo(umbral);
        return switch (operador) {
            case MENOR_QUE -> comparacion < 0;
            case MENOR_IGUAL -> comparacion <= 0;
            case MAYOR_QUE -> comparacion > 0;
            case MAYOR_IGUAL -> comparacion >= 0;
            case IGUAL -> comparacion == 0;
        };
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

    private String construirMensaje(CriterioRiesgo criterio, BigDecimal valor) {
        return "El indicador " + criterio.getIndicador()
                + " presenta el valor " + valor.stripTrailingZeros().toPlainString()
                + " y cumple " + criterio.getOperador()
                + " " + criterio.getUmbral().stripTrailingZeros().toPlainString();
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
                criterio.getFechaActualizacion()
        );
    }

    private RiesgoDto.AlertaResponse toAlertaResponse(AlertaRiesgo alerta) {
        return new RiesgoDto.AlertaResponse(
                alerta.getId(),
                alerta.getAlumno().getId(),
                alerta.getCriterio().getId(),
                alerta.getCriterio().getNombre(),
                alerta.getCriterio().getIndicador(),
                alerta.getValorDetectado(),
                alerta.getCriterio().getUmbral(),
                alerta.getNivelRiesgo(),
                alerta.getEstado(),
                alerta.getMensaje(),
                alerta.getObservacion(),
                alerta.getFechaDeteccion(),
                alerta.getFechaAtencion(),
                alerta.getFechaActualizacion()
        );
    }

    private String limpiar(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }
}
