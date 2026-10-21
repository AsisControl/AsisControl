package com.asiscontrol.service;

import com.asiscontrol.dto.academico.AcademicoDtos.ActualizarSeccionRequest;
import com.asiscontrol.dto.academico.AcademicoDtos.ConfirmarPropuestasSeccionesRequest;
import com.asiscontrol.dto.academico.AcademicoDtos.CrearSeccionRequest;
import com.asiscontrol.dto.academico.AcademicoDtos.GenerarPropuestasSeccionesRequest;
import com.asiscontrol.dto.academico.AcademicoDtos.SeccionPropuestaRequest;
import com.asiscontrol.dto.academico.AcademicoDtos.SeccionPropuestaResponse;
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

import java.util.EnumSet;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class SeccionService {

    private static final Set<com.asiscontrol.entity.enums.EstadoSolicitudMatricula>
            ESTADOS_SOLICITUD_VIGENTES =
                    EnumSet.of(
                            com.asiscontrol.entity.enums.EstadoSolicitudMatricula.BORRADOR,
                            com.asiscontrol.entity.enums.EstadoSolicitudMatricula.ENVIADA,
                            com.asiscontrol.entity.enums.EstadoSolicitudMatricula.EN_REVISION,
                            com.asiscontrol.entity.enums.EstadoSolicitudMatricula.OBSERVADA);

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

    public List<SeccionPropuestaResponse> generarPropuestas(
            GenerarPropuestasSeccionesRequest request) {
        validarPropuestas(request);
        configuracion.exigirOferta(request.anioAcademico(), request.grado(), false);
        GradoAcademico gradoAcademico =
                obtenerGradoActivo(request.anioAcademico(), request.grado());

        Set<String> nombresExistentes =
                new HashSet<>(
                        seccionRepository.findByAnioAcademicoOrderByGradoAscNombreAsc(
                                        request.anioAcademico())
                                .stream()
                                .filter(
                                        seccion ->
                                                seccion.getGrado() == request.grado()
                                                        || (seccion.getGradoAcademico() != null
                                                                && Objects.equals(
                                                                        seccion.getGradoAcademico()
                                                                                .getId(),
                                                                        gradoAcademico.getId())))
                                .map(seccion -> normalizarNombre(seccion.getNombre()))
                                .toList());

        List<SeccionPropuestaResponse> propuestas = new ArrayList<>(request.cantidad());
        int indice = 0;
        while (propuestas.size() < request.cantidad()) {
            String nombre = nombrePropuesto(indice++);
            if (!nombresExistentes.add(nombre)) continue;
            propuestas.add(
                    new SeccionPropuestaResponse(
                            nombre,
                            request.grado(),
                            request.anioAcademico(),
                            request.capacidadMaxima()));
        }
        return propuestas;
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public List<SeccionResponse> confirmarPropuestas(
            ConfirmarPropuestasSeccionesRequest request) {
        validarConfirmacion(request);
        GradoAcademico gradoAcademico = validarOferta(request.anioAcademico(), request.grado());
        Set<String> nombresExistentes =
                nombresExistentes(request.anioAcademico(), request.grado(), gradoAcademico);
        Set<String> nombresSolicitud = new HashSet<>();

        for (SeccionPropuestaRequest propuesta : request.secciones()) {
            String nombre = normalizarNombre(propuesta.nombre());
            if (!nombresSolicitud.add(nombre))
                throw new ConflictException(
                        "SECCIONES_DUPLICADAS",
                        "La solicitud contiene nombres de sección duplicados");
            if (nombresExistentes.contains(nombre))
                throw new ConflictException(
                        "SECCION_DUPLICADA",
                        "Ya existe esa sección para el grado y año académico");
        }

        validarMotivoSiAnioActivo(request.anioAcademico(), request.motivo());

        List<SeccionResponse> creadas = new ArrayList<>(request.secciones().size());
        for (SeccionPropuestaRequest propuesta : request.secciones()) {
            creadas.add(
                    crear(
                            new CrearSeccionRequest(
                                    propuesta.nombre(),
                                    request.grado(),
                                    request.anioAcademico(),
                                    propuesta.capacidadMaxima(),
                                    null,
                                    request.motivo())));
        }
        return creadas;
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
        if (!request.activo() && tieneUsoVigente(id))
            throw new ConflictException(
                    "SECCION_UTILIZADA",
                    "No se puede inhabilitar una sección con solicitudes, matrículas o asignaciones"
                        + " vigentes");
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
        Seccion vista =
                seccionRepository
                        .findById(id)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "SECCION_NO_ENCONTRADA",
                                                "No se encontró la sección"));
        var anio = configuracion.bloquear(vista.getAnioAcademico());
        if (anio.getEstado() != com.asiscontrol.entity.enums.EstadoAnioAcademico.BORRADOR)
            throw new BusinessRuleException(
                    "SECCION_ELIMINACION_NO_PERMITIDA",
                    "Solo se pueden eliminar secciones de años en borrador");

        Seccion seccion =
                seccionRepository
                        .bloquearPorId(id)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "SECCION_NO_ENCONTRADA",
                                                "No se encontró la sección"));
        if (utilizada(id))
            throw new ConflictException(
                    "SECCION_CON_RELACIONES",
                    "No se puede eliminar una sección que tiene matrículas, solicitudes, asignaciones, horarios u otras relaciones históricas");

        String anterior =
                "nombre="
                        + seccion.getNombre()
                        + ",grado="
                        + seccion.getGrado()
                        + ",anio="
                        + seccion.getAnioAcademico()
                        + ",capacidad="
                        + seccion.getCapacidadMaxima();
        seccionRepository.delete(seccion);
        seccionRepository.flush();
        auditoria.registrar(
                "ELIMINAR_SECCION",
                "CONFIGURACION_ACADEMICA",
                "Seccion",
                id.toString(),
                com.asiscontrol.entity.enums.ResultadoAuditoria.EXITOSO,
                null,
                anterior,
                null);
    }

    private boolean tieneUsoVigente(Long id) {
        return matriculaRepository.existsBySeccionIdAndActivoTrueAndEstado(
                        id, EstadoMatricula.ACTIVA)
                || solicitudes.existsBySeccionIdAndActivoTrueAndEstadoIn(
                        id, ESTADOS_SOLICITUD_VIGENTES)
                || asignaciones.existsBySeccionIdAndActivoTrue(id);
    }

    private void validarPropuestas(GenerarPropuestasSeccionesRequest request) {
        if (request.anioAcademico() < 2000 || request.anioAcademico() > 2100)
            throw new BusinessRuleException(
                    "ANIO_ACADEMICO_INVALIDO", "El año académico debe estar entre 2000 y 2100");
        if (request.grado() < 1 || request.grado() > 5)
            throw new BusinessRuleException("GRADO_INVALIDO", "El grado debe estar entre 1 y 5");
        if (request.cantidad() <= 0)
            throw new BusinessRuleException(
                    "CANTIDAD_SECCIONES_INVALIDA", "La cantidad de secciones debe ser positiva");
        if (request.capacidadMaxima() <= 0 || request.capacidadMaxima() > 100)
            throw new BusinessRuleException(
                    "CAPACIDAD_SECCION_INVALIDA",
                    "La capacidad de cada sección debe estar entre 1 y 100");
    }

    private void validarConfirmacion(ConfirmarPropuestasSeccionesRequest request) {
        validarAnioYGrado(request.anioAcademico(), request.grado());
        if (request.secciones() == null || request.secciones().isEmpty())
            throw new BusinessRuleException(
                    "SECCIONES_REQUERIDAS", "Debe confirmar al menos una sección");
        for (SeccionPropuestaRequest propuesta : request.secciones()) {
            if (propuesta == null
                    || propuesta.nombre() == null
                    || propuesta.nombre().isBlank())
                throw new BusinessRuleException(
                        "NOMBRE_SECCION_INVALIDO", "El nombre de la sección es obligatorio");
            if (propuesta.capacidadMaxima() <= 0 || propuesta.capacidadMaxima() > 100)
                throw new BusinessRuleException(
                        "CAPACIDAD_SECCION_INVALIDA",
                        "La capacidad de cada sección debe estar entre 1 y 100");
        }
    }

    private void validarAnioYGrado(int anioAcademico, int grado) {
        if (anioAcademico < 2000 || anioAcademico > 2100)
            throw new BusinessRuleException(
                    "ANIO_ACADEMICO_INVALIDO", "El año académico debe estar entre 2000 y 2100");
        if (grado < 1 || grado > 5)
            throw new BusinessRuleException("GRADO_INVALIDO", "El grado debe estar entre 1 y 5");
    }

    private GradoAcademico validarOferta(int anioAcademico, int grado) {
        configuracion.exigirOferta(anioAcademico, grado, false);
        return obtenerGradoActivo(anioAcademico, grado);
    }

    private Set<String> nombresExistentes(
            int anioAcademico, int grado, GradoAcademico gradoAcademico) {
        return new HashSet<>(
                seccionRepository
                        .findByAnioAcademicoOrderByGradoAscNombreAsc(anioAcademico)
                        .stream()
                        .filter(
                                seccion ->
                                        seccion.getGrado() == grado
                                                || (seccion.getGradoAcademico() != null
                                                        && Objects.equals(
                                                                seccion.getGradoAcademico().getId(),
                                                                gradoAcademico.getId())))
                        .map(seccion -> normalizarNombre(seccion.getNombre()))
                        .toList());
    }

    private void validarMotivoSiAnioActivo(int anioAcademico, String motivo) {
        if (configuracion.buscar(anioAcademico).getEstado()
                        == com.asiscontrol.entity.enums.EstadoAnioAcademico.ACTIVO
                && (motivo == null || motivo.isBlank()))
            throw new BusinessRuleException(
                    "MOTIVO_REQUERIDO", "Indique el motivo para añadir secciones al año activo");
    }

    private String nombrePropuesto(int indice) {
        StringBuilder nombre = new StringBuilder();
        int valor = indice;
        do {
            nombre.insert(0, (char) ('A' + valor % 26));
            valor = valor / 26 - 1;
        } while (valor >= 0);
        return nombre.toString();
    }

    private String normalizarNombre(String nombre) {
        return nombre.trim().toUpperCase(Locale.ROOT);
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
