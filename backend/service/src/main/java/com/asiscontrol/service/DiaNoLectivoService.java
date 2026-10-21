package com.asiscontrol.service;

import com.asiscontrol.entity.enums.EstadoAnioAcademico;
import com.asiscontrol.entity.enums.ResultadoAuditoria;
import com.asiscontrol.entity.planificacion.AnioAcademico;
import com.asiscontrol.entity.planificacion.DiaNoLectivo;
import com.asiscontrol.exception.BusinessRuleException;
import com.asiscontrol.exception.ConflictException;
import com.asiscontrol.exception.ResourceNotFoundException;
import com.asiscontrol.repository.planificacion.AnioAcademicoRepository;
import com.asiscontrol.repository.planificacion.DiaNoLectivoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class DiaNoLectivoService {

    private final AnioAcademicoRepository anios;
    private final DiaNoLectivoRepository diasNoLectivos;
    private final AuditoriaService auditoria;

    public DiaNoLectivoService(
            AnioAcademicoRepository anios,
            DiaNoLectivoRepository diasNoLectivos,
            AuditoriaService auditoria) {
        this.anios = anios;
        this.diasNoLectivos = diasNoLectivos;
        this.auditoria = auditoria;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public DiaNoLectivo crear(int numeroAnio, LocalDate fecha, String descripcion) {
        AnioAcademico anioAcademico = bloquearAnio(numeroAnio);
        exigirEditable(anioAcademico);
        validarFechaDentroDelAnio(anioAcademico, fecha);

        if (diasNoLectivos.existsByAnioAcademicoAnioAndFecha(numeroAnio, fecha))
            throw new ConflictException(
                    "DIA_NO_LECTIVO_DUPLICADO",
                    "Ya existe un día no lectivo para esa fecha en el año académico");

        DiaNoLectivo diaNoLectivo = new DiaNoLectivo();
        diaNoLectivo.setAnioAcademico(anioAcademico);
        diaNoLectivo.setFecha(fecha);
        diaNoLectivo.setDescripcion(normalizarDescripcion(descripcion));
        DiaNoLectivo guardado = diasNoLectivos.saveAndFlush(diaNoLectivo);

        registrarAuditoria(
                "CREAR_DIA_NO_LECTIVO",
                guardado.getId(),
                null,
                describir(guardado));
        return guardado;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public DiaNoLectivo actualizarDescripcion(
            Long id, String descripcion, Long version) {
        DiaNoLectivo diaNoLectivo = obtener(id);
        AnioAcademico anioAcademico = bloquearAnio(diaNoLectivo.getAnioAcademico().getAnio());
        exigirEditable(anioAcademico);
        exigirVersion(diaNoLectivo.getVersion(), version);

        String anterior = describir(diaNoLectivo);
        diaNoLectivo.setDescripcion(normalizarDescripcion(descripcion));
        DiaNoLectivo actualizado = diasNoLectivos.saveAndFlush(diaNoLectivo);

        registrarAuditoria(
                "ACTUALIZAR_DIA_NO_LECTIVO",
                actualizado.getId(),
                anterior,
                describir(actualizado));
        return actualizado;
    }

    public List<DiaNoLectivo> listar(int numeroAnio) {
        obtenerAnio(numeroAnio);
        return diasNoLectivos.findByAnioAcademicoAnioOrderByFechaAsc(numeroAnio);
    }

    public boolean esNoLectivo(int numeroAnio, LocalDate fecha) {
        obtenerAnio(numeroAnio);
        if (fecha == null)
            throw new BusinessRuleException(
                    "DIA_NO_LECTIVO_FECHA_REQUERIDA", "La fecha es obligatoria");
        return diasNoLectivos.existsByAnioAcademicoAnioAndFecha(numeroAnio, fecha);
    }

    public DiaNoLectivo obtener(Long id) {
        return diasNoLectivos
                .findById(id)
                .orElseThrow(
                        () ->
                                new ResourceNotFoundException(
                                        "DIA_NO_LECTIVO_NO_ENCONTRADO",
                                        "No se encontró el día no lectivo"));
    }

    private AnioAcademico obtenerAnio(int numeroAnio) {
        return anios.findByAnio(numeroAnio)
                .orElseThrow(
                        () ->
                                new ResourceNotFoundException(
                                        "ANIO_NO_CONFIGURADO",
                                        "Configure el año académico en Administración"));
    }

    private AnioAcademico bloquearAnio(int numeroAnio) {
        return anios.bloquear(numeroAnio)
                .orElseThrow(
                        () ->
                                new ResourceNotFoundException(
                                        "ANIO_NO_CONFIGURADO",
                                        "Configure el año académico en Administración"));
    }

    private void validarFechaDentroDelAnio(AnioAcademico anioAcademico, LocalDate fecha) {
        if (fecha == null)
            throw new BusinessRuleException(
                    "DIA_NO_LECTIVO_FECHA_REQUERIDA", "La fecha es obligatoria");
        if (fecha.isBefore(anioAcademico.getFechaInicio())
                || fecha.isAfter(anioAcademico.getFechaFin()))
            throw new BusinessRuleException(
                    "DIA_NO_LECTIVO_FECHA_FUERA_DEL_ANIO",
                    "La fecha debe estar dentro del rango del año académico");
    }

    private void exigirEditable(AnioAcademico anioAcademico) {
        if (anioAcademico.getEstado() == EstadoAnioAcademico.CERRADO)
            throw new BusinessRuleException(
                    "DIA_NO_LECTIVO_ANIO_CERRADO",
                    "El año académico cerrado no admite cambios en sus días no lectivos");
    }

    private String normalizarDescripcion(String descripcion) {
        if (descripcion == null || descripcion.isBlank()) return null;
        String normalizada = descripcion.trim();
        if (normalizada.length() > 255)
            throw new BusinessRuleException(
                    "DIA_NO_LECTIVO_DESCRIPCION_INVALIDA",
                    "La descripción no puede superar los 255 caracteres");
        return normalizada;
    }

    private void exigirVersion(long actual, Long recibida) {
        if (recibida == null || actual != recibida)
            throw new ConflictException(
                    "VERSION_DESACTUALIZADA",
                    "El día no lectivo fue modificado; vuelva a cargarlo");
    }

    private String describir(DiaNoLectivo diaNoLectivo) {
        return "fecha="
                + diaNoLectivo.getFecha()
                + ",descripcion="
                + (diaNoLectivo.getDescripcion() == null
                        ? ""
                        : diaNoLectivo.getDescripcion());
    }

    private void registrarAuditoria(
            String accion, Long id, String anterior, String nuevoValor) {
        auditoria.registrar(
                accion,
                "CONFIGURACION_ACADEMICA",
                "DiaNoLectivo",
                id.toString(),
                ResultadoAuditoria.EXITOSO,
                null,
                anterior,
                nuevoValor);
    }
}
