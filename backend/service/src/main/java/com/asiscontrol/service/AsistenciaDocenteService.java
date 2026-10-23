package com.asiscontrol.service;


import com.asiscontrol.service.ModuloAccessGuard;
import com.asiscontrol.dto.asistencia.AsistenciaDtos.AsistenciaDocenteResponse;
import com.asiscontrol.dto.asistencia.AsistenciaDtos.CodigoQRResponse;
import com.asiscontrol.dto.asistencia.AsistenciaDtos.EmitirCodigoQRRequest;
import com.asiscontrol.dto.asistencia.AsistenciaDtos.RegistrarContingenciaRequest;
import com.asiscontrol.entity.AsistenciaDocente;
import com.asiscontrol.entity.AuditableEntity;
import com.asiscontrol.entity.CodigoQRAsistencia;
import com.asiscontrol.entity.Docente;
import com.asiscontrol.entity.enums.EstadoAsistencia;
import com.asiscontrol.entity.enums.EstadoCodigoQR;
import com.asiscontrol.entity.enums.ResultadoAuditoria;
import com.asiscontrol.exception.BusinessRuleException;
import com.asiscontrol.exception.ConflictException;
import com.asiscontrol.exception.ResourceNotFoundException;
import com.asiscontrol.repository.AsistenciaDocenteRepository;
import com.asiscontrol.repository.CodigoQRAsistenciaRepository;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
@Transactional(readOnly = true)
public class AsistenciaDocenteService {

    private final AsistenciaDocenteRepository asistenciaRepository;
    private final CodigoQRAsistenciaRepository codigoRepository;
    private final EntityManager entityManager;
    private final ModuloAccessGuard accessGuard;
    private final AuditoriaService auditoria;
    private final SecureRandom secureRandom = new SecureRandom();
    private final int vigenciaPredeterminadaSegundos;

    public AsistenciaDocenteService(
            AsistenciaDocenteRepository asistenciaRepository,
            CodigoQRAsistenciaRepository codigoRepository,
            EntityManager entityManager,
            ModuloAccessGuard accessGuard,
            AuditoriaService auditoria,
            @Value("${app.asistencia.qr-vigencia-segundos:120}") int vigenciaPredeterminadaSegundos) {
        this.asistenciaRepository = asistenciaRepository;
        this.codigoRepository = codigoRepository;
        this.entityManager = entityManager;
        this.accessGuard = accessGuard;
        this.auditoria = auditoria;
        this.vigenciaPredeterminadaSegundos = vigenciaPredeterminadaSegundos;
    }

    @Transactional
    public CodigoQRResponse emitir(EmitirCodigoQRRequest request) {
        Docente docente = buscarActivo(Docente.class, request.docenteId(), "DOCENTE_NO_ENCONTRADO", "docente");
        accessGuard.verificarDocentePropietario(docente);
        validarSinAsistenciaEnJornada(docente.getId(), LocalDate.now());
        invalidarCodigosVigentes(docente.getId());
        CodigoQRResponse response = crearCodigo(docente, request.vigenciaSegundos());
        auditoria.registrar("EMITIR_QR_DOCENTE", "ASISTENCIA", "CodigoQRAsistencia",
                response.id().toString(), ResultadoAuditoria.EXITOSO, null, null,
                "docente=" + docente.getId());
        return response;
    }

    @Transactional
    public CodigoQRResponse renovar(Long codigoId, Integer vigenciaSegundos) {
        CodigoQRAsistencia anterior = obtenerCodigo(codigoId);
        accessGuard.verificarDocentePropietario(anterior.getDocente());
        actualizarExpiracion(anterior);
        if (anterior.getEstado() == EstadoCodigoQR.UTILIZADO) {
            throw new BusinessRuleException(
                    "QR_YA_UTILIZADO", "No se puede renovar un código QR que ya registró asistencia");
        }
        if (anterior.getEstado() == EstadoCodigoQR.INVALIDADO) {
            throw new BusinessRuleException(
                    "QR_INVALIDADO", "No se puede renovar un código QR cancelado o reemplazado");
        }
        if (anterior.getEstado() == EstadoCodigoQR.VIGENTE) {
            anterior.setEstado(EstadoCodigoQR.INVALIDADO);
            anterior.setFechaHoraCancelacion(LocalDateTime.now());
            codigoRepository.save(anterior);
        }
        validarSinAsistenciaEnJornada(anterior.getDocente().getId(), LocalDate.now());
        invalidarCodigosVigentes(anterior.getDocente().getId());
        return crearCodigo(anterior.getDocente(), vigenciaSegundos);
    }

    @Transactional(noRollbackFor = BusinessRuleException.class)
    public void cancelar(Long codigoId) {
        CodigoQRAsistencia codigo = obtenerCodigo(codigoId);
        accessGuard.verificarDocentePropietario(codigo.getDocente());
        actualizarExpiracion(codigo);
        if (codigo.getEstado() != EstadoCodigoQR.VIGENTE) {
            throw new BusinessRuleException("QR_NO_CANCELABLE", "Solo se puede cancelar un código QR vigente");
        }
        codigo.setEstado(EstadoCodigoQR.INVALIDADO);
        codigo.setFechaHoraCancelacion(LocalDateTime.now());
        codigoRepository.save(codigo);
        auditoria.registrar("CANCELAR_QR_DOCENTE", "ASISTENCIA", "CodigoQRAsistencia",
                codigoId.toString(), ResultadoAuditoria.EXITOSO, null, null, null);
    }

    @Transactional(noRollbackFor = BusinessRuleException.class)
    public AsistenciaDocenteResponse marcarConToken(String token) {
        String hash = hashToken(token);
        CodigoQRAsistencia codigo = codigoRepository.findLockedByTokenHashAndActivoTrue(hash)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "QR_NO_ENCONTRADO", "El código QR no existe o no es válido"));
        accessGuard.verificarDocentePropietario(codigo.getDocente());
        actualizarExpiracion(codigo);
        if (codigo.getEstado() == EstadoCodigoQR.EXPIRADO) {
            throw new BusinessRuleException("QR_EXPIRADO", "El código QR ya expiró; solicite uno nuevo");
        }
        if (codigo.getEstado() != EstadoCodigoQR.VIGENTE) {
            throw new BusinessRuleException("QR_NO_VIGENTE", "El código QR ya fue utilizado o invalidado");
        }

        LocalDateTime ahora = LocalDateTime.now();
        LocalDate jornada = ahora.toLocalDate();
        if (asistenciaRepository.existsByDocenteIdAndFechaJornadaAndActivoTrue(codigo.getDocente().getId(), jornada)) {
            throw new ConflictException(
                    "ASISTENCIA_DOCENTE_DUPLICADA", "El docente ya registró asistencia para la jornada actual");
        }

        AsistenciaDocente asistencia = new AsistenciaDocente();
        asistencia.setDocente(codigo.getDocente());
        asistencia.setCodigoQR(codigo);
        asistencia.setFechaJornada(jornada);
        asistencia.setFechaHoraMarcada(ahora);
        asistencia.setEstado(EstadoAsistencia.PRESENTE);
        asistencia.setContingencia(false);
        asistencia.setActivo(true);

        codigo.setEstado(EstadoCodigoQR.UTILIZADO);
        codigo.setFechaHoraUso(ahora);
        codigoRepository.save(codigo);
        AsistenciaDocenteResponse response = mapResponse(asistenciaRepository.save(asistencia));
        auditoria.registrar("MARCAR_ASISTENCIA_DOCENTE", "ASISTENCIA", "AsistenciaDocente",
                response.id().toString(), ResultadoAuditoria.EXITOSO, null, null,
                "docente=" + codigo.getDocente().getId() + ",estado=PRESENTE");
        return response;
    }

    @Transactional
    public AsistenciaDocenteResponse registrarContingencia(RegistrarContingenciaRequest request) {
        if (request.fechaJornada().isAfter(LocalDate.now())) {
            throw new BusinessRuleException(
                    "ASISTENCIA_FECHA_FUTURA", "No se puede registrar una contingencia en una fecha futura");
        }
        if (request.estado() != EstadoAsistencia.PRESENTE && request.estado() != EstadoAsistencia.TARDANZA) {
            throw new BusinessRuleException(
                    "CONTINGENCIA_ESTADO_INVALIDO", "La contingencia solo puede registrar presencia o tardanza");
        }
        if (request.fechaHoraMarcada() != null
                && (!request.fechaHoraMarcada().toLocalDate().equals(request.fechaJornada())
                || request.fechaHoraMarcada().isAfter(LocalDateTime.now()))) {
            throw new BusinessRuleException(
                    "CONTINGENCIA_FECHA_HORA_INVALIDA",
                    "La fecha y hora de marcación debe pertenecer a la jornada y no estar en el futuro");
        }
        Docente docente = buscarActivo(Docente.class, request.docenteId(), "DOCENTE_NO_ENCONTRADO", "docente");
        accessGuard.verificarDocentePropietario(docente);
        if (asistenciaRepository.existsByDocenteIdAndFechaJornadaAndActivoTrue(docente.getId(), request.fechaJornada())) {
            throw new ConflictException(
                    "ASISTENCIA_DOCENTE_DUPLICADA", "El docente ya tiene asistencia para la jornada indicada");
        }

        AsistenciaDocente asistencia = new AsistenciaDocente();
        asistencia.setDocente(docente);
        asistencia.setFechaJornada(request.fechaJornada());
        asistencia.setFechaHoraMarcada(request.fechaHoraMarcada() == null
                ? LocalDateTime.now()
                : request.fechaHoraMarcada());
        asistencia.setEstado(request.estado());
        asistencia.setContingencia(true);
        asistencia.setMotivoContingencia(request.motivo().trim());
        asistencia.setActivo(true);
        AsistenciaDocenteResponse response = mapResponse(asistenciaRepository.save(asistencia));
        auditoria.registrar("REGISTRAR_CONTINGENCIA_DOCENTE", "ASISTENCIA", "AsistenciaDocente",
                response.id().toString(), ResultadoAuditoria.EXITOSO, null, null,
                "docente=" + docente.getId() + ",estado=" + request.estado() + ",motivo=" + request.motivo().trim());
        return response;
    }

    public Page<AsistenciaDocenteResponse> listar(
            Long docenteId,
            Long personaDocenteId,
            Pageable pageable
    ) {
        return asistenciaRepository.buscarVisibles(docenteId, personaDocenteId, pageable)
                .map(this::mapResponse);
    }

    @Transactional
    public CodigoQRResponse consultarCodigo(Long codigoId) {
        CodigoQRAsistencia codigo = obtenerCodigo(codigoId);
        accessGuard.verificarDocentePropietario(codigo.getDocente());
        actualizarExpiracion(codigo);
        return new CodigoQRResponse(
                codigo.getId(),
                codigo.getDocente().getId(),
                null,
                codigo.getFechaHoraEmision(),
                codigo.getFechaHoraExpiracion(),
                codigo.getEstado());
    }

    private CodigoQRResponse crearCodigo(Docente docente, Integer vigenciaSolicitada) {
        validarSinAsistenciaEnJornada(docente.getId(), LocalDate.now());
        int vigencia = vigenciaSolicitada == null ? vigenciaPredeterminadaSegundos : vigenciaSolicitada;
        if (vigencia < 30 || vigencia > 900) {
            throw new BusinessRuleException("QR_VIGENCIA_INVALIDA", "La vigencia debe estar entre 30 y 900 segundos");
        }
        byte[] aleatorio = new byte[32];
        secureRandom.nextBytes(aleatorio);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(aleatorio);
        LocalDateTime ahora = LocalDateTime.now();

        CodigoQRAsistencia codigo = new CodigoQRAsistencia();
        codigo.setDocente(docente);
        codigo.setTokenHash(hashToken(token));
        codigo.setFechaHoraEmision(ahora);
        codigo.setFechaHoraExpiracion(ahora.plusSeconds(vigencia));
        codigo.setEstado(EstadoCodigoQR.VIGENTE);
        codigo.setActivo(true);
        codigo = codigoRepository.save(codigo);
        return new CodigoQRResponse(
                codigo.getId(),
                docente.getId(),
                token,
                codigo.getFechaHoraEmision(),
                codigo.getFechaHoraExpiracion(),
                codigo.getEstado());
    }

    private void invalidarCodigosVigentes(Long docenteId) {
        LocalDateTime ahora = LocalDateTime.now();
        for (CodigoQRAsistencia codigo : codigoRepository.findAllByDocenteIdAndEstadoAndActivoTrue(
                docenteId, EstadoCodigoQR.VIGENTE)) {
            if (ahora.isAfter(codigo.getFechaHoraExpiracion())) {
                codigo.setEstado(EstadoCodigoQR.EXPIRADO);
            } else {
                codigo.setEstado(EstadoCodigoQR.INVALIDADO);
                codigo.setFechaHoraCancelacion(ahora);
            }
            codigoRepository.save(codigo);
        }
    }

    private void actualizarExpiracion(CodigoQRAsistencia codigo) {
        if (codigo.getEstado() == EstadoCodigoQR.VIGENTE
                && !LocalDateTime.now().isBefore(codigo.getFechaHoraExpiracion())) {
            codigo.setEstado(EstadoCodigoQR.EXPIRADO);
            codigoRepository.save(codigo);
        }
    }

    private void validarSinAsistenciaEnJornada(Long docenteId, LocalDate fechaJornada) {
        if (asistenciaRepository.existsByDocenteIdAndFechaJornadaAndActivoTrue(docenteId, fechaJornada)) {
            throw new ConflictException(
                    "ASISTENCIA_DOCENTE_DUPLICADA", "El docente ya registró asistencia para la jornada indicada");
        }
    }

    private CodigoQRAsistencia obtenerCodigo(Long id) {
        return codigoRepository.findByIdAndActivoTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "QR_NO_ENCONTRADO", "No existe un código QR activo con id " + id));
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 no está disponible", ex);
        }
    }

    private AsistenciaDocenteResponse mapResponse(AsistenciaDocente asistencia) {
        return new AsistenciaDocenteResponse(
                asistencia.getId(),
                asistencia.getDocente().getId(),
                asistencia.getCodigoQR() == null ? null : asistencia.getCodigoQR().getId(),
                asistencia.getFechaJornada(),
                asistencia.getFechaHoraMarcada(),
                asistencia.getEstado(),
                asistencia.isContingencia(),
                asistencia.getMotivoContingencia(),
                asistencia.getVersion());
    }

    private <T extends AuditableEntity> T buscarActivo(
            Class<T> tipo, Long id, String codigo, String nombreRecurso) {
        T entidad = entityManager.find(tipo, id);
        if (entidad == null || !entidad.isActivo()) {
            throw new ResourceNotFoundException(codigo, "No existe un " + nombreRecurso + " activo con id " + id);
        }
        return entidad;
    }
}
