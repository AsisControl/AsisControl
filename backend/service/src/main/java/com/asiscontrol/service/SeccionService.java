package com.asiscontrol.service;

import com.asiscontrol.dto.academico.AcademicoDtos.ActualizarSeccionRequest;
import com.asiscontrol.dto.academico.AcademicoDtos.CrearSeccionRequest;
import com.asiscontrol.dto.academico.AcademicoDtos.SeccionResponse;
import com.asiscontrol.entity.Docente;
import com.asiscontrol.entity.GradoAcademico;
import com.asiscontrol.entity.Seccion;
import com.asiscontrol.entity.enums.EstadoMatricula;
import com.asiscontrol.exception.BusinessRuleException;
import com.asiscontrol.exception.ConflictException;
import com.asiscontrol.exception.ResourceNotFoundException;
import com.asiscontrol.repository.DocenteRepository;
import com.asiscontrol.repository.GradoAcademicoRepository;
import com.asiscontrol.repository.MatriculaRepository;
import com.asiscontrol.repository.SeccionRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SeccionService {

    private final SeccionRepository seccionRepository;
    private final DocenteRepository docenteRepository;
    private final GradoAcademicoRepository gradoAcademicoRepository;
    private final MatriculaRepository matriculaRepository;
    private final ConfiguracionAcademicaService configuracion;
    private final AuditoriaService auditoria;
    private final com.asiscontrol.repository.SolicitudMatriculaRepository solicitudes;
    private final com.asiscontrol.repository.AsignacionCursoRepository asignaciones;

    public SeccionService(
            SeccionRepository seccionRepository,
            DocenteRepository docenteRepository,
            GradoAcademicoRepository gradoAcademicoRepository,
            MatriculaRepository matriculaRepository,
            ConfiguracionAcademicaService configuracion,
            AuditoriaService auditoria,
            com.asiscontrol.repository.SolicitudMatriculaRepository solicitudes,
            com.asiscontrol.repository.AsignacionCursoRepository asignaciones) {
        this.seccionRepository = seccionRepository;
        this.docenteRepository = docenteRepository;
        this.gradoAcademicoRepository = gradoAcademicoRepository;
        this.matriculaRepository = matriculaRepository;
        this.configuracion = configuracion;
        this.auditoria = auditoria;
        this.solicitudes = solicitudes;
        this.asignaciones = asignaciones;
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public SeccionResponse crear(CrearSeccionRequest request) {
        var anio = configuracion.bloquear(request.anioAcademico());
        configuracion.exigirOferta(request.anioAcademico(), request.grado(), false);
        GradoAcademico gradoAcademico = obtenerGradoActivo(request.anioAcademico(), request.grado());
        if (anio.getEstado() == com.asiscontrol.entity.enums.EstadoAnioAcademico.ACTIVO
                && (request.motivo() == null || request.motivo().isBlank()))
            throw new BusinessRuleException(
                    "MOTIVO_REQUERIDO", "Indique el motivo para añadir una sección al año activo");
        validarUnica(
                gradoAcademico, request.anioAcademico(), request.grado(), request.nombre(), null);
        Seccion seccion = new Seccion();
        aplicar(
                seccion,
                request.nombre(),
                request.grado(),
                request.anioAcademico(),
                request.capacidadMaxima(),
                request.docenteTutorId());
        seccion.setGradoAcademico(gradoAcademico);
        seccion.setActivo(true);
        seccionRepository.saveAndFlush(seccion);
        auditoria.registrar(
                "CREAR_SECCION",
                "CONFIGURACION_ACADEMICA",
                "Seccion",
                seccion.getId().toString(),
                com.asiscontrol.entity.enums.ResultadoAuditoria.EXITOSO,
                request.motivo(),
                null,
                "grado=" + seccion.getGrado() + ",capacidad=" + seccion.getCapacidadMaxima());
        return mapear(seccion);
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public SeccionResponse actualizar(Long id, ActualizarSeccionRequest request) {
        Seccion vista = obtenerEntidad(id);
        configuracion.bloquear(vista.getAnioAcademico());
        configuracion.exigirOferta(vista.getAnioAcademico(), vista.getGrado(), false);
        GradoAcademico gradoAcademico =
                obtenerGradoActivo(request.anioAcademico(), request.grado());
        Seccion seccion = seccionRepository.buscarActivaParaActualizar(id).orElseThrow();
        String anterior =
                "grado=" + seccion.getGrado() + ",capacidad=" + seccion.getCapacidadMaxima();
        if (request.anioAcademico() != seccion.getAnioAcademico()
                || request.grado() != seccion.getGrado())
            throw new BusinessRuleException(
                    "SECCION_IDENTIDAD_FIJA",
                    "El año y el grado de una sección son fijos; registre otra sección");
        if (!request.nombre().trim().equalsIgnoreCase(seccion.getNombre()) && utilizada(id))
            throw new BusinessRuleException(
                    "SECCION_UTILIZADA", "No cambie el nombre de una sección utilizada");
        exigirVersion(seccion.getVersion(), request.version());
        validarUnica(
                gradoAcademico, request.anioAcademico(), request.grado(), request.nombre(), id);
        long matriculados =
                matriculaRepository.countBySeccionIdAndEstadoAndActivoTrue(
                        id, EstadoMatricula.ACTIVA);
        if (request.capacidadMaxima() < matriculados) {
            throw new BusinessRuleException(
                    "SECCION_CAPACIDAD_INFERIOR_A_MATRICULADOS",
                    "La capacidad no puede ser menor que los alumnos matriculados");
        }
        aplicar(
                seccion,
                request.nombre(),
                request.grado(),
                request.anioAcademico(),
                request.capacidadMaxima(),
                request.docenteTutorId());
        seccion.setGradoAcademico(gradoAcademico);
        seccionRepository.saveAndFlush(seccion);
        auditoria.registrar(
                "ACTUALIZAR_SECCION",
                "CONFIGURACION_ACADEMICA",
                "Seccion",
                seccion.getId().toString(),
                com.asiscontrol.entity.enums.ResultadoAuditoria.EXITOSO,
                request.motivo(),
                anterior,
                "grado=" + seccion.getGrado() + ",capacidad=" + seccion.getCapacidadMaxima());
        return mapear(seccion);
    }

    public SeccionResponse obtener(Long id) {
        return mapear(obtenerEntidad(id));
    }

    public Page<SeccionResponse> listar(
            Boolean activo, Integer anioAcademico, Integer grado, Pageable pageable) {
        validarFiltros(anioAcademico, grado);
        return seccionRepository
                .buscarConFiltros(
                        activo == null ? Boolean.TRUE : activo, anioAcademico, grado, pageable)
                .map(this::mapear);
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public SeccionResponse cambiarEstado(
            Long id, com.asiscontrol.dto.academico.ConfiguracionDtos.EstadoSeccion request) {
        Seccion vista =
                seccionRepository
                        .findById(id)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "SECCION_NO_ENCONTRADA",
                                                "No se encontró la sección"));
        configuracion.bloquear(vista.getAnioAcademico());
        configuracion.exigirOferta(vista.getAnioAcademico(), vista.getGrado(), false);
        Seccion seccion = seccionRepository.bloquearPorId(id).orElseThrow();
        exigirVersion(seccion.getVersion(), request.version());
        if (!request.activo() && utilizada(id))
            throw new ConflictException(
                    "SECCION_UTILIZADA",
                    "No se puede inhabilitar una sección utilizada por solicitudes, matrículas o"
                        + " asignaciones");
        if (request.motivo() == null || request.motivo().isBlank())
            throw new BusinessRuleException(
                    "MOTIVO_REQUERIDO", "Indique el motivo del cambio de estado");
        boolean anterior = seccion.isActivo();
        seccion.setActivo(request.activo());
        seccionRepository.saveAndFlush(seccion);
        auditoria.registrar(
                "CAMBIAR_ESTADO_SECCION",
                "CONFIGURACION_ACADEMICA",
                "Seccion",
                id.toString(),
                com.asiscontrol.entity.enums.ResultadoAuditoria.EXITOSO,
                request.motivo(),
                "activo=" + anterior,
                "activo=" + request.activo());
        return mapear(seccion);
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void eliminar(Long id) {
        throw new BusinessRuleException(
                "HISTORIAL_PROTEGIDO",
                "Utilice el cambio de estado con motivo y versión; la sección se conserva");
    }

    private boolean utilizada(Long id) {
        return matriculaRepository.existsBySeccionId(id)
                || solicitudes.existsBySeccionId(id)
                || asignaciones.existsBySeccionId(id);
    }

    public Seccion obtenerEntidad(Long id) {
        return seccionRepository
                .findByIdAndActivoTrue(id)
                .orElseThrow(
                        () ->
                                new ResourceNotFoundException(
                                        "SECCION_NO_ENCONTRADA",
                                        "No existe una seccion activa con id " + id));
    }

    private void validarUnica(
            GradoAcademico gradoAcademico,
            int anio,
            int grado,
            String nombre,
            Long idActual) {
        String nombreNormalizado = nombre.trim();
        boolean duplicadaLegacy =
                seccionRepository
                        .findByAnioAcademicoAndGradoAndNombreIgnoreCase(
                                anio, grado, nombreNormalizado)
                        .filter(existente -> idActual == null || !existente.getId().equals(idActual))
                        .isPresent();
        boolean duplicadaPorRelacion =
                gradoAcademico.getId() != null
                        && seccionRepository
                                .findByGradoAcademicoIdAndNombreIgnoreCase(
                                        gradoAcademico.getId(), nombreNormalizado)
                                .filter(
                                        existente ->
                                                idActual == null
                                                        || !existente.getId().equals(idActual))
                                .isPresent();
        if (duplicadaLegacy || duplicadaPorRelacion)
            throw new ConflictException(
                    "SECCION_DUPLICADA", "Ya existe esa seccion para el grado y anio academico");
    }

    private GradoAcademico obtenerGradoActivo(int anio, int numero) {
        return gradoAcademicoRepository
                .findByAnioAcademicoAnioAndNumero(anio, numero)
                .filter(GradoAcademico::isActivo)
                .orElseThrow(
                        () ->
                                new BusinessRuleException(
                                        "GRADO_NO_HABILITADO",
                                        "El grado no está habilitado para ese año"));
    }

    private void aplicar(
            Seccion seccion,
            String nombre,
            int grado,
            int anioAcademico,
            int capacidadMaxima,
            Long docenteTutorId) {
        seccion.setNombre(nombre.trim().toUpperCase());
        seccion.setGrado(grado);
        seccion.setAnioAcademico(anioAcademico);
        seccion.setCapacidadMaxima(capacidadMaxima);
        seccion.setDocenteTutor(docenteTutorId == null ? null : buscarDocente(docenteTutorId));
    }

    private Docente buscarDocente(Long id) {
        return docenteRepository
                .findByIdAndActivoTrue(id)
                .orElseThrow(
                        () ->
                                new ResourceNotFoundException(
                                        "DOCENTE_NO_ENCONTRADO",
                                        "No existe un docente activo con id " + id));
    }

    private SeccionResponse mapear(Seccion seccion) {
        Docente tutor = seccion.getDocenteTutor();
        return new SeccionResponse(
                seccion.getId(),
                seccion.getNombre(),
                seccion.getGrado(),
                seccion.getAnioAcademico(),
                seccion.getCapacidadMaxima(),
                tutor == null ? null : tutor.getId(),
                tutor == null ? null : tutor.getPersona().nombreCompleto(),
                seccion.isActivo(),
                seccion.getVersion());
    }

    private void exigirVersion(long actual, Long recibida) {
        if (recibida == null || actual != recibida) {
            throw new ConflictException(
                    "VERSION_DESACTUALIZADA", "La seccion fue modificada; vuelva a cargarla");
        }
    }

    private void validarFiltros(Integer anioAcademico, Integer grado) {
        if (anioAcademico != null && (anioAcademico < 2000 || anioAcademico > 2100))
            throw new BusinessRuleException(
                    "ANIO_ACADEMICO_INVALIDO", "El año académico debe estar entre 2000 y 2100");
        if (grado != null && (grado < 1 || grado > 5))
            throw new BusinessRuleException("GRADO_INVALIDO", "El grado debe estar entre 1 y 5");
    }
}
