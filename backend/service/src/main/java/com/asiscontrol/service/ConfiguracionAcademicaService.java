package com.asiscontrol.service;

import com.asiscontrol.dto.PageResponse;
import com.asiscontrol.dto.academico.ConfiguracionDtos.*;
import com.asiscontrol.entity.*;
import com.asiscontrol.entity.enums.*;
import com.asiscontrol.entity.planificacion.AnioAcademico;
import com.asiscontrol.entity.planificacion.PeriodoAcademico;
import com.asiscontrol.exception.*;
import com.asiscontrol.repository.*;

import com.asiscontrol.repository.planificacion.AnioAcademicoRepository;
import com.asiscontrol.repository.planificacion.PeriodoAcademicoRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class ConfiguracionAcademicaService {
    private final AnioAcademicoRepository anios;
    private final PeriodoAcademicoRepository periodos;
    private final GradoAcademicoRepository grados;
    private final SeccionRepository secciones;
    private final MatriculaRepository matriculas;
    private final AuditoriaService auditoria;

    public ConfiguracionAcademicaService(
            AnioAcademicoRepository anios,
            PeriodoAcademicoRepository periodos,
            GradoAcademicoRepository grados,
            SeccionRepository secciones,
            MatriculaRepository matriculas,
            AuditoriaService auditoria) {
        this.anios = anios;
        this.periodos = periodos;
        this.grados = grados;
        this.secciones = secciones;
        this.matriculas = matriculas;
        this.auditoria = auditoria;
    }

    public PageResponse<AnioResponse> listar(Pageable page) {
        return PageResponse.from(anios.findAll(page), this::mapear);
    }

    public AnioResponse obtener(int anio) {
        return mapear(buscar(anio));
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public AnioResponse crear(CrearAnio request) {
        validarFechas(request.fechaInicio(), request.fechaFin());
        if (anios.findByAnio(request.anio()).isPresent())
            rechazarConflicto("El año académico ya está registrado");
        AnioAcademico anioAcademico = new AnioAcademico();
        anioAcademico.setAnio(request.anio());
        anioAcademico.setFechaInicio(request.fechaInicio());
        anioAcademico.setFechaFin(request.fechaFin());
        anios.saveAndFlush(anioAcademico);
        registrarAuditoria(
                "CREAR_ANIO",
                "AnioAcademico",
                anioAcademico.getId(),
                null,
                null,
                anioAcademico.getEstado().name());
        return mapear(anioAcademico);
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public AnioResponse estado(int numero, EstadoAnio request) {
        AnioAcademico anioAcademico = bloquear(numero);
        validarVersion(anioAcademico.getVersion(), request.version());
        String valorAnterior = anioAcademico.getEstado().name();
        if (anioAcademico.getEstado() == EstadoAnioAcademico.BORRADOR
                && request.estado() == EstadoAnioAcademico.ACTIVO) {
            if (periodos.findByAnioAcademicoAnioOrderByOrden(numero).isEmpty())
                rechazarRegla("Configure al menos un período antes de activar el año");
            if (anios.existsByEstado(EstadoAnioAcademico.ACTIVO))
                rechazarConflicto("Ya existe un año académico activo");
            anioAcademico.setClaveActiva(1);
        } else if (anioAcademico.getEstado() == EstadoAnioAcademico.ACTIVO
                && request.estado() == EstadoAnioAcademico.CERRADO) {
            if (periodos.findByAnioAcademicoAnioOrderByOrden(numero).stream()
                    .anyMatch(periodo -> periodo.getEstado() != EstadoPeriodoAcademico.CERRADO))
                rechazarRegla("Cierre todos los períodos antes de cerrar el año");
            anioAcademico.setClaveActiva(null);
        } else rechazarRegla("No se permite esa transición de año académico");
        anioAcademico.setEstado(request.estado());
        if (request.estado() == EstadoAnioAcademico.CERRADO)
            anioAcademico.setAdmisionAbierta(false);
        anios.saveAndFlush(anioAcademico);
        registrarAuditoria(
                "CAMBIAR_ESTADO_ANIO",
                "AnioAcademico",
                anioAcademico.getId(),
                request.motivo(),
                valorAnterior,
                request.estado().name());
        return mapear(anioAcademico);
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public AnioResponse agregarPeriodo(int numero, CrearPeriodo request) {
        AnioAcademico anioAcademico = bloquear(numero);
        if (anioAcademico.getEstado() != EstadoAnioAcademico.BORRADOR)
            rechazarRegla("Los períodos se configuran mientras el año está en borrador");
        validarFechas(request.fechaInicio(), request.fechaFin());
        if (request.fechaInicio().isBefore(anioAcademico.getFechaInicio())
                || request.fechaFin().isAfter(anioAcademico.getFechaFin()))
            rechazarRegla("Las validarFechas del período deben estar dentro del año");
        for (PeriodoAcademico periodo : periodos.findByAnioAcademicoAnioOrderByOrden(numero)) {
            if (periodo.getOrden() == request.orden()
                    || periodo.getNombre().equalsIgnoreCase(request.nombre().trim()))
                rechazarConflicto("El nombre u orden del período ya está registrado");
            if (!request.fechaFin().isBefore(periodo.getFechaInicio())
                    && !request.fechaInicio().isAfter(periodo.getFechaFin()))
                rechazarRegla(
                        "Las validarFechas del período se superponen con " + periodo.getNombre());
        }
        PeriodoAcademico periodo = new PeriodoAcademico();
        periodo.setAnioAcademico(anioAcademico);
        periodo.setNombre(request.nombre().trim());
        periodo.setOrden(request.orden());
        periodo.setFechaInicio(request.fechaInicio());
        periodo.setFechaFin(request.fechaFin());
        periodos.saveAndFlush(periodo);
        registrarAuditoria(
                "CREAR_PERIODO",
                "PeriodoAcademico",
                periodo.getId(),
                null,
                null,
                periodo.getNombre());
        return mapear(anioAcademico);
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public AnioResponse estadoPeriodo(Long id, EstadoPeriodo request) {
        PeriodoAcademico periodo =
                periodos.findById(id)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "PERIODO_NO_ENCONTRADO",
                                                "No se encontró el período"));
        AnioAcademico anioAcademico = bloquear(periodo.getAnioAcademico().getAnio());
        validarVersion(periodo.getVersion(), request.version());
        String valorAnterior = periodo.getEstado().name();
        if (anioAcademico.getEstado() != EstadoAnioAcademico.ACTIVO)
            rechazarRegla("El año del período debe estar activo");
        if (periodo.getEstado() == EstadoPeriodoAcademico.PLANIFICADO
                && request.estado() == EstadoPeriodoAcademico.ACTIVO) {
            if (periodos.existsByEstado(EstadoPeriodoAcademico.ACTIVO))
                rechazarConflicto("Ya existe un período activo");
            periodo.setClaveActiva(1);
        } else if (periodo.getEstado() == EstadoPeriodoAcademico.ACTIVO
                && request.estado() == EstadoPeriodoAcademico.CERRADO) periodo.setClaveActiva(null);
        else rechazarRegla("No se permite esa transición de período");
        periodo.setEstado(request.estado());
        periodos.saveAndFlush(periodo);
        registrarAuditoria(
                "CAMBIAR_ESTADO_PERIODO",
                "PeriodoAcademico",
                periodo.getId(),
                request.motivo(),
                valorAnterior,
                request.estado().name());
        return mapear(anioAcademico);
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public AnioResponse agregarGrado(int numero, CrearGrado request) {
        AnioAcademico anioAcademico = bloquear(numero);
        exigirEditable(anioAcademico);
        if (grados.findByAnioAcademicoAnioAndNumero(numero, request.numero()).isPresent())
            rechazarConflicto("El grado ya está configurado para ese año");
        GradoAcademico gradoAcademico = new GradoAcademico();
        gradoAcademico.setAnioAcademico(anioAcademico);
        gradoAcademico.setNumero(request.numero());
        gradoAcademico.setCapacidadDefault(request.capacidadDefault());
        grados.saveAndFlush(gradoAcademico);
        registrarAuditoria(
                "CONFIGURAR_GRADO",
                "GradoAcademico",
                gradoAcademico.getId(),
                null,
                null,
                "grado=" + gradoAcademico.getNumero());
        return mapear(anioAcademico);
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public AnioResponse estadoGrado(Long id, EstadoGrado request) {
        GradoAcademico gradoAcademico =
                grados.findById(id)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "GRADO_NO_ENCONTRADO", "No se encontró el grado"));
        AnioAcademico anioAcademico = bloquear(gradoAcademico.getAnioAcademico().getAnio());
        exigirEditable(anioAcademico);
        validarVersion(gradoAcademico.getVersion(), request.version());
        if (!request.activo()
                && secciones
                        .findByAnioAcademicoOrderByGradoAscNombreAsc(anioAcademico.getAnio())
                        .stream()
                        .anyMatch(
                                seccion ->
                                        seccion.getGrado() == gradoAcademico.getNumero()
                                                && seccion.isActivo()))
            rechazarRegla("Primero inhabilite las secciones del grado");
        String valorAnterior =
                "activo="
                        + gradoAcademico.isActivo()
                        + ",capacidad="
                        + gradoAcademico.getCapacidadDefault();
        gradoAcademico.setActivo(request.activo());
        gradoAcademico.setCapacidadDefault(request.capacidadDefault());
        grados.saveAndFlush(gradoAcademico);
        registrarAuditoria(
                "ACTUALIZAR_GRADO",
                "GradoAcademico",
                gradoAcademico.getId(),
                request.motivo(),
                valorAnterior,
                "activo="
                        + gradoAcademico.isActivo()
                        + ",capacidad="
                        + gradoAcademico.getCapacidadDefault());
        return mapear(anioAcademico);
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public AnioResponse admision(int numero, AdmisionAnio request) {
        AnioAcademico a = bloquear(numero);
        validarVersion(a.getVersion(), request.version());
        if (a.getEstado() != EstadoAnioAcademico.ACTIVO)
            rechazarRegla("Active el año antes de abrir o cerrar la admisión");
        if (request.abierta()
                && grados.findByAnioAcademicoAnioOrderByNumero(numero).stream()
                        .noneMatch(GradoAcademico::isActivo))
            rechazarRegla("Habilite al menos un grado antes de abrir la admisión");
        String anterior = Boolean.toString(a.isAdmisionAbierta());
        a.setAdmisionAbierta(request.abierta());
        anios.saveAndFlush(a);
        registrarAuditoria(
                "CONFIGURAR_ADMISION",
                "AnioAcademico",
                a.getId(),
                request.motivo(),
                anterior,
                Boolean.toString(request.abierta()));
        return mapear(a);
    }

    public void exigirAdmision(int numero, int grado) {
        exigirOferta(numero, grado, true);
        if (!buscar(numero).isAdmisionAbierta())
            rechazarRegla("La recepción de solicitudes no está abierta para ese año");
    }

    public AnioAcademico buscar(int numeroAnio) {
        return anios.findByAnio(numeroAnio)
                .orElseThrow(
                        () ->
                                new ResourceNotFoundException(
                                        "ANIO_NO_CONFIGURADO",
                                        "Configure el año académico en Administración"));
    }

    public AnioAcademico bloquear(int numeroAnio) {
        return anios.bloquear(numeroAnio)
                .orElseThrow(
                        () ->
                                new ResourceNotFoundException(
                                        "ANIO_NO_CONFIGURADO",
                                        "Configure el año académico en Administración"));
    }

    public void exigirOferta(int numeroAnio, int grado, boolean matriculacion) {
        AnioAcademico anioAcademico = buscar(numeroAnio);
        exigirEditable(anioAcademico);
        if (matriculacion && anioAcademico.getEstado() != EstadoAnioAcademico.ACTIVO)
            rechazarRegla("El año académico debe estar activo para gestionar la matrícula");
        if (grados.findByAnioAcademicoAnioAndNumero(numeroAnio, grado)
                .filter(GradoAcademico::isActivo)
                .isEmpty()) rechazarRegla("El grado no está habilitado para ese año");
    }

    public List<VacantesResponse> vacantes(int numeroAnio) {
        buscar(numeroAnio);
        return secciones.findByAnioAcademicoOrderByGradoAscNombreAsc(numeroAnio).stream()
                .map(this::vacante)
                .toList();
    }

    private VacantesResponse vacante(Seccion seccion) {
        long ocupado =
                matriculas.countBySeccionIdAndEstadoAndActivoTrue(
                        seccion.getId(), EstadoMatricula.ACTIVA);
        return new VacantesResponse(
                seccion.getId(),
                seccion.getNombre(),
                seccion.getGrado(),
                seccion.getAnioAcademico(),
                seccion.getCapacidadMaxima(),
                ocupado,
                Math.max(0, seccion.getCapacidadMaxima() - ocupado),
                seccion.isActivo(),
                seccion.getVersion());
    }

    private AnioResponse mapear(AnioAcademico anioAcademico) {
        return new AnioResponse(
                anioAcademico.getId(),
                anioAcademico.getAnio(),
                anioAcademico.getFechaInicio(),
                anioAcademico.getFechaFin(),
                anioAcademico.getEstado(),
                anioAcademico.getVersion(),
                periodos.findByAnioAcademicoAnioOrderByOrden(anioAcademico.getAnio()).stream()
                        .map(
                                periodo ->
                                        new PeriodoResponse(
                                                periodo.getId(),
                                                periodo.getNombre(),
                                                periodo.getOrden(),
                                                periodo.getFechaInicio(),
                                                periodo.getFechaFin(),
                                                periodo.getEstado(),
                                                periodo.getVersion()))
                        .toList(),
                grados.findByAnioAcademicoAnioOrderByNumero(anioAcademico.getAnio()).stream()
                        .map(
                                gradoAcademico ->
                                        new GradoResponse(
                                                gradoAcademico.getId(),
                                                gradoAcademico.getNumero(),
                                                gradoAcademico.getCapacidadDefault(),
                                                gradoAcademico.isActivo(),
                                                gradoAcademico.getVersion()))
                        .toList(),
                vacantes(anioAcademico.getAnio()),
                anioAcademico.isAdmisionAbierta());
    }

    private void validarFechas(LocalDate inicio, LocalDate fin) {
        if (fin.isBefore(inicio))
            rechazarRegla("La fecha final no puede ser anterior anioAcademico la inicial");
    }

    private void exigirEditable(AnioAcademico anioAcademico) {
        if (anioAcademico.getEstado() == EstadoAnioAcademico.CERRADO)
            rechazarRegla("El año cerrado conserva su información y no admite cambios ordinarios");
    }

    private void validarVersion(long actual, Long recibida) {
        if (recibida == null || actual != recibida)
            rechazarConflicto("La configuración cambió; vuelva anioAcademico cargarla");
    }

    private void rechazarRegla(String m) {
        throw new BusinessRuleException("CONFIGURACION_INVALIDA", m);
    }

    private void rechazarConflicto(String m) {
        throw new ConflictException("CONFIGURACION_CONFLICTO", m);
    }

    private void registrarAuditoria(
            String accion, String entidad, Long id, String motivo, String anterior, String nuevo) {
        auditoria.registrar(
                accion,
                ModuloAuditoria.CONFIGURACION_ACADEMICA,
                entidad,
                id.toString(),
                ResultadoAuditoria.EXITOSO,
                motivo,
                anterior,
                nuevo);
    }
}
