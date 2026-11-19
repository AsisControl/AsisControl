package com.asiscontrol.service;


import com.asiscontrol.service.ModuloAccessGuard;
import com.asiscontrol.dto.asistencia.AsistenciaDtos.AsistenciaDocenteResponse;
import com.asiscontrol.dto.asistencia.AsistenciaDtos.ClaseDiaDocenteResponse;
import com.asiscontrol.dto.asistencia.AsistenciaDtos.CodigoQRResponse;
import com.asiscontrol.dto.asistencia.AsistenciaDtos.EmitirCodigoQRRequest;
import com.asiscontrol.dto.asistencia.AsistenciaDtos.RegistrarContingenciaRequest;
import com.asiscontrol.dto.asistencia.AsistenciaDtos.ResumenMarcacionResponse;
import com.asiscontrol.entity.AsistenciaDocente;
import com.asiscontrol.entity.AuditableEntity;
import com.asiscontrol.entity.CodigoQRAsistencia;
import com.asiscontrol.entity.Docente;
import com.asiscontrol.entity.enums.EstadoAsistencia;
import com.asiscontrol.entity.enums.EstadoCodigoQR;
import com.asiscontrol.entity.enums.EstadoVerificacionClase;
import com.asiscontrol.entity.asistencia.EstadoSesionClase;
import com.asiscontrol.entity.asistencia.SesionClase;
import com.asiscontrol.entity.enums.ResultadoAuditoria;
import com.asiscontrol.exception.BusinessRuleException;
import com.asiscontrol.exception.ConflictException;
import com.asiscontrol.exception.ResourceNotFoundException;
import com.asiscontrol.repository.AsistenciaDocenteRepository;
import com.asiscontrol.repository.CodigoQRAsistenciaRepository;
import com.asiscontrol.repository.DocenteRepository;
import com.asiscontrol.repository.asistencia.SesionClaseRepository;
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
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Base64;

@Service
@Transactional(readOnly = true)
public class AsistenciaDocenteService {

    private final AsistenciaDocenteRepository asistenciaRepository;
    private final CodigoQRAsistenciaRepository codigoRepository;
    private final SesionClaseRepository sesionRepository;
    private final DocenteRepository docenteRepository;
    private final Clock reloj;
    private final EntityManager entityManager;
    private final ModuloAccessGuard accessGuard;
    private final AuditoriaService auditoria;
    private final SecureRandom secureRandom = new SecureRandom();
    private final int vigenciaPredeterminadaSegundos;

    public AsistenciaDocenteService(
            AsistenciaDocenteRepository asistenciaRepository,
            CodigoQRAsistenciaRepository codigoRepository,
            SesionClaseRepository sesionRepository,
            DocenteRepository docenteRepository,
            Clock reloj,
            EntityManager entityManager,
            ModuloAccessGuard accessGuard,
            AuditoriaService auditoria,
            @Value("${app.asistencia.qr-vigencia-segundos:120}") int vigenciaPredeterminadaSegundos) {
        this.asistenciaRepository = asistenciaRepository;
        this.codigoRepository = codigoRepository;
        this.sesionRepository = sesionRepository;
        this.docenteRepository = docenteRepository;
        this.reloj = reloj;
        this.entityManager = entityManager;
        this.accessGuard = accessGuard;
        this.auditoria = auditoria;
        this.vigenciaPredeterminadaSegundos = vigenciaPredeterminadaSegundos;
    }

    /** Abre el punto de marcación: reemplaza el código vigente de la institución por uno nuevo. */
    @Transactional
    public CodigoQRResponse emitir(EmitirCodigoQRRequest request) {
        invalidarCodigosVigentes();
        CodigoQRResponse response = crearCodigo(request == null ? null : request.vigenciaSegundos());
        auditoria.registrar("EMITIR_QR_ASISTENCIA", ModuloAuditoria.ASISTENCIA, "CodigoQRAsistencia",
                response.id().toString(), ResultadoAuditoria.EXITOSO, null, null, null);
        return response;
    }

    /**
     * Entrega el código siguiente sin invalidar el anterior: quien lo escaneó justo antes de la
     * renovación todavía puede marcar mientras le quede vigencia.
     */
    @Transactional
    public CodigoQRResponse renovar(Long codigoId, Integer vigenciaSegundos) {
        CodigoQRAsistencia anterior = obtenerCodigo(codigoId);
        actualizarExpiracion(anterior);
        if (anterior.getEstado() == EstadoCodigoQR.INVALIDADO) {
            throw new BusinessRuleException(
                    "QR_INVALIDADO", "No se puede renovar un código QR cancelado o reemplazado");
        }
        CodigoQRResponse nuevo = crearCodigo(vigenciaSegundos);
        auditoria.registrar("RENOVAR_QR_ASISTENCIA", ModuloAuditoria.ASISTENCIA, "CodigoQRAsistencia",
                nuevo.id().toString(), ResultadoAuditoria.EXITOSO, null,
                "codigoAnteriorId=" + codigoId, "vigenciaSegundos=" + vigenciaSegundos);
        return nuevo;
    }

    @Transactional(noRollbackFor = BusinessRuleException.class)
    public void cancelar(Long codigoId) {
        CodigoQRAsistencia codigo = obtenerCodigo(codigoId);
        actualizarExpiracion(codigo);
        if (codigo.getEstado() != EstadoCodigoQR.VIGENTE) {
            throw new BusinessRuleException("QR_NO_CANCELABLE", "Solo se puede cancelar un código QR vigente");
        }
        codigo.setEstado(EstadoCodigoQR.INVALIDADO);
        codigo.setFechaHoraCancelacion(LocalDateTime.now(reloj));
        codigoRepository.save(codigo);
        auditoria.registrar("CANCELAR_QR_ASISTENCIA", ModuloAuditoria.ASISTENCIA, "CodigoQRAsistencia",
                codigoId.toString(), ResultadoAuditoria.EXITOSO, null, null, null);
    }

    /**
     * El docente que escanea el código es quien marca: se identifica por su sesión y no por el
     * código, que es el mismo para todos mientras esté vigente.
     */
    @Transactional(noRollbackFor = BusinessRuleException.class)
    public AsistenciaDocenteResponse marcarConToken(String token) {
        CodigoQRAsistencia codigo = codigoRepository.findLockedByTokenHashAndActivoTrue(hashToken(token))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "QR_NO_ENCONTRADO", "El código QR no existe o no es válido"));
        actualizarExpiracion(codigo);
        if (codigo.getEstado() == EstadoCodigoQR.EXPIRADO) {
            throw new BusinessRuleException("QR_EXPIRADO", "El código QR ya expiró; escanee el que se muestra ahora");
        }
        if (codigo.getEstado() != EstadoCodigoQR.VIGENTE) {
            throw new BusinessRuleException("QR_NO_VIGENTE", "El código QR fue reemplazado o cancelado");
        }
        Docente docente = docenteDeLaSesion();

        LocalDateTime ahora = LocalDateTime.now(reloj);
        LocalDate jornada = ahora.toLocalDate();
        if (asistenciaRepository.existsByDocenteIdAndFechaJornadaAndActivoTrue(docente.getId(), jornada)) {
            throw new ConflictException(
                    "ASISTENCIA_DOCENTE_DUPLICADA", "El docente ya registró asistencia para la jornada actual");
        }

        AsistenciaDocente asistencia = new AsistenciaDocente();
        asistencia.setDocente(docente);
        asistencia.setCodigoQR(codigo);
        asistencia.setFechaJornada(jornada);
        asistencia.setFechaHoraMarcada(ahora);
        asistencia.setEstado(EstadoAsistencia.PRESENTE);
        asistencia.setContingencia(false);
        asistencia.setActivo(true);
        cruzarConSesion(asistencia);

        AsistenciaDocenteResponse response = mapResponse(asistenciaRepository.save(asistencia));
        auditoria.registrar("MARCAR_ASISTENCIA_DOCENTE", ModuloAuditoria.ASISTENCIA, "AsistenciaDocente",
                response.id().toString(), ResultadoAuditoria.EXITOSO, null, null,
                "docente=" + docente.getId() + ",estado=PRESENTE,verificacion=" + asistencia.getVerificacion());
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
        auditoria.registrar("REGISTRAR_CONTINGENCIA_DOCENTE", ModuloAuditoria.ASISTENCIA, "AsistenciaDocente",
                response.id().toString(), ResultadoAuditoria.EXITOSO, null, null,
                "docente=" + docente.getId() + ",estado=" + request.estado() + ",motivo=" + request.motivo().trim());
        return response;
    }

    public Page<AsistenciaDocenteResponse> listar(
            Long docenteId,
            Long personaDocenteId,
            EstadoVerificacionClase verificacion,
            LocalDate fechaJornada,
            Pageable pageable
    ) {
        return asistenciaRepository.buscarVisibles(docenteId, personaDocenteId, verificacion, fechaJornada, pageable)
                .map(this::mapResponse);
    }

    @Transactional
    public CodigoQRResponse consultarCodigo(Long codigoId) {
        CodigoQRAsistencia codigo = obtenerCodigo(codigoId);
        actualizarExpiracion(codigo);
        return new CodigoQRResponse(
                codigo.getId(),
                null,
                codigo.getFechaHoraEmision(),
                codigo.getFechaHoraExpiracion(),
                codigo.getEstado());
    }

    public ResumenMarcacionResponse resumen(LocalDate fecha) {
        LocalDate dia = fecha == null ? LocalDate.now(reloj) : fecha;
        long totalDocentes = docenteRepository.countByActivoTrue();
        long marcaron = asistenciaRepository.countByFechaJornadaAndActivoTrue(dia);
        long pendientes = asistenciaRepository.countByFechaJornadaAndVerificacionAndActivoTrue(
                dia, EstadoVerificacionClase.OBSERVADA);
        return new ResumenMarcacionResponse(dia, totalDocentes, marcaron, pendientes,
                Math.max(0, totalDocentes - marcaron));
    }

    /** Clases del docente en la fecha y si ya tienen la lista de alumnos registrada. */
    public List<ClaseDiaDocenteResponse> clasesDelDia(Long docenteId, LocalDate fecha) {
        Docente docente = buscarActivo(Docente.class, docenteId, "DOCENTE_NO_ENCONTRADO", "docente");
        accessGuard.verificarDocentePropietario(docente);
        LocalDate dia = fecha == null ? LocalDate.now(reloj) : fecha;
        return sesionRepository
                .findAllByAsignacionCursoDocenteIdAndFechaAndActivoTrueOrderByHoraInicioAsc(docenteId, dia)
                .stream()
                .map(sesion -> new ClaseDiaDocenteResponse(
                        sesion.getId(),
                        sesion.getAsignacionCurso().getCurso().getNombre(),
                        sesion.getAsignacionCurso().getSeccion().getGrado() + "° "
                                + sesion.getAsignacionCurso().getSeccion().getNombre(),
                        sesion.getHoraInicio(),
                        sesion.getHoraFin(),
                        sesion.getEstado()))
                .toList();
    }

    private Docente docenteDeLaSesion() {
        Long personaId = accessGuard.restriccionDocentePersonaId();
        if (personaId == null) {
            throw new BusinessRuleException(
                    "MARCACION_SOLO_DOCENTE", "Solo un docente puede registrar su asistencia con el código QR");
        }
        return docenteRepository.findByPersonaIdAndActivoTrue(personaId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "DOCENTE_NO_ENCONTRADO", "Su usuario no tiene un docente activo asociado"));
    }

    private CodigoQRResponse crearCodigo(Integer vigenciaSolicitada) {
        int vigencia = vigenciaSolicitada == null ? vigenciaPredeterminadaSegundos : vigenciaSolicitada;
        if (vigencia < 30 || vigencia > 900) {
            throw new BusinessRuleException("QR_VIGENCIA_INVALIDA", "La vigencia debe estar entre 30 y 900 segundos");
        }
        byte[] aleatorio = new byte[32];
        secureRandom.nextBytes(aleatorio);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(aleatorio);
        LocalDateTime ahora = LocalDateTime.now(reloj);

        CodigoQRAsistencia codigo = new CodigoQRAsistencia();
        codigo.setTokenHash(hashToken(token));
        codigo.setFechaHoraEmision(ahora);
        codigo.setFechaHoraExpiracion(ahora.plusSeconds(vigencia));
        codigo.setEstado(EstadoCodigoQR.VIGENTE);
        codigo.setActivo(true);
        codigo = codigoRepository.save(codigo);
        return new CodigoQRResponse(
                codigo.getId(),
                token,
                codigo.getFechaHoraEmision(),
                codigo.getFechaHoraExpiracion(),
                codigo.getEstado());
    }

    private void invalidarCodigosVigentes() {
        LocalDateTime ahora = LocalDateTime.now(reloj);
        codigoRepository.invalidarVigentes(ahora, EstadoCodigoQR.VIGENTE, EstadoCodigoQR.INVALIDADO);
        codigoRepository.expirarVencidos(ahora, EstadoCodigoQR.VIGENTE, EstadoCodigoQR.EXPIRADO);
    }

    /**
     * Escanear el QR no basta para confirmar la clase: la marcación queda observada hasta que el
     * docente finaliza la lista de alumnos de la sesión. Sin clase programada no hay nada que cruzar.
     */
    private void cruzarConSesion(AsistenciaDocente asistencia) {
        List<SesionClase> sesiones = sesionRepository
                .findAllByAsignacionCursoDocenteIdAndFechaAndActivoTrueOrderByHoraInicioAsc(
                        asistencia.getDocente().getId(), asistencia.getFechaJornada());
        LocalTime horaMarcada = asistencia.getFechaHoraMarcada().toLocalTime();
        sesiones.stream()
                .min(Comparator.comparingLong(sesion -> distanciaEnSegundos(sesion, horaMarcada)))
                .ifPresent(sesion -> {
                    asistencia.setSesionClase(sesion);
                    asistencia.setVerificacion(sesion.getEstado() == EstadoSesionClase.TOMADA
                            ? EstadoVerificacionClase.CONFIRMADA
                            : EstadoVerificacionClase.OBSERVADA);
                });
    }

    /** Cero si la hora cae dentro de la sesión; si no, cuánto falta para que empiece o termine. */
    private long distanciaEnSegundos(SesionClase sesion, LocalTime hora) {
        if (hora.isBefore(sesion.getHoraInicio())) {
            return java.time.Duration.between(hora, sesion.getHoraInicio()).getSeconds();
        }
        if (hora.isAfter(sesion.getHoraFin())) {
            return java.time.Duration.between(sesion.getHoraFin(), hora).getSeconds();
        }
        return 0;
    }

    private void actualizarExpiracion(CodigoQRAsistencia codigo) {
        if (codigo.getEstado() == EstadoCodigoQR.VIGENTE
                && !LocalDateTime.now(reloj).isBefore(codigo.getFechaHoraExpiracion())) {
            codigo.setEstado(EstadoCodigoQR.EXPIRADO);
            codigoRepository.save(codigo);
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
                asistencia.getVersion(),
                asistencia.getSesionClase() == null ? null : asistencia.getSesionClase().getId(),
                asistencia.getVerificacion(),
                asistencia.getDocente().getPersona().nombreCompleto());
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
