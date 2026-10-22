package com.asiscontrol.service;

import com.asiscontrol.dto.academico.AcademicoDtos.ActualizarAulaRequest;
import com.asiscontrol.dto.academico.AcademicoDtos.AulaResponse;
import com.asiscontrol.dto.academico.AcademicoDtos.AulaResumenResponse;
import com.asiscontrol.dto.academico.AcademicoDtos.CrearAulaRequest;
import com.asiscontrol.dto.academico.AcademicoDtos.EstadoAula;
import com.asiscontrol.dto.academico.AcademicoDtos.HabilitarAulaRequest;
import com.asiscontrol.dto.academico.AcademicoDtos.HorarioEnUsoAula;
import com.asiscontrol.dto.academico.AcademicoDtos.InhabilitarAulaRequest;
import com.asiscontrol.entity.AsignacionCurso;
import com.asiscontrol.entity.Aula;
import com.asiscontrol.entity.Horario;
import com.asiscontrol.entity.Seccion;
import com.asiscontrol.entity.enums.ResultadoAuditoria;
import com.asiscontrol.exception.BusinessRuleException;
import com.asiscontrol.exception.ConflictException;
import com.asiscontrol.exception.ResourceNotFoundException;
import com.asiscontrol.repository.AulaRepository;
import com.asiscontrol.repository.HorarioRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
@Transactional(readOnly = true)
public class AulaService {

    private static final String MODULO = "ACADEMICO";
    private static final String ENTIDAD = "Aula";

    private final AulaRepository aulaRepository;
    private final HorarioRepository horarioRepository;
    private final AuditoriaService auditoriaService;

    public AulaService(
            AulaRepository aulaRepository,
            HorarioRepository horarioRepository,
            AuditoriaService auditoriaService) {
        this.aulaRepository = aulaRepository;
        this.horarioRepository = horarioRepository;
        this.auditoriaService = auditoriaService;
    }

    /** La capacidad del aula es suficiente si es mayor o igual a la capacidad maxima de la seccion. */
    public static boolean esSuficiente(int capacidadAula, int capacidadMaximaSeccion) {
        return capacidadAula >= capacidadMaximaSeccion;
    }

    @Transactional
    public AulaResponse crear(CrearAulaRequest request) {
        validarCapacidad(request.capacidad());
        validarCodigoUnico(request.codigo(), null);
        Aula aula = new Aula();
        aplicar(aula, request);
        aula.setActivo(true);
        Aula guardada = aulaRepository.save(aula);
        auditar("CREAR_AULA", guardada, null, null, describir(guardada));
        return mapear(guardada);
    }

    @Transactional
    public AulaResponse actualizar(Long id, ActualizarAulaRequest request) {
        validarCapacidad(request.capacidad());
        Aula aula = obtenerEntidad(id);
        exigirVersion(aula.getVersion(), request.version());
        validarCodigoUnico(request.codigo(), id);
        String codigo = normalizarCodigo(request.codigo());
        if (!codigo.equals(aula.getCodigo()) && horarioRepository.existsByAulaId(id)) {
            throw new ConflictException(
                    "AULA_CODIGO_EN_USO", "No se puede cambiar el codigo de un aula utilizada en horarios");
        }
        if (request.capacidad() < aula.getCapacidad()) {
            validarReduccionCapacidad(id, request.capacidad());
        }
        String nombre = request.nombre().trim();
        String ubicacion = normalizarUbicacion(request.ubicacion());
        StringBuilder anterior = new StringBuilder();
        StringBuilder nuevo = new StringBuilder();
        anotarCambio(anterior, nuevo, "codigo", aula.getCodigo(), codigo);
        anotarCambio(anterior, nuevo, "nombre", aula.getNombre(), nombre);
        anotarCambio(anterior, nuevo, "tipoAula", aula.getTipoAula(), request.tipoAula());
        anotarCambio(anterior, nuevo, "ubicacion", aula.getUbicacion(), ubicacion);
        Integer capacidadAnterior = aula.getCapacidad();
        aula.setCodigo(codigo);
        aula.setNombre(nombre);
        aula.setTipoAula(request.tipoAula());
        aula.setCapacidad(request.capacidad());
        aula.setUbicacion(ubicacion);
        Aula guardada = aulaRepository.save(aula);
        if (!nuevo.isEmpty()) {
            auditar("ACTUALIZAR_AULA", guardada, null, anterior.toString(), nuevo.toString());
        }
        if (!capacidadAnterior.equals(request.capacidad())) {
            auditar("CAMBIAR_CAPACIDAD_AULA", guardada, null,
                    "capacidad=" + capacidadAnterior, "capacidad=" + request.capacidad());
        }
        return mapear(guardada);
    }

    @Transactional
    public AulaResponse inhabilitar(Long id, InhabilitarAulaRequest request) {
        String motivo = exigirMotivo(request.motivo());
        Aula aula = obtenerEntidadSinFiltroActivo(id);
        exigirVersion(aula.getVersion(), request.version());
        if (!aula.isActivo()) {
            throw new ConflictException("AULA_YA_INHABILITADA", "El aula ya se encuentra inhabilitada");
        }
        List<HorarioEnUsoAula> enUso = horarioRepository.findActivosPorAulaConSeccion(id).stream()
                .map(this::mapearUso)
                .toList();
        if (!enUso.isEmpty()) {
            throw new ConflictException(
                    "AULA_CON_HORARIOS_ACTIVOS",
                    "No se puede inhabilitar un aula con horarios activos", enUso);
        }
        aula.setActivo(false);
        Aula guardada = aulaRepository.save(aula);
        auditar("INHABILITAR_AULA", guardada, motivo, "activo=true", "activo=false");
        return mapear(guardada);
    }

    @Transactional
    public AulaResponse habilitar(Long id, HabilitarAulaRequest request) {
        Aula aula = obtenerEntidadSinFiltroActivo(id);
        exigirVersion(aula.getVersion(), request.version());
        if (aula.isActivo()) {
            throw new ConflictException("AULA_YA_HABILITADA", "El aula ya se encuentra habilitada");
        }
        aula.setActivo(true);
        Aula guardada = aulaRepository.save(aula);
        String motivo = request.motivo() == null || request.motivo().isBlank() ? null : request.motivo().trim();
        auditar("HABILITAR_AULA", guardada, motivo, "activo=false", "activo=true");
        return mapear(guardada);
    }

    public AulaResponse obtener(Long id) {
        return mapear(obtenerEntidadSinFiltroActivo(id));
    }

    public Page<AulaResponse> listar(Pageable pageable) {
        return listar(null, pageable);
    }

    public Page<AulaResponse> listar(EstadoAula estado, Pageable pageable) {
        if (estado == null || estado == EstadoAula.HABILITADAS) {
            return aulaRepository.findByActivoTrue(pageable).map(this::mapear);
        }
        if (estado == EstadoAula.INHABILITADAS) {
            return aulaRepository.findByActivoFalse(pageable).map(this::mapear);
        }
        return aulaRepository.findAll(pageable).map(this::mapear);
    }

    public AulaResumenResponse resumen() {
        return new AulaResumenResponse(aulaRepository.countByActivoTrue());
    }

    @Transactional
    public void eliminar(Long id) {
        Aula aula = obtenerEntidad(id);
        aula.setActivo(false);
        aulaRepository.save(aula);
    }

    public Aula obtenerEntidad(Long id) {
        return aulaRepository.findByIdAndActivoTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "AULA_NO_ENCONTRADA", "No existe un aula activa con id " + id));
    }

    private Aula obtenerEntidadSinFiltroActivo(Long id) {
        return aulaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "AULA_NO_ENCONTRADA", "No existe un aula con id " + id));
    }

    private void aplicar(Aula aula, CrearAulaRequest request) {
        aula.setCodigo(normalizarCodigo(request.codigo()));
        aula.setNombre(request.nombre().trim());
        aula.setTipoAula(request.tipoAula());
        aula.setCapacidad(request.capacidad());
        aula.setUbicacion(normalizarUbicacion(request.ubicacion()));
    }

    private void validarCapacidad(Integer capacidad) {
        if (capacidad == null || capacidad < 1)
            throw new BusinessRuleException(
                    "AULA_CAPACIDAD_INVALIDA", "La capacidad del aula debe ser un entero positivo");
    }

    private void validarCodigoUnico(String codigo, Long idActual) {
        aulaRepository.findByCodigoIgnoreCase(normalizarCodigo(codigo))
                .filter(existente -> idActual == null || !existente.getId().equals(idActual))
                .ifPresent(existente -> {
                    throw new ConflictException("AULA_CODIGO_DUPLICADO", "Ya existe un aula con ese codigo");
                });
    }

    private String normalizarCodigo(String codigo) {
        return codigo.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    private String normalizarUbicacion(String ubicacion) {
        // La columna sigue siendo NOT NULL; "" representa sin ubicacion hasta V2.
        return ubicacion == null || ubicacion.isBlank() ? "" : ubicacion.trim();
    }

    private AulaResponse mapear(Aula aula) {
        return new AulaResponse(
                aula.getId(), aula.getCodigo(), aula.getNombre(), aula.getTipoAula(), aula.getCapacidad(),
                aula.getUbicacion(), aula.isActivo(), aula.getVersion());
    }

    private void validarReduccionCapacidad(Long aulaId, int nuevaCapacidad) {
        // Sin fecha en Horario: todo horario activo cuenta como asignacion activa o futura.
        List<HorarioEnUsoAula> incompatibles = horarioRepository.findActivosPorAulaConSeccion(aulaId).stream()
                .filter(h -> !esSuficiente(nuevaCapacidad, h.getAsignacionCurso().getSeccion().getCapacidadMaxima()))
                .map(this::mapearUso)
                .toList();
        if (!incompatibles.isEmpty()) {
            throw new ConflictException(
                    "AULA_CAPACIDAD_INSUFICIENTE",
                    "La nueva capacidad es menor que la capacidad maxima de secciones asignadas al aula",
                    incompatibles);
        }
    }

    private String exigirMotivo(String motivo) {
        if (motivo == null || motivo.isBlank()) {
            throw new BusinessRuleException("MOTIVO_REQUERIDO", "Indique el motivo del cambio de estado");
        }
        return motivo.trim();
    }

    private HorarioEnUsoAula mapearUso(Horario horario) {
        AsignacionCurso asignacion = horario.getAsignacionCurso();
        Seccion seccion = asignacion.getSeccion();
        return new HorarioEnUsoAula(
                horario.getId(), asignacion.getId(), horario.getDiaSemana(), horario.getHoraInicio(),
                horario.getHoraFin(), seccion.getId(), seccion.getNombre(), seccion.getGrado(),
                asignacion.getCurso().getNombre(), seccion.getCapacidadMaxima());
    }

    private void auditar(String accion, Aula aula, String motivo, String valorAnterior, String nuevoValor) {
        // Misma transaccion que la operacion: si esta se revierte, no queda registro.
        auditoriaService.registrar(
                accion, MODULO, ENTIDAD, aula.getId().toString(), ResultadoAuditoria.EXITOSO,
                motivo, valorAnterior, nuevoValor);
    }

    private String describir(Aula aula) {
        return "codigo=" + aula.getCodigo()
                + ",nombre=" + aula.getNombre()
                + ",tipoAula=" + aula.getTipoAula()
                + ",capacidad=" + aula.getCapacidad()
                + ",ubicacion=" + aula.getUbicacion()
                + ",activo=" + aula.isActivo();
    }

    private void anotarCambio(StringBuilder anterior, StringBuilder nuevo, String campo, Object antes, Object despues) {
        if (Objects.equals(antes, despues)) {
            return;
        }
        if (!nuevo.isEmpty()) {
            anterior.append(',');
            nuevo.append(',');
        }
        anterior.append(campo).append('=').append(antes);
        nuevo.append(campo).append('=').append(despues);
    }

    private void exigirVersion(long actual, Long recibida) {
        if (recibida == null || actual != recibida) {
            throw new ConflictException(
                    "VERSION_DESACTUALIZADA", "El aula fue modificada; vuelva a cargarla");
        }
    }
}
