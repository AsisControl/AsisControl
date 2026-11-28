package com.asiscontrol.service;

import com.asiscontrol.dto.PageResponse;
import com.asiscontrol.dto.auditoria.AuditoriaDtos.HistorialResponse;
import com.asiscontrol.dto.persona.PersonaDtos;
import com.asiscontrol.entity.Alumno;
import com.asiscontrol.entity.AlumnoApoderado;
import com.asiscontrol.entity.Apoderado;
import com.asiscontrol.entity.Docente;
import com.asiscontrol.entity.Nacionalidad;
import com.asiscontrol.entity.Persona;
import com.asiscontrol.entity.enums.Especialidad;
import com.asiscontrol.entity.enums.Parentesco;
import com.asiscontrol.entity.enums.ResultadoAuditoria;
import com.asiscontrol.exception.BusinessRuleException;
import com.asiscontrol.exception.ConflictException;
import com.asiscontrol.exception.FormFieldException;
import com.asiscontrol.exception.ResourceNotFoundException;
import com.asiscontrol.repository.AlumnoApoderadoRepository;
import com.asiscontrol.repository.AlumnoRepository;
import com.asiscontrol.repository.ApoderadoRepository;
import com.asiscontrol.repository.DocenteRepository;
import com.asiscontrol.repository.NacionalidadRepository;
import com.asiscontrol.repository.PersonaRepository;
import com.asiscontrol.util.PageUtils;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
public class PersonaService {

    private static final Set<String> ALUMNO_SORT_FIELDS =
            Set.of("codigoAlumno", "fechaCreacion", "persona.apellidoPaterno");
    private static final Set<String> DOCENTE_SORT_FIELDS =
            Set.of("codigoDocente", "fechaIngreso", "fechaCreacion", "persona.apellidoPaterno");
    private static final Set<String> APODERADO_SORT_FIELDS =
            Set.of("fechaCreacion", "persona.apellidoPaterno");

    private final PersonaRepository personaRepository;
    private final NacionalidadRepository nacionalidadRepository;
    private final AlumnoRepository alumnoRepository;
    private final DocenteRepository docenteRepository;
    private final ApoderadoRepository apoderadoRepository;
    private final AlumnoApoderadoRepository alumnoApoderadoRepository;
    private final CicloCuentaService cuentas;
    private final AuditoriaService auditoriaService;
    private final Validator validator;
    private final CodigoInstitucionalService codigos;

    public PersonaService(
            PersonaRepository personaRepository,
            NacionalidadRepository nacionalidadRepository,
            AlumnoRepository alumnoRepository,
            DocenteRepository docenteRepository,
            ApoderadoRepository apoderadoRepository,
            AlumnoApoderadoRepository alumnoApoderadoRepository,
            CicloCuentaService cuentas,
            AuditoriaService auditoriaService,
            Validator validator,
            CodigoInstitucionalService codigos) {
        this.personaRepository = personaRepository;
        this.nacionalidadRepository = nacionalidadRepository;
        this.alumnoRepository = alumnoRepository;
        this.docenteRepository = docenteRepository;
        this.apoderadoRepository = apoderadoRepository;
        this.alumnoApoderadoRepository = alumnoApoderadoRepository;
        this.cuentas = cuentas;
        this.auditoriaService = auditoriaService;
        this.validator = validator;
        this.codigos = codigos;
    }

    @Transactional
    public PersonaDtos.AlumnoResponse createAlumno(PersonaDtos.AlumnoCreateRequest request) {
        validateStudentRequest(request);
        if (request.persona().tipoDocumento() == null) {
            throw new FormFieldException("persona.tipoDocumento", "Selecciona un tipo de documento");
        }
        if (blankToNull(request.persona().numeroDocumento()) == null) {
            throw new FormFieldException("persona.numeroDocumento", "Ingresa el número de documento");
        }
        codigos.bloquearSecuenciaCuentas();
        Persona persona = resolvePersona(request.persona());
        if (alumnoRepository.existsByPersonaId(persona.getId())) {
            throw new ConflictException("DOCUMENTO_DUPLICADO",
                    "Ya hay una ficha de estudiante registrada con este documento");
        }
        String codigoAlumno = codigos.siguiente(CodigoInstitucionalService.Prefijo.EST);
        Alumno alumno = new Alumno();
        alumno.setPersona(persona);
        alumno.setCodigoAlumno(codigoAlumno);
        Alumno saved = alumnoRepository.save(alumno);
        auditCreate("Alumno", saved.getId());
        return toAlumnoResponse(saved, cuentas.altaDesdeFicha(persona, "ALUMNO", codigoAlumno));
    }

    @Transactional(readOnly = true)
    public PageResponse<PersonaDtos.AlumnoResponse> listAlumnos(
            String search, PersonaDtos.FiltroAlumnos filtro, int page, int size, String sortBy,
            Sort.Direction direction) {
        Pageable pageable =
                PageUtils.create(
                        page,
                        size,
                        sortBy,
                        direction,
                        ALUMNO_SORT_FIELDS,
                        "persona.apellidoPaterno");
        return PageResponse.from(
                alumnoRepository.searchActive(normalizeSearch(search), filtro.conMatricula(),
                        filtro.grado(), filtro.conApoderado(), pageable),
                this::toAlumnoResponse);
    }

    @Transactional(readOnly = true)
    public PersonaDtos.AlumnoResponse getAlumno(Long id) {
        return toAlumnoResponse(findAlumno(id));
    }

    @Transactional
    public PersonaDtos.AlumnoResponse updateAlumno(
            Long id, PersonaDtos.AlumnoUpdateRequest request) {
        validateStudentRequest(request);
        Alumno alumno = findAlumno(id);
        validateInstitutionalCode(alumno, request.codigoAlumno());
        Persona persona = alumno.getPersona();
        boolean corrigeIdentidad = hasIdentityChanges(persona, request.persona());
        String motivo = validateCorrectionReason(persona, request);
        java.util.Map<String, Object> personaAntes = snapshotPersona(persona);
        java.util.Map<String, Object> identidadAntes = snapshotStudentIdentity(persona);
        updatePersona(persona, request.persona());
        Alumno saved = alumnoRepository.save(alumno);
        if (corrigeIdentidad) {
            java.util.Map<String, Object> identidadDespues =
                    snapshotStudentIdentity(saved.getPersona());
            // Motivo admite 1000 caracteres en la ficha; el resumen de auditoría admite 500.
            // El detalle conserva el motivo completo sin cambiar la tabla compartida.
            identidadDespues.put("motivoCorreccion", motivo);
            auditoriaService.registrar(
                    "ACTUALIZAR_ALUMNO",
                    ModuloAuditoria.PERSONAS,
                    "Alumno",
                    id.toString(),
                    ResultadoAuditoria.EXITOSO,
                    motivo,
                    identidadAntes.toString(),
                    identidadDespues.toString());
        } else {
            String codigo = "codigoAlumno=" + saved.getCodigoAlumno();
            auditUpdate("Alumno", id, personaAntes, saved.getPersona(), codigo, codigo);
        }
        return toAlumnoResponse(saved);
    }

    private void validateInstitutionalCode(Alumno alumno, String requestedCode) {
        if (requestedCode != null && !requestedCode.equals(alumno.getCodigoAlumno())) {
            throw new FormFieldException(
                    "codigoAlumno",
                    "El código institucional es permanente y no se puede modificar");
        }
    }

    private String validateCorrectionReason(
            Persona persona, PersonaDtos.AlumnoUpdateRequest request) {
        String motivo = blankToNull(request.motivo());
        boolean motivoInvalido = motivo == null || motivo.length() < 10 || motivo.length() > 1000;
        if (hasIdentityChanges(persona, request.persona()) && motivoInvalido) {
            throw new FormFieldException(
                    "motivo", "Explica la corrección de identidad en 10 a 1000 caracteres");
        }
        return motivo;
    }

    private boolean hasIdentityChanges(Persona persona, PersonaDtos.PersonaRequest request) {
        return !Objects.equals(persona.getNombres(), request.nombres().trim())
                || !Objects.equals(persona.getApellidoPaterno(), request.apellidoPaterno().trim())
                || !Objects.equals(
                        blankToNull(persona.getApellidoMaterno()),
                        blankToNull(request.apellidoMaterno()))
                || !Objects.equals(persona.getTipoDocumento(), request.tipoDocumento())
                || !Objects.equals(
                        blankToNull(persona.getNumeroDocumento()),
                        upperOrNull(request.numeroDocumento()))
                || !Objects.equals(persona.getFechaNacimiento(), request.fechaNacimiento());
    }

    @Transactional
    public void deleteAlumno(Long id) {
        Alumno alumno = findAlumno(id);
        alumno.setActivo(false);
        alumnoRepository.save(alumno);
        cuentas.revocarDesdeFicha(alumno.getPersona().getId(), "ALUMNO");
        auditDelete("Alumno", id);
    }

    @Transactional
    public PersonaDtos.DocenteResponse createDocente(PersonaDtos.DocenteCreateRequest request) {
        if (request.fechaIngreso().isAfter(LocalDate.now())) {
            throw new BusinessRuleException(
                    "FECHA_INGRESO_INVALIDA", "La fecha de ingreso no puede estar en el futuro");
        }
        codigos.bloquearSecuenciaCuentas();
        Persona persona = resolvePersona(request.persona());
        if (docenteRepository.existsByPersonaId(persona.getId())) {
            throw duplicateProfile("docente");
        }
        Docente docente = new Docente();
        docente.setPersona(persona);
        docente.setCodigoDocente(codigos.siguiente(CodigoInstitucionalService.Prefijo.DOC));
        docente.setFechaIngreso(request.fechaIngreso());
        docente.setEspecialidad(request.especialidad());
        Docente saved = docenteRepository.save(docente);
        auditCreate("Docente", saved.getId());
        return toDocenteResponse(saved,
                cuentas.altaDesdeFicha(persona, "DOCENTE", saved.getCodigoDocente()));
    }

    @Transactional(readOnly = true)
    public PageResponse<PersonaDtos.DocenteResponse> listDocentes(
            String search,
            Especialidad especialidad,
            int page,
            int size,
            String sortBy,
            Sort.Direction direction) {
        Pageable pageable =
                PageUtils.create(
                        page,
                        size,
                        sortBy,
                        direction,
                        DOCENTE_SORT_FIELDS,
                        "persona.apellidoPaterno");
        return PageResponse.from(
                docenteRepository.searchActive(normalizeSearch(search), especialidad, pageable),
                this::toDocenteResponse);
    }

    @Transactional(readOnly = true)
    public PersonaDtos.DocenteResponse getDocente(Long id) {
        return toDocenteResponse(findDocente(id));
    }

    @Transactional
    public PersonaDtos.DocenteResponse updateDocente(
            Long id, PersonaDtos.DocenteUpdateRequest request) {
        if (request.fechaIngreso().isAfter(LocalDate.now())) {
            throw new BusinessRuleException(
                    "FECHA_INGRESO_INVALIDA", "La fecha de ingreso no puede estar en el futuro");
        }
        Docente docente = findDocente(id);
        if (request.codigoDocente() != null
                && !request.codigoDocente().trim().equalsIgnoreCase(docente.getCodigoDocente())) {
            throw new FormFieldException(
                    "codigoDocente",
                    "El código institucional es permanente y no se puede modificar");
        }
        java.util.Map<String, Object> personaAntes = snapshotPersona(docente.getPersona());
        String anterior = describirDocente(docente);
        updatePersona(docente.getPersona(), request.persona());
        docente.setFechaIngreso(request.fechaIngreso());
        docente.setEspecialidad(request.especialidad());
        Docente saved = docenteRepository.save(docente);
        auditUpdate(
                "Docente", id, personaAntes, saved.getPersona(), anterior, describirDocente(saved));
        return toDocenteResponse(saved);
    }

    @Transactional
    public void deleteDocente(Long id) {
        Docente docente = findDocente(id);
        docente.setActivo(false);
        docenteRepository.save(docente);
        cuentas.revocarDesdeFicha(docente.getPersona().getId(), "DOCENTE");
        auditDelete("Docente", id);
    }

    @Transactional
    public PersonaDtos.ApoderadoResponse createApoderado(
            PersonaDtos.ApoderadoCreateRequest request) {
        codigos.bloquearSecuenciaCuentas();
        return createApoderado(resolvePersona(request.persona()));
    }

    private PersonaDtos.ApoderadoResponse createApoderado(Persona persona) {
        if (apoderadoRepository.existsByPersonaId(persona.getId())) {
            throw duplicateProfile("apoderado");
        }
        Apoderado apoderado = new Apoderado();
        apoderado.setPersona(persona);
        apoderado.setCodigoApoderado(codigos.siguiente(CodigoInstitucionalService.Prefijo.APO));
        Apoderado saved = apoderadoRepository.save(apoderado);
        auditCreate("Apoderado", saved.getId());
        return toApoderadoResponse(saved,
                cuentas.altaDesdeFicha(persona, "APODERADO", saved.getCodigoApoderado()));
    }

    /**
     * Apoderado de una importación: si el documento ya es de un apoderado habilitado con la misma
     * identidad, lo reutiliza en lugar de rechazar la fila. Así un apoderado con varios hijos, o
     * repetido en el archivo, queda con un solo perfil y una sola cuenta.
     */
    @Transactional
    public PersonaDtos.ApoderadoResponse registrarOReutilizarApoderado(
            PersonaDtos.ApoderadoCreateRequest request) {
        codigos.bloquearSecuenciaCuentas();
        Persona persona = resolvePersona(request.persona());
        var existente = apoderadoRepository.findByPersonaIdAndActivoTrue(persona.getId());
        if (existente.isEmpty()) return createApoderado(persona);
        Apoderado apoderado = existente.get();
        return toApoderadoResponse(apoderado,
                cuentas.altaDesdeFicha(persona, "APODERADO", apoderado.getCodigoApoderado()));
    }

    /** Id de la nacionalidad habilitada con ese nombre, sin distinguir mayúsculas. */
    @Transactional(readOnly = true)
    public Long nacionalidadPorNombre(String nombre) {
        return nacionalidadRepository
                .findByNombreIgnoreCase(nombre.trim())
                .filter(nacionalidad -> nacionalidad.isActivo())
                .map(nacionalidad -> nacionalidad.getId())
                .orElseThrow(
                        () ->
                                new BusinessRuleException(
                                        "NACIONALIDAD_NO_ENCONTRADA",
                                        "No hay una nacionalidad registrada con el nombre "
                                                + nombre.trim()));
    }

    @Transactional(readOnly = true)
    public PageResponse<PersonaDtos.ApoderadoResponse> listApoderados(
            String search, Boolean conEstudiantes, int page, int size, String sortBy,
            Sort.Direction direction) {
        Pageable pageable =
                PageUtils.create(
                        page,
                        size,
                        sortBy,
                        direction,
                        APODERADO_SORT_FIELDS,
                        "persona.apellidoPaterno");
        return PageResponse.from(
                apoderadoRepository.searchActive(normalizeSearch(search), conEstudiantes, pageable),
                this::toApoderadoResponse);
    }

    @Transactional(readOnly = true)
    public PersonaDtos.ApoderadoResponse getApoderado(Long id) {
        return toApoderadoResponse(findApoderado(id));
    }

    @Transactional
    public PersonaDtos.ApoderadoResponse updateApoderado(
            Long id, PersonaDtos.ApoderadoUpdateRequest request) {
        Apoderado apoderado = findApoderado(id);
        java.util.Map<String, Object> personaAntes = snapshotPersona(apoderado.getPersona());
        updatePersona(apoderado.getPersona(), request.persona());
        Apoderado saved = apoderadoRepository.save(apoderado);
        auditUpdate("Apoderado", id, personaAntes, saved.getPersona(), null, null);
        return toApoderadoResponse(saved);
    }

    @Transactional
    public void deleteApoderado(Long id) {
        Apoderado apoderado = findApoderado(id);
        apoderado.setActivo(false);
        apoderadoRepository.save(apoderado);
        cuentas.revocarDesdeFicha(apoderado.getPersona().getId(), "APODERADO");
        alumnoApoderadoRepository
                .findAllByApoderadoIdAndActivoTrue(id)
                .forEach(
                        link -> {
                            link.setActivo(false);
                            alumnoApoderadoRepository.save(link);
                        });
        auditDelete("Apoderado", id);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PersonaDtos.ApoderadoVinculadoResponse linkGuardian(
            Long alumnoId, PersonaDtos.VinculoApoderadoRequest request) {
        return guardarNuevoVinculo(alumnoId, request, true);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PersonaDtos.ApoderadoVinculadoResponse crearVinculoIndividual(
            Long alumnoId, PersonaDtos.VinculoApoderadoRequest request) {
        return guardarNuevoVinculo(alumnoId, request, false);
    }

    private PersonaDtos.ApoderadoVinculadoResponse guardarNuevoVinculo(
            Long alumnoId, PersonaDtos.VinculoApoderadoRequest request, boolean permitirReactivacionInterna) {
        Alumno alumno = bloquearAlumnoParaVinculos(alumnoId);
        Apoderado apoderado = apoderadoHabilitado(request.apoderadoId());
        if (alumno.getPersona().getId().equals(apoderado.getPersona().getId())) {
            throw new BusinessRuleException("VINCULO_MISMA_PERSONA",
                    "El estudiante y su apoderado deben ser personas distintas");
        }
        var existente = alumnoApoderadoRepository.findByAlumnoIdAndApoderadoId(alumnoId, apoderado.getId());
        if (existente.filter(AlumnoApoderado::isActivo).isPresent()) {
            throw new ConflictException(
                    "VINCULO_DUPLICADO", "El apoderado ya está vinculado al estudiante.");
        }
        if (existente.isPresent() && !permitirReactivacionInterna) {
            throw new ConflictException("VINCULO_FINALIZADO",
                    "Este vínculo está finalizado. Reactívalo desde Gestionar vínculos indicando el motivo.");
        }
        String motivo = existente.isPresent() ? "Reactivación al registrar la relación familiar" : null;
        var link = existente.orElseGet(AlumnoApoderado::new);
        var anterior = existente.map(this::valoresVinculo).orElse(null);
        link.setAlumno(alumno);
        link.setApoderado(apoderado);
        if (request.principal()) {
            sustituirPrincipal(link, motivo == null ? "Se designó otro apoderado principal" : motivo);
        }
        link.setParentesco(Objects.requireNonNullElse(request.parentesco(), Parentesco.NO_DECLARADO));
        link.setPrincipal(request.principal());
        link.setActivo(true);
        alumnoApoderadoRepository.saveAndFlush(link);
        auditarVinculo(existente.isPresent() ? "REACTIVAR_VINCULO_APODERADO" : "ASOCIAR_APODERADO",
                link, motivo, anterior);
        return toGuardianLink(link);
    }

    @Transactional(readOnly = true)
    public List<PersonaDtos.VinculoApoderadoResponse> listarVinculosApoderados(Long alumnoId) {
        if (!alumnoRepository.existsById(alumnoId)) {
            throw new ResourceNotFoundException("ALUMNO_NO_ENCONTRADO", "No se encontró el estudiante.");
        }
        return alumnoApoderadoRepository.findAllByAlumnoIdOrderByActivoDescPrincipalDescIdAsc(alumnoId)
                .stream().map(this::respuestaVinculo).toList();
    }

    @Transactional(readOnly = true)
    public List<HistorialResponse> historialVinculo(
            Long alumnoId, Long apoderadoId) {
        return auditoriaService.historial("AlumnoApoderado", buscarVinculo(alumnoId, apoderadoId).getId());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PersonaDtos.VinculoApoderadoResponse editarVinculo(Long alumnoId, Long apoderadoId,
            PersonaDtos.EdicionVinculoApoderadoRequest request) {
        String motivo = motivoVinculo(request.motivo());
        bloquearAlumnoParaVinculos(alumnoId);
        var link = buscarVinculo(alumnoId, apoderadoId);
        if (!link.isActivo()) {
            throw new ConflictException("VINCULO_FINALIZADO", "Reactiva el vínculo antes de editarlo.");
        }
        apoderadoHabilitado(apoderadoId);
        var parentesco = Objects.requireNonNullElse(request.parentesco(), Parentesco.NO_DECLARADO);
        if (link.isPrincipal() == request.principal() && link.getParentesco() == parentesco) {
            return respuestaVinculo(link);
        }
        String anterior = valoresVinculo(link);
        if (request.principal()) {
            sustituirPrincipal(link, motivo);
        } else {
            comprobarRetiroPrincipal(link);
        }
        link.setPrincipal(request.principal());
        link.setParentesco(parentesco);
        auditarVinculo("EDITAR_VINCULO_APODERADO", link, motivo, anterior);
        return respuestaVinculo(link);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PersonaDtos.VinculoApoderadoResponse cambiarEstadoVinculo(Long alumnoId, Long apoderadoId,
            PersonaDtos.EstadoVinculoApoderadoRequest request) {
        return cambiarEstadoVinculo(alumnoId, apoderadoId, request.activo(), motivoVinculo(request.motivo()));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void finalizarVinculo(Long alumnoId, Long apoderadoId, String motivo) {
        cambiarEstadoVinculo(alumnoId, apoderadoId, false, motivoVinculo(motivo));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void unlinkGuardian(Long alumnoId, Long apoderadoId) {
        cambiarEstadoVinculo(alumnoId, apoderadoId, false, "Finalización de la relación familiar");
    }

    private PersonaDtos.VinculoApoderadoResponse cambiarEstadoVinculo(
            Long alumnoId, Long apoderadoId, boolean activo, String motivo) {
        bloquearAlumnoParaVinculos(alumnoId);
        var link = buscarVinculo(alumnoId, apoderadoId);
        if (link.isActivo() == activo) {
            return respuestaVinculo(link);
        }
        String anterior = valoresVinculo(link);
        if (activo) {
            apoderadoHabilitado(apoderadoId);
            if (alumnoApoderadoRepository.existsByAlumnoIdAndPrincipalTrueAndActivoTrue(alumnoId)) {
                link.setPrincipal(false);
            }
        } else {
            comprobarRetiroPrincipal(link);
        }
        link.setActivo(activo);
        auditarVinculo(activo ? "REACTIVAR_VINCULO_APODERADO" : "DESVINCULAR_APODERADO", link, motivo, anterior);
        return respuestaVinculo(link);
    }

    private Alumno bloquearAlumnoParaVinculos(Long alumnoId) {
        var alumno = findAlumno(alumnoId);
        personaRepository.lockActiveById(alumno.getPersona().getId())
                .orElseThrow(() -> new BusinessRuleException("ALUMNO_INACTIVO", "El estudiante no está habilitado."));
        return alumno;
    }

    private Apoderado apoderadoHabilitado(Long apoderadoId) {
        var apoderado = findApoderado(apoderadoId);
        if (!apoderado.getPersona().isActivo()) {
            throw new BusinessRuleException("APODERADO_INACTIVO", "La ficha del apoderado no está habilitada.");
        }
        return apoderado;
    }

    private AlumnoApoderado buscarVinculo(Long alumnoId, Long apoderadoId) {
        return alumnoApoderadoRepository.findByAlumnoIdAndApoderadoId(alumnoId, apoderadoId)
                .orElseThrow(() -> new ResourceNotFoundException("VINCULO_NO_ENCONTRADO",
                        "No se encontró el vínculo entre el estudiante y el apoderado."));
    }

    private void comprobarRetiroPrincipal(AlumnoApoderado link) {
        if (link.isPrincipal() && alumnoApoderadoRepository.findAllByAlumnoIdAndActivoTrue(link.getAlumno().getId())
                .stream().anyMatch(otro -> !otro.getId().equals(link.getId()))) {
            throw new BusinessRuleException("APODERADO_PRINCIPAL_REQUERIDO",
                    "Designa otro apoderado principal antes de retirar al actual.");
        }
    }

    private void sustituirPrincipal(AlumnoApoderado link, String motivo) {
        for (var otro : alumnoApoderadoRepository.findAllByAlumnoIdAndActivoTrue(link.getAlumno().getId())) {
            if (otro.isPrincipal() && !otro.getId().equals(link.getId())) {
                String anterior = valoresVinculo(otro);
                otro.setPrincipal(false);
                auditarVinculo("EDITAR_VINCULO_APODERADO", otro, motivo, anterior);
            }
        }
    }

    private String motivoVinculo(String motivo) {
        if (motivo == null || motivo.isBlank() || motivo.length() > 500) {
            throw new BusinessRuleException("MOTIVO_VINCULO_INVALIDO", "Ingresa un motivo de hasta 500 caracteres.");
        }
        return motivo.strip();
    }

    private PersonaDtos.VinculoApoderadoResponse respuestaVinculo(AlumnoApoderado link) {
        var apoderado = link.getApoderado();
        return new PersonaDtos.VinculoApoderadoResponse(link.getId(), apoderado.getId(),
                apoderado.getPersona().nombreCompleto(), link.getParentesco(), link.isPrincipal(),
                link.isActivo(), apoderado.isActivo() && apoderado.getPersona().isActivo());
    }

    private String valoresVinculo(AlumnoApoderado link) {
        return "alumno=" + link.getAlumno().getId() + ",apoderado=" + link.getApoderado().getId()
                + ",parentesco=" + link.getParentesco() + ",principal=" + link.isPrincipal()
                + ",activo=" + link.isActivo();
    }

    private void auditarVinculo(String accion, AlumnoApoderado link, String motivo, String anterior) {
        auditoriaService.registrar(accion, ModuloAuditoria.PERSONAS, "AlumnoApoderado", link.getId().toString(),
                ResultadoAuditoria.EXITOSO, motivo, anterior, valoresVinculo(link));
    }

    @Transactional(readOnly = true)
    public List<PersonaDtos.NacionalidadResponse> listNationalities() {
        return nacionalidadRepository.findAllByActivoTrueOrderByNombreAsc().stream()
                .map(
                        value ->
                                new PersonaDtos.NacionalidadResponse(
                                        value.getId(), value.getNombre()))
                .toList();
    }

    @Transactional
    public PersonaDtos.NacionalidadResponse createNationality(
            PersonaDtos.NacionalidadRequest request) {
        nacionalidadRepository
                .findByNombreIgnoreCase(request.nombre().trim())
                .ifPresent(
                        existing -> {
                            throw new ConflictException(
                                    "NACIONALIDAD_DUPLICADA", "La nacionalidad ya existe");
                        });
        Nacionalidad nationality = new Nacionalidad();
        nationality.setNombre(request.nombre().trim());
        Nacionalidad saved = nacionalidadRepository.save(nationality);
        return new PersonaDtos.NacionalidadResponse(saved.getId(), saved.getNombre());
    }

    public Persona createPersona(PersonaDtos.PersonaRequest request) {
        validateUniquePersona(request, null);
        Persona persona = new Persona();
        applyPersonaRequest(persona, request);
        return personaRepository.save(persona);
    }

    /**
     * Si el tipo y número de documento ya están registrados, reutiliza a la persona
     * (por ejemplo, una docente que también es apoderada)
     * en lugar de duplicarla, y conserva sus datos registrados. El documento por si solo no basta:
     * un digito mal escrito asignaria el perfil a otra persona, asi que tambien deben coincidir
     * los nombres, el primer apellido y la fecha de nacimiento.
     */
    public Persona resolvePersona(PersonaDtos.PersonaRequest request) {
        String numeroDocumento = upperOrNull(request.numeroDocumento());
        if (request.tipoDocumento() == null || numeroDocumento == null) {
            return createPersona(request);
        }
        return personaRepository
                .findByTipoDocumentoAndNumeroDocumento(request.tipoDocumento(), numeroDocumento)
                .map(existing -> reusablePersona(existing, request))
                .orElseGet(() -> createPersona(request));
    }

    private Persona reusablePersona(Persona existing, PersonaDtos.PersonaRequest request) {
        if (!existing.isActivo()) {
            throw new ConflictException(
                    "PERSONA_INHABILITADA",
                    "La persona con este documento esta inhabilitada. Reactivala antes de"
                            + " registrarle otro perfil");
        }
        if (!sameIdentity(existing, request)) {
            throw new ConflictException(
                    "DOCUMENTO_CON_OTROS_DATOS",
                    "El documento ya esta registrado con otros nombres o fecha de nacimiento."
                            + " Verifica el documento");
        }
        return personaRepository.lockActiveById(existing.getId())
                .orElseThrow(() -> new ConflictException("PERSONA_INHABILITADA",
                        "La persona está inhabilitada. Reactívala antes de registrarle otro perfil"));
    }

    private boolean sameIdentity(Persona persona, PersonaDtos.PersonaRequest request) {
        boolean sameMaternalName =
                persona.getApellidoMaterno() == null
                        || blankToNull(request.apellidoMaterno()) == null
                        || comparableName(persona.getApellidoMaterno())
                                .equals(comparableName(request.apellidoMaterno()));
        return Objects.equals(persona.getFechaNacimiento(), request.fechaNacimiento())
                && comparableName(persona.getNombres()).equals(comparableName(request.nombres()))
                && comparableName(persona.getApellidoPaterno())
                        .equals(comparableName(request.apellidoPaterno()))
                && sameMaternalName;
    }

    /** "  José  María " y "jose maria" se consideran el mismo nombre. */
    private String comparableName(String value) {
        String text = value == null ? "" : value;
        return Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .trim()
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }

    private ConflictException duplicateProfile(String perfil) {
        return new ConflictException(
                "PERFIL_DUPLICADO", "La persona con este documento ya esta registrada como " + perfil);
    }

    private void updatePersona(Persona persona, PersonaDtos.PersonaRequest request) {
        validateUniquePersona(request, persona.getId());
        applyPersonaRequest(persona, request);
        personaRepository.save(persona);
    }

    private void applyPersonaRequest(Persona persona, PersonaDtos.PersonaRequest request) {
        persona.setNombres(request.nombres().trim());
        persona.setApellidoPaterno(request.apellidoPaterno().trim());
        persona.setApellidoMaterno(blankToNull(request.apellidoMaterno()));
        persona.setTipoDocumento(request.tipoDocumento());
        persona.setNumeroDocumento(upperOrNull(request.numeroDocumento()));
        persona.setNacionalidad(
                request.nacionalidadId() == null
                        ? null
                        : nacionalidadRepository
                                .findByIdAndActivoTrue(request.nacionalidadId())
                                .orElseThrow(
                                        () ->
                                                new ResourceNotFoundException(
                                                        "NACIONALIDAD_NO_ENCONTRADA",
                                                        "No se encontro la nacionalidad")));
        persona.setSexo(request.sexo());
        persona.setFechaNacimiento(request.fechaNacimiento());
        persona.setTelefono(blankToNull(request.telefono()));
        String email = blankToNull(request.correo());
        persona.setCorreo(email == null ? null : email.toLowerCase(Locale.ROOT));
        persona.setDireccion(blankToNull(request.direccion()));
    }

    private void validateUniquePersona(PersonaDtos.PersonaRequest request, Long currentId) {
        validateUniqueDocument(request, currentId);
        validateUniqueEmail(request.correo(), currentId);
    }

    private void validateUniqueDocument(PersonaDtos.PersonaRequest request, Long currentId) {
        if (request.tipoDocumento() == null || blankToNull(request.numeroDocumento()) == null) {
            return;
        }
        personaRepository
                .findByTipoDocumentoAndNumeroDocumento(
                        request.tipoDocumento(), upperOrNull(request.numeroDocumento()))
                .filter(existing -> currentId == null || !existing.getId().equals(currentId))
                .ifPresent(
                        existing -> {
                            throw new ConflictException(
                                    "DOCUMENTO_DUPLICADO",
                                    "Ya existe una persona con el mismo documento");
                        });
    }

    private void validateUniqueEmail(String correo, Long currentId) {
        if (blankToNull(correo) == null) {
            return;
        }
        personaRepository
                .findByCorreoIgnoreCase(correo.trim())
                .filter(existing -> currentId == null || !existing.getId().equals(currentId))
                .ifPresent(
                        existing -> {
                            throw new ConflictException(
                                    "CORREO_DUPLICADO",
                                    "Ya existe una persona con el mismo correo");
                        });
    }

    private Alumno findAlumno(Long id) {
        return alumnoRepository
                .findByIdAndActivoTrue(id)
                .orElseThrow(
                        () ->
                                new ResourceNotFoundException(
                                        "ALUMNO_NO_ENCONTRADO", "No se encontro el alumno"));
    }

    private Docente findDocente(Long id) {
        return docenteRepository
                .findByIdAndActivoTrue(id)
                .orElseThrow(
                        () ->
                                new ResourceNotFoundException(
                                        "DOCENTE_NO_ENCONTRADO", "No se encontro el docente"));
    }

    private Apoderado findApoderado(Long id) {
        return apoderadoRepository
                .findByIdAndActivoTrue(id)
                .orElseThrow(
                        () ->
                                new ResourceNotFoundException(
                                        "APODERADO_NO_ENCONTRADO", "No se encontro el apoderado"));
    }

    private PersonaDtos.AlumnoResponse toAlumnoResponse(Alumno alumno) {
        return toAlumnoResponse(alumno, null);
    }

    private PersonaDtos.AlumnoResponse toAlumnoResponse(
            Alumno alumno, PersonaDtos.CredencialAcceso credencial) {
        List<PersonaDtos.ApoderadoVinculadoResponse> guardians =
                alumnoApoderadoRepository.findAllByAlumnoIdAndActivoTrue(alumno.getId()).stream()
                        .map(this::toGuardianLink)
                        .toList();
        return new PersonaDtos.AlumnoResponse(
                alumno.getId(),
                alumno.getCodigoAlumno(),
                toPersonaResponse(alumno.getPersona()),
                guardians,
                  alumno.isActivo(),
                  credencial);
    }

    private PersonaDtos.DocenteResponse toDocenteResponse(Docente docente) {
        return toDocenteResponse(docente, null);
    }

    private PersonaDtos.DocenteResponse toDocenteResponse(
            Docente docente, PersonaDtos.CredencialAcceso credencial) {
        return new PersonaDtos.DocenteResponse(
                docente.getId(),
                docente.getCodigoDocente(),
                docente.getFechaIngreso(),
                docente.getEspecialidad(),
                toPersonaResponse(docente.getPersona()),
                  docente.isActivo(),
                  credencial);
    }

    private PersonaDtos.ApoderadoResponse toApoderadoResponse(Apoderado apoderado) {
        return toApoderadoResponse(apoderado, null);
    }

    private PersonaDtos.ApoderadoResponse toApoderadoResponse(
            Apoderado apoderado, PersonaDtos.CredencialAcceso credencial) {
        List<PersonaDtos.AlumnoVinculadoResponse> students =
                alumnoApoderadoRepository
                        .findAllByApoderadoIdAndActivoTrue(apoderado.getId())
                        .stream()
                        .map(
                                link ->
                                        new PersonaDtos.AlumnoVinculadoResponse(
                                                link.getId(),
                                                link.getAlumno().getId(),
                                                link.getAlumno().getCodigoAlumno(),
                                                link.getAlumno().getPersona().nombreCompleto(),
                                                link.getParentesco(),
                                                link.isPrincipal()))
                        .toList();
        return new PersonaDtos.ApoderadoResponse(
                apoderado.getId(),
                apoderado.getCodigoApoderado(),
                toPersonaResponse(apoderado.getPersona()),
                students,
                  apoderado.isActivo(),
                  credencial);
    }

    private PersonaDtos.PersonaResponse toPersonaResponse(Persona persona) {
        return new PersonaDtos.PersonaResponse(
                persona.getId(),
                persona.getNombres(),
                persona.getApellidoPaterno(),
                persona.getApellidoMaterno(),
                persona.nombreCompleto(),
                persona.getTipoDocumento(),
                persona.getNumeroDocumento(),
                persona.getNacionalidad() == null ? null : persona.getNacionalidad().getId(),
                persona.getNacionalidad() == null ? null : persona.getNacionalidad().getNombre(),
                persona.getSexo(),
                persona.getFechaNacimiento(),
                persona.getTelefono(),
                persona.getCorreo(),
                persona.getDireccion(),
                persona.isActivo(),
                persona.getFechaCreacion(),
                persona.getFechaActualizacion());
    }

    private PersonaDtos.ApoderadoVinculadoResponse toGuardianLink(AlumnoApoderado link) {
        return new PersonaDtos.ApoderadoVinculadoResponse(
                link.getId(),
                link.getApoderado().getId(),
                link.getApoderado().getPersona().nombreCompleto(),
                link.getParentesco(),
                link.isPrincipal());
    }

    private void auditCreate(String entity, Long id) {
        auditoriaService.registrar(
                "CREAR_" + entity.toUpperCase(),
                ModuloAuditoria.PERSONAS,
                entity,
                id.toString(),
                ResultadoAuditoria.EXITOSO,
                null,
                null,
                null);
    }

    /**
     * Para los datos personales solo se registran los nombres de los campos modificados, no sus
     * valores, para no copiar documentos, telefonos o direcciones a la tabla de auditoria.
     */
    private void auditUpdate(
            String entity,
            Long id,
            java.util.Map<String, Object> personaAntes,
            Persona personaDespues,
            String anterior,
            String nuevo) {
        java.util.Map<String, Object> despues = snapshotPersona(personaDespues);
        String campos =
                personaAntes.keySet().stream()
                        .filter(
                                campo ->
                                        !java.util.Objects.equals(
                                                personaAntes.get(campo), despues.get(campo)))
                        .collect(java.util.stream.Collectors.joining("|"));
        auditoriaService.registrar(
                "ACTUALIZAR_" + entity.toUpperCase(),
                ModuloAuditoria.PERSONAS,
                entity,
                id.toString(),
                ResultadoAuditoria.EXITOSO,
                campos.isEmpty() ? null : "datosPersonalesModificados=" + campos,
                anterior,
                nuevo);
    }

    private java.util.Map<String, Object> snapshotStudentIdentity(Persona persona) {
        java.util.Map<String, Object> datos = new java.util.LinkedHashMap<>();
        datos.put("nombres", persona.getNombres());
        datos.put("apellidoPaterno", persona.getApellidoPaterno());
        datos.put("apellidoMaterno", persona.getApellidoMaterno());
        datos.put("tipoDocumento", persona.getTipoDocumento());
        datos.put("numeroDocumento", persona.getNumeroDocumento());
        datos.put("fechaNacimiento", persona.getFechaNacimiento());
        return datos;
    }

    private java.util.Map<String, Object> snapshotPersona(Persona persona) {
        java.util.Map<String, Object> datos = new java.util.LinkedHashMap<>();
        datos.put("nombres", persona.getNombres());
        datos.put("apellidoPaterno", persona.getApellidoPaterno());
        datos.put("apellidoMaterno", persona.getApellidoMaterno());
        datos.put("tipoDocumento", persona.getTipoDocumento());
        datos.put("numeroDocumento", persona.getNumeroDocumento());
        datos.put(
                "nacionalidad",
                persona.getNacionalidad() == null ? null : persona.getNacionalidad().getId());
        datos.put("sexo", persona.getSexo());
        datos.put("fechaNacimiento", persona.getFechaNacimiento());
        datos.put("telefono", persona.getTelefono());
        datos.put("correo", persona.getCorreo());
        datos.put("direccion", persona.getDireccion());
        return datos;
    }

    private String describirDocente(Docente docente) {
        return "codigoDocente="
                + docente.getCodigoDocente()
                + ",fechaIngreso="
                + docente.getFechaIngreso()
                + ",especialidad="
                + docente.getEspecialidad();
    }

    private void auditDelete(String entity, Long id) {
        auditoriaService.registrar(
                "DESACTIVAR_" + entity.toUpperCase(),
                ModuloAuditoria.PERSONAS,
                entity,
                id.toString(),
                ResultadoAuditoria.EXITOSO,
                null,
                null,
                null);
    }

    private String normalizeSearch(String search) {
        return search == null || search.isBlank() ? null : search.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String upperOrNull(String value) {
        String normalized = blankToNull(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private <T> void validateStudentRequest(T request) {
        var violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
    }
}
