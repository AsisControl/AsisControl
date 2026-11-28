package com.asiscontrol.service;

import com.asiscontrol.dto.EvidenciaPedagogicaDto.Adjunto;
import com.asiscontrol.dto.EvidenciaPedagogicaDto.Crear;
import com.asiscontrol.dto.EvidenciaPedagogicaDto.Editar;
import com.asiscontrol.dto.EvidenciaPedagogicaDto.Estudiante;
import com.asiscontrol.dto.EvidenciaPedagogicaDto.EvaluacionVinculada;
import com.asiscontrol.dto.EvidenciaPedagogicaDto.Respuesta;
import com.asiscontrol.dto.PageResponse;
import com.asiscontrol.entity.ArchivoAdjunto;
import com.asiscontrol.entity.AsignacionCurso;
import com.asiscontrol.entity.Evaluacion;
import com.asiscontrol.entity.EvidenciaPedagogica;
import com.asiscontrol.entity.enums.EstadoMatricula;
import com.asiscontrol.entity.enums.ResultadoAuditoria;
import com.asiscontrol.exception.BusinessRuleException;
import com.asiscontrol.exception.ConflictException;
import com.asiscontrol.exception.ResourceNotFoundException;
import com.asiscontrol.repository.AlumnoRepository;
import com.asiscontrol.repository.AsignacionCursoRepository;
import com.asiscontrol.repository.EvaluacionRepository;
import com.asiscontrol.repository.EvidenciaPedagogicaRepository;
import com.asiscontrol.repository.MatriculaRepository;
import com.asiscontrol.repository.planificacion.PeriodoAcademicoRepository;
import java.util.Comparator;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class EvidenciaPedagogicaService {

    private final EvidenciaPedagogicaRepository evidencias;
    private final AsignacionCursoRepository asignaciones;
    private final AlumnoRepository alumnos;
    private final MatriculaRepository matriculas;
    private final PlanEstudiosService planes;
    private final PeriodoAcademicoRepository periodos;
    private final EvaluacionRepository evaluaciones;
    private final ArchivoStorageService archivos;
    private final ModuloAccessGuard acceso;
    private final AuditoriaService auditoria;

    public EvidenciaPedagogicaService(
            EvidenciaPedagogicaRepository evidencias, AsignacionCursoRepository asignaciones,
            AlumnoRepository alumnos, MatriculaRepository matriculas,
            PlanEstudiosService planes, PeriodoAcademicoRepository periodos,
            EvaluacionRepository evaluaciones, ArchivoStorageService archivos,
            ModuloAccessGuard acceso, AuditoriaService auditoria) {
        this.evidencias = evidencias;
        this.asignaciones = asignaciones;
        this.alumnos = alumnos;
        this.matriculas = matriculas;
        this.planes = planes;
        this.periodos = periodos;
        this.evaluaciones = evaluaciones;
        this.archivos = archivos;
        this.acceso = acceso;
        this.auditoria = auditoria;
    }

    @Transactional
    public Respuesta crear(Crear request) {
        AsignacionCurso asignacion = asignacionAutorizada(request.asignacionId());
        var alumno = alumnos.findByIdAndActivoTrue(request.alumnoId())
                .filter(item -> item.getPersona().isActivo())
                .orElseThrow(() -> new ResourceNotFoundException("ALUMNO_NO_ENCONTRADO", "El estudiante no está activo o no existe"));
        if (!matriculas.existsByAlumnoIdAndSeccionIdAndEstadoAndActivoTrue(
                alumno.getId(), asignacion.getSeccion().getId(), EstadoMatricula.ACTIVA)) {
            throw new BusinessRuleException("EVIDENCIA_ALUMNO_FUERA_DE_SECCION",
                    "El estudiante debe tener matrícula activa en la sección de la asignación");
        }
        var competencia = planes.competenciasDeAsignacion(asignacion).stream()
                .filter(item -> item.getId().equals(request.competenciaId())).findFirst()
                .orElseThrow(() -> new BusinessRuleException("EVIDENCIA_COMPETENCIA_AJENA",
                        "La competencia debe estar activa y pertenecer al plan del curso"));
        var periodo = periodos.findById(request.periodoId())
                .filter(p -> p.isActivo() && p.getAnioAcademico().isActivo()
                        && p.getAnioAcademico().getAnio() == asignacion.getSeccion().getAnioAcademico())
                .orElseThrow(() -> new BusinessRuleException("EVIDENCIA_PERIODO_INVALIDO",
                        "El período debe pertenecer al año académico de la sección"));

        var evidencia = new EvidenciaPedagogica();
        evidencia.setAsignacion(asignacion);
        evidencia.setAlumno(alumno);
        evidencia.setCompetencia(competencia);
        evidencia.setPeriodo(periodo);
        evidencia.setAutor(asignacion.getDocente().getPersona());
        asignarContenido(evidencia, request.descripcion(), request.archivoId());
        var guardada = evidencias.save(evidencia);
        registrar("CREAR_EVIDENCIA_PEDAGOGICA", guardada);
        return respuesta(guardada);
    }

    public List<Estudiante> listarEstudiantes(Long asignacionId) {
        var seccion = asignacionAutorizada(asignacionId).getSeccion();
        return matriculas.listarPadronDeSeccion(seccion.getId(), List.of(EstadoMatricula.ACTIVA),
                        seccion.getAnioAcademico(), true)
                .stream().map(alumno -> new Estudiante(alumno.getId(), alumno.getPersona().nombreCompleto()))
                .toList();
    }

    public PageResponse<Respuesta> listar(Long asignacionId, Long alumnoId, Long competenciaId,
                                 Long periodoId, Pageable pagina) {
        asignacionAutorizada(asignacionId);
        return PageResponse.from(evidencias.buscar(asignacionId, alumnoId, competenciaId, periodoId, pagina),
                this::respuesta);
    }

    public Respuesta obtener(Long id) {
        var evidencia = evidencias.findByIdAndActivoTrue(id).orElseThrow(this::noEncontrada);
        verificarAsignacion(evidencia.getAsignacion());
        return respuesta(evidencia);
    }

    @Transactional
    public Respuesta editar(Long id, Editar request) {
        var evidencia = paraCambio(id);
        exigirSinEvaluaciones(evidencia);
        asignarContenido(evidencia, request.descripcion(), request.archivoId());
        evidencias.flush();
        registrar("ACTUALIZAR_EVIDENCIA_PEDAGOGICA", evidencia);
        return respuesta(evidencia);
    }

    @Transactional
    public void inactivar(Long id) {
        var evidencia = paraCambio(id);
        exigirSinEvaluaciones(evidencia);
        evidencia.setActivo(false);
        registrar("INACTIVAR_EVIDENCIA_PEDAGOGICA", evidencia);
    }

    @Transactional
    public void vincularEvaluacion(Long id, Long evaluacionId) {
        var evidencia = paraCambio(id);
        var evaluacion = evaluaciones.findByIdAndActivoTrue(evaluacionId)
                .orElseThrow(() -> new ResourceNotFoundException("EVALUACION_NO_ENCONTRADA", "La evaluación no está activa o no existe"));
        var fecha = evaluacion.getFechaEvaluacion();
        var periodo = evidencia.getPeriodo();
        if (!evaluacion.getAsignacionCurso().getId().equals(evidencia.getAsignacion().getId())
                || evaluacion.getCompetencias().stream().noneMatch(c -> c.getId().equals(evidencia.getCompetencia().getId()))
                || fecha.isBefore(periodo.getFechaInicio()) || fecha.isAfter(periodo.getFechaFin())) {
            throw new BusinessRuleException("EVIDENCIA_EVALUACION_INCOMPATIBLE",
                    "La evaluación debe corresponder a la asignación, competencia y fechas del período");
        }
        if (evidencia.getEvaluaciones().add(evaluacion)) {
            registrar("VINCULAR_EVIDENCIA_EVALUACION", evidencia);
        }
    }

    private AsignacionCurso asignacionAutorizada(Long id) {
        var asignacion = asignaciones.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ASIGNACION_NO_ENCONTRADA", "No existe la asignación indicada"));
        verificarAsignacion(asignacion);
        return asignacion;
    }

    private void verificarAsignacion(AsignacionCurso asignacion) {
        acceso.verificarDocenteTitular(asignacion);
        if (!asignacion.isActivo() || !asignacion.getDocente().isActivo()) {
            throw new AccessDeniedException("La asignación y el perfil docente deben estar activos");
        }
    }

    private EvidenciaPedagogica paraCambio(Long id) {
        var evidencia = evidencias.buscarParaCambio(id).orElseThrow(this::noEncontrada);
        verificarAsignacion(evidencia.getAsignacion());
        return evidencia;
    }

    private ResourceNotFoundException noEncontrada() {
        return new ResourceNotFoundException("EVIDENCIA_NO_ENCONTRADA", "La evidencia no está activa o no existe");
    }

    private void exigirSinEvaluaciones(EvidenciaPedagogica evidencia) {
        if (!evidencia.getEvaluaciones().isEmpty()) {
            throw new ConflictException("EVIDENCIA_USADA_EN_EVALUACION",
                    "La evidencia vinculada a una evaluación debe conservarse");
        }
    }

    private void asignarContenido(EvidenciaPedagogica evidencia, String descripcion, Long archivoId) {
        String texto = descripcion == null ? "" : descripcion.strip();
        if (texto.isEmpty() && archivoId == null) {
            throw new BusinessRuleException("EVIDENCIA_SIN_CONTENIDO", "Indica una descripción o adjunta un archivo");
        }
        ArchivoAdjunto archivo = null;
        if (archivoId != null) {
            archivo = archivos.obtenerActivoParaVincular(archivoId);
            if (!archivo.getCargadoPor().getId().equals(acceso.personaIdActual())) {
                throw new AccessDeniedException("Solo puede adjuntar archivos cargados por usted");
            }
        }
        evidencia.setDescripcion(texto.isEmpty() ? null : texto);
        evidencia.setArchivo(archivo);
    }

    private void registrar(String accion, EvidenciaPedagogica evidencia) {
        auditoria.registrar(accion, ModuloAuditoria.ACADEMICO, "EvidenciaPedagogica",
                evidencia.getId().toString(), ResultadoAuditoria.EXITOSO, null, null, null);
    }

    private Respuesta respuesta(EvidenciaPedagogica evidencia) {
        ArchivoAdjunto archivo = evidencia.getArchivo();
        var adjunto = archivo == null ? null : new Adjunto(archivo.getId(), archivo.getNombreOriginal(),
                archivo.getTipoMime(), archivo.getTamanioBytes());
        return new Respuesta(evidencia.getId(), evidencia.getAsignacion().getId(), evidencia.getAlumno().getId(),
                evidencia.getAsignacion().getCurso().getId(), evidencia.getCompetencia().getId(),
                evidencia.getCompetencia().getCodigo(), evidencia.getCompetencia().getNombre(),
                evidencia.getPeriodo().getId(), evidencia.getAutor().getId(), evidencia.getDescripcion(),
                adjunto, evidencia.getFechaCreacion(), evidencia.getFechaActualizacion(),
                evidencia.getEvaluaciones().stream().sorted(Comparator.comparing(Evaluacion::getId))
                        .map(e -> new EvaluacionVinculada(e.getId(), e.getTitulo())).toList());
    }
}
