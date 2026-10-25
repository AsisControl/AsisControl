package com.asiscontrol.service;

import com.asiscontrol.dto.academico.AcademicoDtos.ActualizarCursoRequest;
import com.asiscontrol.dto.academico.AcademicoDtos.CrearCursoRequest;
import com.asiscontrol.dto.academico.AcademicoDtos.CursoResponse;
import com.asiscontrol.entity.Curso;
import com.asiscontrol.entity.GradoAcademico;
import com.asiscontrol.entity.enums.EstadoAnioAcademico;
import com.asiscontrol.entity.enums.ResultadoAuditoria;
import com.asiscontrol.exception.BusinessRuleException;
import com.asiscontrol.exception.ConflictException;
import com.asiscontrol.exception.ResourceNotFoundException;
import com.asiscontrol.repository.AsignacionCursoRepository;
import com.asiscontrol.repository.CursoRepository;
import com.asiscontrol.repository.GradoAcademicoRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CursoService {

    private final CursoRepository cursoRepository;
    private final GradoAcademicoRepository gradoRepository;
    private final AsignacionCursoRepository asignacionRepository;
    private final AuditoriaService auditoria;

    public CursoService(
            CursoRepository cursoRepository,
            GradoAcademicoRepository gradoRepository,
            AsignacionCursoRepository asignacionRepository,
            AuditoriaService auditoria) {
        this.cursoRepository = cursoRepository;
        this.gradoRepository = gradoRepository;
        this.asignacionRepository = asignacionRepository;
        this.auditoria = auditoria;
    }

    @Transactional
    public CursoResponse crear(CrearCursoRequest request) {
        validarCodigoUnico(request.codigo(), null);
        Curso curso = new Curso();
        aplicar(curso, request.codigo(), request.nombre(), request.descripcion(), request.horasSemanales());
        curso.setGradoAcademico(gradoDisponible(request.gradoAcademicoId()));
        curso.setActivo(true);
        Curso saved = cursoRepository.save(curso);
        auditoria.registrar("CREAR_CURSO", ModuloAuditoria.ACADEMICO, "Curso",
                saved.getId().toString(), ResultadoAuditoria.EXITOSO, null, null, describir(saved));
        return mapear(saved);
    }

    @Transactional
    public CursoResponse actualizar(Long id, ActualizarCursoRequest request) {
        Curso curso = obtenerEntidad(id);
        exigirVersion(curso.getVersion(), request.version());
        validarCodigoUnico(request.codigo(), id);
        String anterior = describir(curso);
        aplicar(curso, request.codigo(), request.nombre(), request.descripcion(), request.horasSemanales());
        cambiarGrado(curso, request.gradoAcademicoId());
        Curso saved = cursoRepository.save(curso);
        auditoria.registrar("ACTUALIZAR_CURSO", ModuloAuditoria.ACADEMICO, "Curso",
                id.toString(), ResultadoAuditoria.EXITOSO, null, anterior, describir(saved));
        return mapear(saved);
    }

    public CursoResponse obtener(Long id) {
        return mapear(obtenerEntidad(id));
    }

    public Page<CursoResponse> listar(
            String busqueda, Integer anio, Integer grado, Pageable pageable) {
        String criterio = busqueda == null || busqueda.isBlank() ? null : busqueda.trim();
        return cursoRepository.buscar(criterio, anio, grado, pageable).map(this::mapear);
    }

    @Transactional
    public void eliminar(Long id) {
        Curso curso = obtenerEntidad(id);
        curso.setActivo(false);
        cursoRepository.save(curso);
        auditoria.registrar("ELIMINAR_CURSO", ModuloAuditoria.ACADEMICO, "Curso",
                id.toString(), ResultadoAuditoria.EXITOSO, null,
                "codigo=" + curso.getCodigo() + ",nombre=" + curso.getNombre(), null);
    }

    public Curso obtenerEntidad(Long id) {
        return cursoRepository.findByIdAndActivoTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "CURSO_NO_ENCONTRADO", "No existe un curso activo con id " + id));
    }

    /** Un curso solo puede ofrecerse en un grado habilitado de un anio que no este cerrado. */
    private GradoAcademico gradoDisponible(Long gradoId) {
        GradoAcademico grado = gradoRepository.findById(gradoId)
                .filter(GradoAcademico::isActivo)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "GRADO_NO_ENCONTRADO", "El grado seleccionado no existe o no esta habilitado"));
        if (grado.getAnioAcademico().getEstado() == EstadoAnioAcademico.CERRADO) {
            throw new BusinessRuleException(
                    "ANIO_CERRADO", "No se pueden registrar cursos en un año académico cerrado");
        }
        return grado;
    }

    /**
     * Cambiar el grado de un curso con asignaciones vigentes dejaria secciones de otro grado
     * dictandolo; primero deben retirarse esas asignaciones.
     */
    private void cambiarGrado(Curso curso, Long gradoId) {
        GradoAcademico actual = curso.getGradoAcademico();
        if (actual != null && actual.getId().equals(gradoId)) {
            return;
        }
        if (actual != null && asignacionRepository.existsByCursoIdAndActivoTrue(curso.getId())) {
            throw new ConflictException(
                    "CURSO_CON_ASIGNACIONES",
                    "El curso ya está asignado a secciones; no se puede cambiar su año o grado");
        }
        curso.setGradoAcademico(gradoDisponible(gradoId));
    }

    private String describir(Curso curso) {
        GradoAcademico grado = curso.getGradoAcademico();
        return "codigo=" + curso.getCodigo() + ",nombre=" + curso.getNombre()
                + (grado == null ? "" : ",anio=" + grado.getAnioAcademico().getAnio()
                        + ",grado=" + grado.getNumero());
    }

    private void validarCodigoUnico(String codigo, Long idActual) {
        cursoRepository.findByCodigoIgnoreCase(codigo.trim())
                .filter(existente -> idActual == null || !existente.getId().equals(idActual))
                .ifPresent(existente -> {
                    throw new ConflictException("CURSO_CODIGO_DUPLICADO", "Ya existe un curso con ese codigo , puede estar activo o no");
                });
    }

    private void aplicar(Curso curso, String codigo, String nombre, String descripcion, int horasSemanales) {
        curso.setCodigo(codigo.trim().toUpperCase());
        curso.setNombre(nombre.trim());
        curso.setDescripcion(normalizar(descripcion));
        curso.setHorasSemanales(horasSemanales);
    }

    private CursoResponse mapear(Curso curso) {
        GradoAcademico grado = curso.getGradoAcademico();
        return new CursoResponse(
                curso.getId(), curso.getCodigo(), curso.getNombre(), curso.getDescripcion(),
                curso.getHorasSemanales(), curso.isActivo(), curso.getVersion(),
                grado == null ? null : grado.getId(),
                grado == null ? null : grado.getAnioAcademico().getAnio(),
                grado == null ? null : grado.getNumero());
    }

    private String normalizar(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    private void exigirVersion(long actual, Long recibida) {
        if (recibida == null || actual != recibida) {
            throw new ConflictException(
                    "VERSION_DESACTUALIZADA", "El curso fue modificado; vuelva a cargarlo");
        }
    }
}
