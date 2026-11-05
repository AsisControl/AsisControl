// asistencia alumnos
package com.asiscontrol.service.asistencia;

import com.asiscontrol.dto.asistencia.AsistenciaSesionDtos.AlumnoAsistenciaResponse;
import com.asiscontrol.dto.asistencia.AsistenciaSesionDtos.CambioAsistenciaResponse;
import com.asiscontrol.dto.asistencia.AsistenciaSesionDtos.CorregirAsistenciaRequest;
import com.asiscontrol.dto.asistencia.AsistenciaSesionDtos.CorreccionAsistenciaResponse;
import com.asiscontrol.dto.asistencia.AsistenciaSesionDtos.FinalizarAsistenciaRequest;
import com.asiscontrol.dto.asistencia.AsistenciaSesionDtos.GuardarBorradorRequest;
import com.asiscontrol.dto.asistencia.AsistenciaSesionDtos.MarcaAsistenciaRequest;
import com.asiscontrol.dto.asistencia.AsistenciaSesionDtos.SesionAsistenciaResponse;
import com.asiscontrol.entity.Alumno;
import com.asiscontrol.entity.AsignacionCurso;
import com.asiscontrol.entity.AsistenciaAlumno;
import com.asiscontrol.entity.JustificacionAsistencia;
import com.asiscontrol.entity.asistencia.CorreccionAsistencia;
import com.asiscontrol.entity.asistencia.CorreccionAsistenciaDetalle;
import com.asiscontrol.entity.asistencia.EstadoSesionClase;
import com.asiscontrol.entity.asistencia.SesionClase;
import com.asiscontrol.entity.enums.EstadoAsistencia;
import com.asiscontrol.entity.enums.ResultadoAuditoria;
import com.asiscontrol.exception.BusinessRuleException;
import com.asiscontrol.exception.ConflictException;
import com.asiscontrol.exception.ResourceNotFoundException;
import com.asiscontrol.repository.AsistenciaAlumnoRepository;
import com.asiscontrol.repository.JustificacionAsistenciaRepository;
import com.asiscontrol.repository.asistencia.CorreccionAsistenciaDetalleRepository;
import com.asiscontrol.repository.asistencia.CorreccionAsistenciaRepository;
import com.asiscontrol.repository.asistencia.SesionClaseRepository;
import com.asiscontrol.service.AuditoriaService;
import com.asiscontrol.service.MatriculaService;
import com.asiscontrol.service.ModuloAccessGuard;
import com.asiscontrol.service.ModuloAuditoria;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Toma de asistencia de una sesión de clase: borrador, finalización y corrección de una
 * asistencia TOMADA. Solo el docente asignado escribe (R01); cada operación es una unidad
 * transaccional con su auditoría y bloquea la fila de la sesión para serializar a quien
 * escriba a la vez.
 */
@Service
@Transactional(readOnly = true)
public class AsistenciaSesionService {

    private static final Set<EstadoAsistencia> ESTADOS_PERMITIDOS = Set.of(
            EstadoAsistencia.PRESENTE, EstadoAsistencia.TARDANZA, EstadoAsistencia.INASISTENCIA);

    private final SesionClaseRepository sesionRepository;
    private final AsistenciaAlumnoRepository asistenciaRepository;
    private final CorreccionAsistenciaRepository correccionRepository;
    private final CorreccionAsistenciaDetalleRepository detalleRepository;
    private final JustificacionAsistenciaRepository justificacionRepository;
    private final MatriculaService matriculaService;
    private final AuditoriaService auditoria;
    private final ModuloAccessGuard accessGuard;
    private final CalendarioSesionService calendarioService;
    private final Clock reloj;

    public AsistenciaSesionService(
            SesionClaseRepository sesionRepository,
            AsistenciaAlumnoRepository asistenciaRepository,
            CorreccionAsistenciaRepository correccionRepository,
            CorreccionAsistenciaDetalleRepository detalleRepository,
            JustificacionAsistenciaRepository justificacionRepository,
            MatriculaService matriculaService,
            AuditoriaService auditoria,
            ModuloAccessGuard accessGuard,
            CalendarioSesionService calendarioService,
            Clock reloj) {
        this.sesionRepository = sesionRepository;
        this.asistenciaRepository = asistenciaRepository;
        this.correccionRepository = correccionRepository;
        this.detalleRepository = detalleRepository;
        this.justificacionRepository = justificacionRepository;
        this.matriculaService = matriculaService;
        this.auditoria = auditoria;
        this.accessGuard = accessGuard;
        this.calendarioService = calendarioService;
        this.reloj = reloj;
    }

    /**
     * Estudiantes de la sesión con su estado. Una sesión TOMADA muestra lo registrado; las demás,
     * a los matriculados hoy. Un borrador vencido no muestra sus marcas (vuelve a PENDIENTE).
     */
    public SesionAsistenciaResponse obtener(Long sesionId) {
        SesionClase sesion = buscarSesion(sesionId);
        accessGuard.verificarAsignacionDocente(sesion.getAsignacionCurso());
        calendarioService.exigirAnioDictado(sesion.getAsignacionCurso());
        return mapear(sesion, asistenciaRepository.findAllBySesionClaseId(sesionId));
    }

    @Transactional
    public SesionAsistenciaResponse guardarBorrador(Long sesionId, GuardarBorradorRequest request) {
        SesionClase sesion = bloquearParaEscribir(sesionId, null);
        exigirTomaDelDia(sesion);
        Map<Long, EstadoAsistencia> marcas = validarMarcas(request.marcas());
        if (marcas.isEmpty()) {
            throw new BusinessRuleException(
                    "ASISTENCIA_SIN_MARCAS", "Asigne un estado al menos a un estudiante para guardar");
        }
        Map<Long, Alumno> matriculados = matriculados(sesion);
        exigirMatriculados(marcas, matriculados);

        List<AsistenciaAlumno> filas = reemplazarFilas(sesion, marcas, matriculados);
        sesion.setEstado(EstadoSesionClase.BORRADOR);
        sesionRepository.saveAndFlush(sesion);
        auditoria.registrar("GUARDAR_BORRADOR_ASISTENCIA", ModuloAuditoria.ASISTENCIA, "SesionClase",
                sesionId.toString(), ResultadoAuditoria.EXITOSO, null, null, describir(marcas));
        return mapear(sesion, filas);
    }

    /** Finaliza en una sola transacción; si algo falla no queda una asistencia a medias (R10, R11). */
    @Transactional
    public SesionAsistenciaResponse finalizar(Long sesionId, FinalizarAsistenciaRequest request) {
        SesionClase sesion = bloquearParaEscribir(sesionId, request.version());
        exigirTomaDelDia(sesion);
        Map<Long, EstadoAsistencia> marcas = validarMarcas(request.marcas());
        Map<Long, Alumno> matriculados = matriculados(sesion);
        if (matriculados.isEmpty()) {
            throw new BusinessRuleException(
                    "ASISTENCIA_SIN_ESTUDIANTES", "La sección no tiene estudiantes matriculados");
        }
        exigirMatriculados(marcas, matriculados);
        long pendientes = matriculados.keySet().stream().filter(id -> !marcas.containsKey(id)).count();
        if (pendientes > 0) {
            throw new BusinessRuleException(
                    "ASISTENCIA_INCOMPLETA",
                    "No se puede finalizar la asistencia. Complete el estado de todos los estudiantes. "
                            + "Pendientes de completar: " + pendientes + ".");
        }

        List<AsistenciaAlumno> filas = reemplazarFilas(sesion, marcas, matriculados);
        sesion.setEstado(EstadoSesionClase.TOMADA);
        sesion.setDocenteRegistrador(sesion.getAsignacionCurso().getDocente());
        sesion.setFechaHoraFinalizacion(LocalDateTime.now(reloj));
        sesionRepository.saveAndFlush(sesion);
        auditoria.registrar("FINALIZAR_ASISTENCIA", ModuloAuditoria.ASISTENCIA, "SesionClase",
                sesionId.toString(), ResultadoAuditoria.EXITOSO, null, null, describir(marcas));
        return mapear(sesion, filas);
    }

    /**
     * Corrige una asistencia TOMADA, incluso días después (R12, R13). Registra un detalle por
     * estudiante cambiado ligado al motivo; si nada cambió se rechaza sin crear una corrección.
     * Reenviar el estado base de una marca justificada no es un cambio: conserva la justificación.
     */
    @Transactional
    public SesionAsistenciaResponse corregir(Long sesionId, CorregirAsistenciaRequest request) {
        SesionClase sesion = bloquearParaEscribir(sesionId, request.version());
        if (sesion.getEstado() != EstadoSesionClase.TOMADA) {
            throw new BusinessRuleException(
                    "ASISTENCIA_NO_TOMADA", "Solo se corrige una asistencia finalizada");
        }
        Map<Long, EstadoAsistencia> marcas = validarMarcas(request.marcas());
        Map<Long, AsistenciaAlumno> registradas = asistenciaRepository.findAllBySesionClaseId(sesionId).stream()
                .collect(Collectors.toMap(fila -> fila.getAlumno().getId(), Function.identity()));

        List<CorreccionAsistenciaDetalle> cambios = new ArrayList<>();
        marcas.forEach((alumnoId, estado) -> {
            AsistenciaAlumno fila = registradas.get(alumnoId);
            if (fila == null) {
                throw new BusinessRuleException(
                        "ALUMNO_NO_REGISTRADO_EN_SESION",
                        "El estudiante " + alumnoId + " no figura en esta asistencia");
            }
            if (estadoBase(fila.getEstadoAsistencia()) != estado) {
                CorreccionAsistenciaDetalle detalle = new CorreccionAsistenciaDetalle();
                detalle.setAlumno(fila.getAlumno());
                detalle.setEstadoAnterior(fila.getEstadoAsistencia());
                detalle.setEstadoNuevo(estado);
                cambios.add(detalle);
                fila.setEstadoAsistencia(estado);
            }
        });
        if (cambios.isEmpty()) {
            throw new BusinessRuleException(
                    "ASISTENCIA_SIN_CAMBIOS", "No se modificó ningún estado; no hay corrección que guardar");
        }

        CorreccionAsistencia correccion = new CorreccionAsistencia();
        correccion.setSesionClase(sesion);
        correccion.setDocente(sesion.getAsignacionCurso().getDocente());
        correccion.setMotivo(request.motivo().trim());
        correccion.setFechaHora(LocalDateTime.now(reloj));
        correccion.setActivo(true);
        correccionRepository.save(correccion);
        cambios.forEach(detalle -> detalle.setCorreccionAsistencia(correccion));
        detalleRepository.saveAll(cambios);
        asistenciaRepository.saveAll(registradas.values());
        sesionRepository.saveAndFlush(sesion);

        auditoria.registrar("CORREGIR_ASISTENCIA", ModuloAuditoria.ASISTENCIA, "SesionClase",
                sesionId.toString(), ResultadoAuditoria.EXITOSO, correccion.getMotivo(),
                cambios.stream().map(d -> "alumno=" + d.getAlumno().getId() + ":" + d.getEstadoAnterior())
                        .collect(Collectors.joining(",")),
                cambios.stream().map(d -> "alumno=" + d.getAlumno().getId() + ":" + d.getEstadoNuevo())
                        .collect(Collectors.joining(",")));
        return mapear(sesion, new ArrayList<>(registradas.values()));
    }

    public List<CorreccionAsistenciaResponse> listarCorrecciones(Long sesionId) {
        SesionClase sesion = buscarSesion(sesionId);
        accessGuard.verificarAsignacionDocente(sesion.getAsignacionCurso());
        calendarioService.exigirAnioDictado(sesion.getAsignacionCurso());
        List<CorreccionAsistencia> correcciones = correccionRepository.findAllBySesionClaseIdOrderByFechaHoraDesc(sesionId);
        Map<Long, List<CorreccionAsistenciaDetalle>> detalles = detalleRepository
                .findAllByCorreccionAsistenciaIdIn(correcciones.stream().map(CorreccionAsistencia::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(d -> d.getCorreccionAsistencia().getId()));
        return correcciones.stream().map(c -> new CorreccionAsistenciaResponse(
                c.getId(),
                c.getFechaHora(),
                c.getDocente().getId(),
                c.getDocente().getPersona().nombreCompleto(),
                c.getMotivo(),
                detalles.getOrDefault(c.getId(), List.of()).stream().map(d -> new CambioAsistenciaResponse(
                        d.getAlumno().getId(),
                        d.getAlumno().getPersona().nombreCompleto(),
                        d.getEstadoAnterior(),
                        d.getEstadoNuevo())).toList()))
                .toList();
    }

    /** Vencimiento del borrador (R08): borra sus marcas y devuelve la sesión a PENDIENTE (R09). */
    @Transactional
    public int vencerBorradores() {
        List<SesionClase> vencidos = sesionRepository.findAllByEstadoAndFechaBefore(
                EstadoSesionClase.BORRADOR, LocalDate.now(reloj));
        for (SesionClase sesion : vencidos) {
            asistenciaRepository.deleteAll(asistenciaRepository.findAllBySesionClaseId(sesion.getId()));
            sesion.setEstado(EstadoSesionClase.PENDIENTE);
        }
        sesionRepository.saveAll(vencidos);
        return vencidos.size();
    }

    private SesionClase buscarSesion(Long sesionId) {
        return sesionRepository.findByIdAndActivoTrue(sesionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "SESION_CLASE_NO_ENCONTRADA", "No existe una sesión de clase activa con id " + sesionId));
    }

    /**
     * Bloquea la fila de la sesión y, si se envía, comprueba la versión que vio el docente. La
     * versión sube en cada escritura, también cuando solo cambian las filas de los estudiantes.
     */
    private SesionClase bloquearParaEscribir(Long sesionId, Long version) {
        SesionClase sesion = sesionRepository.bloquear(sesionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "SESION_CLASE_NO_ENCONTRADA", "No existe una sesión de clase activa con id " + sesionId));
        accessGuard.verificarDocenteTitular(sesion.getAsignacionCurso());
        calendarioService.exigirAnioDictado(sesion.getAsignacionCurso());
        if (version != null && sesion.getVersion() != version) {
            throw new ConflictException(
                    "VERSION_DESACTUALIZADA", "La asistencia fue modificada en otra pantalla; vuelva a cargarla");
        }
        // Marca la sesión como modificada para que @Version suba aunque solo cambien las filas.
        sesion.setFechaActualizacion(Instant.now(reloj));
        return sesion;
    }

    /** Solo se toma la asistencia del día: ni retroactiva (R15) ni adelantada (R16). */
    private void exigirTomaDelDia(SesionClase sesion) {
        if (!sesion.getFecha().isEqual(LocalDate.now(reloj))) {
            throw new BusinessRuleException(
                    "ASISTENCIA_FUERA_DE_FECHA", "Solo se puede tomar la asistencia el día de la clase");
        }
        if (sesion.getEstado() == EstadoSesionClase.TOMADA) {
            throw new BusinessRuleException(
                    "ASISTENCIA_YA_TOMADA", "La asistencia ya fue finalizada; use la corrección para editarla");
        }
    }

    private Map<Long, EstadoAsistencia> validarMarcas(List<MarcaAsistenciaRequest> marcas) {
        Map<Long, EstadoAsistencia> resultado = new LinkedHashMap<>();
        for (MarcaAsistenciaRequest marca : marcas) {
            if (!ESTADOS_PERMITIDOS.contains(marca.estado())) {
                throw new BusinessRuleException(
                        "ASISTENCIA_ESTADO_NO_PERMITIDO", "Los estados válidos son PRESENTE, TARDANZA e INASISTENCIA");
            }
            if (resultado.put(marca.alumnoId(), marca.estado()) != null) {
                throw new ConflictException(
                        "ASISTENCIA_ALUMNO_DUPLICADO", "El estudiante " + marca.alumnoId() + " figura más de una vez");
            }
        }
        return resultado;
    }

    private Map<Long, Alumno> matriculados(SesionClase sesion) {
        return matriculaService.alumnosConMatriculaActiva(sesion.getAsignacionCurso().getSeccion().getId()).stream()
                .collect(Collectors.toMap(Alumno::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
    }

    private void exigirMatriculados(Map<Long, EstadoAsistencia> marcas, Map<Long, Alumno> matriculados) {
        for (Long alumnoId : marcas.keySet()) {
            if (!matriculados.containsKey(alumnoId)) {
                throw new BusinessRuleException(
                        "ALUMNO_NO_MATRICULADO_EN_SECCION",
                        "El estudiante " + alumnoId + " no tiene una matrícula activa en la sección");
            }
        }
    }

    /** Deja exactamente una fila por estudiante marcado; borra las de quienes ya no tienen estado. */
    private List<AsistenciaAlumno> reemplazarFilas(
            SesionClase sesion, Map<Long, EstadoAsistencia> marcas, Map<Long, Alumno> matriculados) {
        Map<Long, AsistenciaAlumno> existentes = new HashMap<>();
        for (AsistenciaAlumno fila : asistenciaRepository.findAllBySesionClaseId(sesion.getId())) {
            existentes.put(fila.getAlumno().getId(), fila);
        }
        List<AsistenciaAlumno> sobrantes = existentes.entrySet().stream()
                .filter(e -> !marcas.containsKey(e.getKey()))
                .map(Map.Entry::getValue)
                .toList();
        asistenciaRepository.deleteAll(sobrantes);

        LocalTime hora = LocalTime.now(reloj).truncatedTo(ChronoUnit.SECONDS);
        AsignacionCurso asignacion = sesion.getAsignacionCurso();
        List<AsistenciaAlumno> filas = new ArrayList<>();
        marcas.forEach((alumnoId, estado) -> {
            AsistenciaAlumno fila = existentes.get(alumnoId);
            if (fila == null) {
                fila = new AsistenciaAlumno();
                fila.setAlumno(matriculados.get(alumnoId));
                fila.setAsignacionCurso(asignacion);
                fila.setSesionClase(sesion);
                fila.setDocenteRegistrador(asignacion.getDocente());
                fila.setFechaRegistro(sesion.getFecha());
                fila.setActivo(true);
            }
            fila.setHoraRegistro(hora);
            fila.setEstadoAsistencia(estado);
            filas.add(fila);
        });
        return asistenciaRepository.saveAll(filas);
    }

    private SesionAsistenciaResponse mapear(SesionClase sesion, List<AsistenciaAlumno> filas) {
        LocalDate hoy = LocalDate.now(reloj);
        EstadoSesionClase estado = CalendarioSesionService.estadoEfectivo(sesion, hoy);
        boolean vencido = sesion.getEstado() == EstadoSesionClase.BORRADOR && estado == EstadoSesionClase.PENDIENTE;
        Map<Long, AsistenciaAlumno> registradas = vencido ? Map.of() : filas.stream()
                .collect(Collectors.toMap(f -> f.getAlumno().getId(), Function.identity()));
        Map<Long, JustificacionAsistencia> justificaciones = estado != EstadoSesionClase.TOMADA ? Map.of()
                : justificacionRepository
                        .findAllByAsistenciaAlumnoIdInAndActivoTrue(filas.stream().map(AsistenciaAlumno::getId).toList())
                        .stream()
                        .collect(Collectors.toMap(j -> j.getAsistenciaAlumno().getId(), Function.identity()));

        List<Alumno> alumnos = estado == EstadoSesionClase.TOMADA
                ? filas.stream().map(AsistenciaAlumno::getAlumno).toList()
                : matriculaService.alumnosConMatriculaActiva(sesion.getAsignacionCurso().getSeccion().getId());
        List<AlumnoAsistenciaResponse> lista = alumnos.stream()
                .map(alumno -> {
                    AsistenciaAlumno fila = registradas.get(alumno.getId());
                    JustificacionAsistencia justificacion = fila == null ? null : justificaciones.get(fila.getId());
                    return new AlumnoAsistenciaResponse(
                            alumno.getId(),
                            alumno.getCodigoAlumno(),
                            alumno.getPersona().nombreCompleto(),
                            fila == null ? null : estadoBase(fila.getEstadoAsistencia()),
                            fila == null ? null : fila.getId(),
                            justificacion == null ? null : justificacion.getId(),
                            justificacion == null ? null : justificacion.getEstado());
                })
                .sorted(java.util.Comparator.comparing(AlumnoAsistenciaResponse::nombreCompleto))
                .toList();

        AsignacionCurso asignacion = sesion.getAsignacionCurso();
        return new SesionAsistenciaResponse(
                sesion.getId(),
                asignacion.getId(),
                asignacion.getCurso().getNombre(),
                asignacion.getSeccion().getGrado() + "° " + asignacion.getSeccion().getNombre(),
                sesion.getFecha(),
                sesion.getHoraInicio(),
                sesion.getHoraFin(),
                estado,
                sesion.getFecha().isEqual(hoy) && estado != EstadoSesionClase.TOMADA,
                estado == EstadoSesionClase.TOMADA,
                (int) lista.stream().filter(a -> a.estado() == null).count(),
                lista,
                sesion.getDocenteRegistrador() == null
                        ? null : sesion.getDocenteRegistrador().getPersona().nombreCompleto(),
                sesion.getFechaHoraFinalizacion(),
                sesion.getVersion());
    }

    /** Aprobar una justificación cambia la marca a su variante justificada; el docente ve la base. */
    private static EstadoAsistencia estadoBase(EstadoAsistencia estado) {
        return switch (estado) {
            case INASISTENCIA_JUSTIFICADA -> EstadoAsistencia.INASISTENCIA;
            case TARDANZA_JUSTIFICADA -> EstadoAsistencia.TARDANZA;
            default -> estado;
        };
    }

    private String describir(Map<Long, EstadoAsistencia> marcas) {
        return marcas.entrySet().stream()
                .map(e -> "alumno=" + e.getKey() + ":" + e.getValue())
                .collect(Collectors.joining(","));
    }
}
