package com.asiscontrol.service;

import com.asiscontrol.dto.EscalaInstitucionalDto;
import com.asiscontrol.entity.EscalaInstitucional;
import com.asiscontrol.entity.EscalaInstitucional.Ranura;
import com.asiscontrol.entity.enums.NivelLogro;
import com.asiscontrol.entity.enums.ResultadoAuditoria;
import com.asiscontrol.exception.BusinessRuleException;
import com.asiscontrol.exception.ConflictException;
import com.asiscontrol.exception.ResourceNotFoundException;
import com.asiscontrol.repository.EscalaInstitucionalRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class EscalaInstitucionalService {
    static final List<NivelLogro> ORDEN = List.of(NivelLogro.C, NivelLogro.B, NivelLogro.A, NivelLogro.AD);
    private final EscalaInstitucionalRepository escalas;
    private final AuditoriaService auditoria;
    private final com.asiscontrol.repository.PersonaRepository personas;

    public EscalaInstitucionalService(EscalaInstitucionalRepository escalas, AuditoriaService auditoria,
                                    com.asiscontrol.repository.PersonaRepository personas) {
        this.escalas = escalas;
        this.auditoria = auditoria;
        this.personas = personas;
    }

    @Transactional(readOnly = true)
    public List<EscalaInstitucionalDto.Escala> historial() {
        return escalas.findAllByOrderByNumeroVersionDesc().stream().map(this::respuesta).toList();
    }

    @Transactional
    public EscalaInstitucionalDto.Escala guardar(EscalaInstitucionalDto.Borrador request) {
        var rangos = new EnumMap<NivelLogro, EscalaInstitucional.Rango>(NivelLogro.class);
        for (var rango : request.rangos()) {
            if (rangos.putIfAbsent(rango.nivel(), new EscalaInstitucional.Rango(rango.minimo(), rango.maximo())) != null) {
                throw invalida("Cada nivel puede tener un solo rango.");
            }
        }
        validar(rangos, false);
        verificarConfiguracionVigente(escalas.findByRanura(Ranura.VIGENTE).orElse(null), rangos);
        var escala = escalas.findByRanura(Ranura.BORRADOR).orElseGet(() -> {
            var nueva = new EscalaInstitucional();
            nueva.setNumeroVersion(escalas.findFirstByOrderByNumeroVersionDesc()
                    .map(item -> item.getNumeroVersion() + 1).orElse(1));
            return nueva;
        });
        if (escala.getId() != null && request.version() == null && escala.getRangos().equals(rangos)) return respuesta(escala);
        verificarVersion(escala, request.version());
        if (escala.getId() != null && escala.getRangos().equals(rangos)) return respuesta(escala);
        String anterior = escala.getId() == null ? null : describir(escala);
        escala.getRangos().clear();
        escala.getRangos().putAll(rangos);
        escalas.saveAndFlush(escala);
        auditar("GUARDAR_BORRADOR_ESCALA_INSTITUCIONAL", escala, null, anterior);
        return respuesta(escala);
    }

    @Transactional
    public EscalaInstitucionalDto.Escala activar(Long id, EscalaInstitucionalDto.Activacion request) {
        var escala = escalas.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ESCALA_NO_ENCONTRADA", "Escala no encontrada"));
        verificarVersion(escala, request.version());
        if (escala.getRanura() != Ranura.BORRADOR) throw invalida("Solo se puede activar un borrador.");
        validar(escala.getRangos(), true);
        var anterior = escalas.findByRanura(Ranura.VIGENTE).orElse(null);
        verificarConfiguracionVigente(anterior, escala.getRangos());
        if (anterior != null) {
            anterior.setRanura(null);
            escalas.flush();
        }
        escala.setRanura(Ranura.VIGENTE);
        escala.setFechaAplicacion(Instant.now());
        var sesion = SecurityContextHolder.getContext().getAuthentication();
        var actor = sesion.getName();
        if (sesion.getPrincipal() instanceof org.springframework.security.oauth2.jwt.Jwt jwt
                && jwt.getClaim("personaId") instanceof Number idPersona) {
            actor = personas.findById(idPersona.longValue()).map(com.asiscontrol.entity.Persona::nombreCompleto)
                    .filter(nombre -> !nombre.isBlank()).orElse(actor);
        }
        escala.setActorAplicacion(actor.substring(0, Math.min(actor.length(), 180)));
        escala.setMotivoAplicacion(request.motivo().strip());
        escalas.flush();
        auditar("ACTIVAR_ESCALA_INSTITUCIONAL", escala, escala.getMotivoAplicacion(),
                anterior == null ? null : describir(anterior));
        return respuesta(escala);
    }

    private void verificarConfiguracionVigente(EscalaInstitucional vigente, Map<NivelLogro, EscalaInstitucional.Rango> rangos) {
        if (vigente != null && vigente.getRangos().equals(rangos)) {
            throw new BusinessRuleException("ESCALA_CONFIGURACION_VIGENTE",
                    "Esta configuración coincide con la escala vigente. No es necesario crear una nueva versión.");
        }
    }

    EscalaInstitucional exigirVigente() {
        return escalas.findByRanura(Ranura.VIGENTE).orElseThrow(() ->
                new BusinessRuleException("ESCALA_INSTITUCIONAL_NO_VIGENTE", "Debe existir una escala institucional vigente."));
    }

    static NivelLogro nivel(EscalaInstitucional escala, BigDecimal nota) {
        return nivel(escala, nota.setScale(0, RoundingMode.HALF_UP).intValueExact());
    }

    static NivelLogro nivel(EscalaInstitucional escala, int nota) {
        return escala.getRangos().entrySet().stream().filter(item -> item.getValue().contiene(nota))
                .findFirst().orElseThrow().getKey();
    }

    java.util.Optional<EscalaInstitucional> vigente() {
        return escalas.findByRanura(Ranura.VIGENTE);
    }

    private void validar(Map<NivelLogro, EscalaInstitucional.Rango> rangos, boolean completa) {
        int siguiente = 0;
        for (var nivel : ORDEN) {
            var rango = rangos.get(nivel);
            if (rango == null && !completa) continue;
            if (rango == null || rango.maximo() < rango.minimo() || rango.minimo() < siguiente
                    || (completa && rango.minimo() != siguiente)) {
                throw invalida("Los rangos deben respetar el orden C, B, A y AD. Una escala vigente debe cubrir del 0 al 20 sin huecos.");
            }
            siguiente = rango.maximo() + 1;
        }
        if (completa && siguiente != 21) throw invalida("La escala vigente debe cubrir del 0 al 20.");
    }

    private void verificarVersion(EscalaInstitucional escala, Long recibida) {
        if (escala.getId() == null ? recibida != null : recibida == null || escala.getVersion() != recibida) {
            throw new ConflictException("VERSION_DESACTUALIZADA", "La escala cambió. Actualiza los datos y vuelve a intentarlo.");
        }
    }

    private BusinessRuleException invalida(String mensaje) {
        return new BusinessRuleException("RANGOS_ESCALA_INVALIDOS", mensaje);
    }

    private EscalaInstitucionalDto.Escala respuesta(EscalaInstitucional escala) {
        return new EscalaInstitucionalDto.Escala(escala.getId(), escala.getNumeroVersion(),
                escala.getRanura() == null ? "HISTORICA" : escala.getRanura().name(), escala.getVersion(),
                ORDEN.stream().filter(escala.getRangos()::containsKey).map(nivel -> {
                    var rango = escala.getRangos().get(nivel);
                    return new EscalaInstitucionalDto.Rango(nivel, rango.minimo(), rango.maximo());
                }).toList(),
                escala.getFechaAplicacion(), escala.getActorAplicacion(), escala.getMotivoAplicacion());
    }

    private String describir(EscalaInstitucional escala) {
        return "versión=" + escala.getNumeroVersion() + ", rangos=" + respuesta(escala).rangos();
    }

    private void auditar(String accion, EscalaInstitucional escala, String motivo, String anterior) {
        auditoria.registrar(accion, ModuloAuditoria.ACADEMICO, "EscalaInstitucional", escala.getId().toString(),
                ResultadoAuditoria.EXITOSO, motivo, anterior, describir(escala));
    }
}
