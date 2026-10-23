package com.asiscontrol.service;

import com.asiscontrol.dto.academico.AcademicoDtos.ActualizarHorarioRequest;
import com.asiscontrol.dto.academico.AcademicoDtos.CrearHorarioRequest;
import com.asiscontrol.dto.academico.AcademicoDtos.HorarioResponse;
import com.asiscontrol.entity.AsignacionCurso;
import com.asiscontrol.entity.Aula;
import com.asiscontrol.entity.Horario;
import com.asiscontrol.entity.enums.DiaSemana;
import com.asiscontrol.entity.enums.ResultadoAuditoria;
import com.asiscontrol.exception.BusinessRuleException;
import com.asiscontrol.exception.ConflictException;
import com.asiscontrol.exception.ResourceNotFoundException;
import com.asiscontrol.repository.AsignacionCursoRepository;
import com.asiscontrol.repository.AulaRepository;
import com.asiscontrol.repository.HorarioRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class HorarioService {

    private final HorarioRepository horarioRepository;
    private final AsignacionCursoRepository asignacionRepository;
    private final AulaRepository aulaRepository;
    private final AuditoriaService auditoria;

    public HorarioService(
            HorarioRepository horarioRepository,
            AsignacionCursoRepository asignacionRepository,
            AulaRepository aulaRepository,
            AuditoriaService auditoria) {
        this.horarioRepository = horarioRepository;
        this.asignacionRepository = asignacionRepository;
        this.aulaRepository = aulaRepository;
        this.auditoria = auditoria;
    }

    @Transactional
    public HorarioResponse crear(CrearHorarioRequest request) {
        AsignacionCurso asignacion = buscarAsignacion(request.asignacionCursoId());
        Aula aula = buscarAula(request.aulaId());
        validar(request.diaSemana(), request.horaInicio(), request.horaFin(), asignacion, aula, null);
        Horario horario = new Horario();
        aplicar(horario, asignacion, aula, request.diaSemana(), request.horaInicio(), request.horaFin());
        horario.setActivo(true);
        Horario saved = horarioRepository.save(horario);
        auditoria.registrar("CREAR_HORARIO", ModuloAuditoria.HORARIOS, "Horario",
                saved.getId().toString(), ResultadoAuditoria.EXITOSO, null, null, describir(saved));
        return mapear(saved);
    }

    @Transactional
    public HorarioResponse actualizar(Long id, ActualizarHorarioRequest request) {
        Horario horario = obtenerEntidad(id);
        exigirVersion(horario.getVersion(), request.version());
        AsignacionCurso asignacion = buscarAsignacion(request.asignacionCursoId());
        Aula aula = buscarAula(request.aulaId());
        validar(request.diaSemana(), request.horaInicio(), request.horaFin(), asignacion, aula, id);
        String anterior = describir(horario);
        aplicar(horario, asignacion, aula, request.diaSemana(), request.horaInicio(), request.horaFin());
        Horario saved = horarioRepository.save(horario);
        auditoria.registrar("ACTUALIZAR_HORARIO", ModuloAuditoria.HORARIOS, "Horario",
                id.toString(), ResultadoAuditoria.EXITOSO, null, anterior, describir(saved));
        return mapear(saved);
    }

    public HorarioResponse obtener(Long id) {
        return mapear(obtenerEntidad(id));
    }

    public Page<HorarioResponse> listar(Pageable pageable) {
        return horarioRepository.findByActivoTrue(pageable).map(this::mapear);
    }

    @Transactional
    public void eliminar(Long id) {
        Horario horario = obtenerEntidad(id);
        horario.setActivo(false);
        horarioRepository.save(horario);
        auditoria.registrar("ELIMINAR_HORARIO", ModuloAuditoria.HORARIOS, "Horario",
                id.toString(), ResultadoAuditoria.EXITOSO, null, describir(horario), null);
    }

    private String describir(Horario horario) {
        return "asignacionCursoId=" + horario.getAsignacionCurso().getId()
                + ",aulaId=" + horario.getAula().getId()
                + ",dia=" + horario.getDiaSemana()
                + ",horaInicio=" + horario.getHoraInicio()
                + ",horaFin=" + horario.getHoraFin();
    }

    private Horario obtenerEntidad(Long id) {
        return horarioRepository.findByIdAndActivoTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "HORARIO_NO_ENCONTRADO", "No existe un horario activo con id " + id));
    }

    private AsignacionCurso buscarAsignacion(Long id) {
        return asignacionRepository.findByIdAndActivoTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "ASIGNACION_CURSO_NO_ENCONTRADA",
                        "No existe una asignacion de curso activa con id " + id));
    }

    private Aula buscarAula(Long id) {
        return aulaRepository.findByIdAndActivoTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "AULA_NO_ENCONTRADA", "No existe un aula activa con id " + id));
    }

    private void validar(
            DiaSemana dia,
            LocalTime inicio,
            LocalTime fin,
            AsignacionCurso asignacion,
            Aula aula,
            Long idExcluido) {
        if (!inicio.isBefore(fin)) {
            throw new BusinessRuleException(
                    "HORARIO_RANGO_INVALIDO", "La hora de inicio debe ser anterior a la hora de fin");
        }
        if (aula.getCapacidad() < asignacion.getSeccion().getCapacidadMaxima()) {
            throw new BusinessRuleException(
                    "AULA_CAPACIDAD_INSUFICIENTE",
                    "El aula no tiene capacidad para la seccion asignada");
        }
        List<Horario> conflictos = horarioRepository.buscarConflictos(
                dia, inicio, fin, aula.getId(), asignacion.getDocente().getId(),
                asignacion.getSeccion().getId(), idExcluido);
        for (Horario conflicto : conflictos) {
            if (conflicto.getAula().getId().equals(aula.getId())) {
                throw new ConflictException(
                        "HORARIO_AULA_OCUPADA", "El aula ya esta ocupada en ese horario");
            }
            if (conflicto.getAsignacionCurso().getDocente().getId()
                    .equals(asignacion.getDocente().getId())) {
                throw new ConflictException(
                        "HORARIO_DOCENTE_OCUPADO", "El docente ya tiene una clase en ese horario");
            }
            throw new ConflictException(
                    "HORARIO_SECCION_OCUPADA", "La seccion ya tiene una clase en ese horario");
        }
    }

    private void aplicar(
            Horario horario,
            AsignacionCurso asignacion,
            Aula aula,
            DiaSemana dia,
            LocalTime inicio,
            LocalTime fin) {
        horario.setAsignacionCurso(asignacion);
        horario.setAula(aula);
        horario.setDiaSemana(dia);
        horario.setHoraInicio(inicio);
        horario.setHoraFin(fin);
    }

    private HorarioResponse mapear(Horario horario) {
        AsignacionCurso asignacion = horario.getAsignacionCurso();
        return new HorarioResponse(
                horario.getId(), asignacion.getId(), asignacion.getCurso().getId(),
                asignacion.getCurso().getNombre(), asignacion.getSeccion().getId(),
                asignacion.getSeccion().getNombre(), asignacion.getDocente().getId(),
                asignacion.getDocente().getPersona().nombreCompleto(), horario.getAula().getId(),
                horario.getAula().getNombre(), horario.getDiaSemana(), horario.getHoraInicio(),
                horario.getHoraFin(), horario.isActivo(), horario.getVersion());
    }

    private void exigirVersion(long actual, Long recibida) {
        if (recibida == null || actual != recibida) {
            throw new ConflictException(
                    "VERSION_DESACTUALIZADA", "El horario fue modificado; vuelva a cargarlo");
        }
    }
}
