package com.asiscontrol.service;

import com.asiscontrol.dto.PageResponse;
import com.asiscontrol.dto.persona.PersonaDtos;
import com.asiscontrol.entity.AccesoUsuario;
import com.asiscontrol.entity.Alumno;
import com.asiscontrol.entity.AlumnoApoderado;
import com.asiscontrol.entity.Apoderado;
import com.asiscontrol.entity.Docente;
import com.asiscontrol.entity.Nacionalidad;
import com.asiscontrol.entity.Persona;
import com.asiscontrol.entity.enums.Especialidad;
import com.asiscontrol.entity.enums.EstadoAcceso;
import com.asiscontrol.entity.enums.EstadoSesion;
import com.asiscontrol.entity.enums.ResultadoAuditoria;
import com.asiscontrol.exception.BusinessRuleException;
import com.asiscontrol.exception.ConflictException;
import com.asiscontrol.exception.FormFieldException;
import com.asiscontrol.exception.ResourceNotFoundException;
import com.asiscontrol.repository.AccesoUsuarioRepository;
import com.asiscontrol.repository.AdministradorRepository;
import com.asiscontrol.repository.AlumnoApoderadoRepository;
import com.asiscontrol.repository.AlumnoRepository;
import com.asiscontrol.repository.ApoderadoRepository;
import com.asiscontrol.repository.ContadorCodigoAlumnoRepository;
import com.asiscontrol.repository.DocenteRepository;
import com.asiscontrol.repository.NacionalidadRepository;
import com.asiscontrol.repository.PersonaRepository;
import com.asiscontrol.repository.SesionAccesoRepository;
import com.asiscontrol.util.PageUtils;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final AccesoUsuarioRepository usuarioRepository;
    private final SesionAccesoRepository sesionRepository;
    private final AuditoriaService auditoriaService;
    private final Validator validator;
    private final ContadorCodigoAlumnoRepository contadorCodigoAlumnoRepository;
    private final AdministradorRepository administradorRepository;

    public PersonaService(
            PersonaRepository personaRepository,
            NacionalidadRepository nacionalidadRepository,
            AlumnoRepository alumnoRepository,
            DocenteRepository docenteRepository,
            ApoderadoRepository apoderadoRepository,
            AlumnoApoderadoRepository alumnoApoderadoRepository,
            AccesoUsuarioRepository usuarioRepository,
            SesionAccesoRepository sesionRepository,
            AuditoriaService auditoriaService,
            Validator validator,
            ContadorCodigoAlumnoRepository contadorCodigoAlumnoRepository,
            AdministradorRepository administradorRepository) {
        this.personaRepository = personaRepository;
        this.nacionalidadRepository = nacionalidadRepository;
        this.alumnoRepository = alumnoRepository;
        this.docenteRepository = docenteRepository;
        this.apoderadoRepository = apoderadoRepository;
        this.alumnoApoderadoRepository = alumnoApoderadoRepository;
        this.usuarioRepository = usuarioRepository;
        this.sesionRepository = sesionRepository;
        this.auditoriaService = auditoriaService;
        this.validator = validator;
        this.contadorCodigoAlumnoRepository = contadorCodigoAlumnoRepository;
        this.administradorRepository = administradorRepository;
    }

    @Transactional
    public PersonaDtos.AlumnoResponse createAlumno(PersonaDtos.AlumnoCreateRequest request) {
        validateStudentRequest(request);
        String codigoAlumno = resolveAlumnoCode(request.codigoAlumno());
        Persona persona = createPersona(request.persona());
        Alumno alumno = new Alumno();
        alumno.setPersona(persona);
        alumno.setCodigoAlumno(codigoAlumno);
        Alumno saved = alumnoRepository.save(alumno);
        auditCreate("Alumno", saved.getId());
        return toAlumnoResponse(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<PersonaDtos.AlumnoResponse> listAlumnos(
            String search, int page, int size, String sortBy, Sort.Direction direction) {
        Pageable pageable =
                PageUtils.create(
                        page,
                        size,
                        sortBy,
                        direction,
                        ALUMNO_SORT_FIELDS,
                        "persona.apellidoPaterno");
        return PageResponse.from(
                alumnoRepository.searchActive(normalizeSearch(search), pageable),
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

    /** El alta web genera códigos; las importaciones internas conservan su código legado. */
    private String resolveAlumnoCode(String requestedCode) {
        String codigoAlumno = upperOrNull(requestedCode);
        if (codigoAlumno == null) {
            return generateAlumnoCode();
        }
        if (alumnoRepository.findByCodigoAlumnoIgnoreCase(codigoAlumno).isPresent()) {
            throw new ConflictException(
                    "CODIGO_ALUMNO_DUPLICADO", "El código del alumno ya existe");
        }
        return codigoAlumno;
    }

    /** El bloqueo y el guardado del contador pertenecen a la transacción del alta. */
    private String generateAlumnoCode() {
        contadorCodigoAlumnoRepository.initializeCounter();
        var contador = contadorCodigoAlumnoRepository.lockCounter();
        String codigoAlumno;
        do {
            contador.setUltimoValor(Math.incrementExact(contador.getUltimoValor()));
            codigoAlumno = String.format(Locale.ROOT, "ESTU-%06d", contador.getUltimoValor());
        } while (alumnoRepository.findByCodigoAlumnoIgnoreCase(codigoAlumno).isPresent());
        contadorCodigoAlumnoRepository.save(contador);
        return codigoAlumno;
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
        deactivateProfile(alumno.getPersona(), "ALUMNO");
        auditDelete("Alumno", id);
    }

    @Transactional
    public PersonaDtos.DocenteResponse createDocente(PersonaDtos.DocenteCreateRequest request) {
        if (request.fechaIngreso().isAfter(LocalDate.now())) {
            throw new BusinessRuleException(
                    "FECHA_INGRESO_INVALIDA", "La fecha de ingreso no puede estar en el futuro");
        }
        if (docenteRepository
                .findByCodigoDocenteIgnoreCase(request.codigoDocente().trim())
                .isPresent()) {
            throw new ConflictException(
                    "CODIGO_DOCENTE_DUPLICADO", "El codigo del docente ya existe");
        }
        Persona persona = createPersona(request.persona());
        Docente docente = new Docente();
        docente.setPersona(persona);
        docente.setCodigoDocente(request.codigoDocente().trim().toUpperCase());
        docente.setFechaIngreso(request.fechaIngreso());
        docente.setEspecialidad(request.especialidad());
        Docente saved = docenteRepository.save(docente);
        auditCreate("Docente", saved.getId());
        return toDocenteResponse(saved);
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
        docenteRepository
                .findByCodigoDocenteIgnoreCase(request.codigoDocente().trim())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(
                        existing -> {
                            throw new ConflictException(
                                    "CODIGO_DOCENTE_DUPLICADO", "El codigo del docente ya existe");
                        });
        java.util.Map<String, Object> personaAntes = snapshotPersona(docente.getPersona());
        String anterior = describirDocente(docente);
        updatePersona(docente.getPersona(), request.persona());
        docente.setCodigoDocente(request.codigoDocente().trim().toUpperCase());
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
        deactivateProfile(docente.getPersona(), "DOCENTE");
        auditDelete("Docente", id);
    }

    @Transactional
    public PersonaDtos.ApoderadoResponse createApoderado(
            PersonaDtos.ApoderadoCreateRequest request) {
        Persona persona = createPersona(request.persona());
        Apoderado apoderado = new Apoderado();
        apoderado.setPersona(persona);
        Apoderado saved = apoderadoRepository.save(apoderado);
        auditCreate("Apoderado", saved.getId());
        return toApoderadoResponse(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<PersonaDtos.ApoderadoResponse> listApoderados(
            String search, int page, int size, String sortBy, Sort.Direction direction) {
        Pageable pageable =
                PageUtils.create(
                        page,
                        size,
                        sortBy,
                        direction,
                        APODERADO_SORT_FIELDS,
                        "persona.apellidoPaterno");
        return PageResponse.from(
                apoderadoRepository.searchActive(normalizeSearch(search), pageable),
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
        deactivateProfile(apoderado.getPersona(), "APODERADO");
        alumnoApoderadoRepository
                .findAllByApoderadoIdAndActivoTrue(id)
                .forEach(
                        link -> {
                            link.setActivo(false);
                            alumnoApoderadoRepository.save(link);
                        });
        auditDelete("Apoderado", id);
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public PersonaDtos.ApoderadoVinculadoResponse linkGuardian(
            Long alumnoId, PersonaDtos.VinculoApoderadoRequest request) {
        Alumno alumno = findAlumno(alumnoId);
        personaRepository
                .lockActiveById(alumno.getPersona().getId())
                .orElseThrow(
                        () ->
                                new BusinessRuleException(
                                        "ALUMNO_INACTIVO", "El estudiante no está habilitado"));
        Apoderado apoderado = findApoderado(request.apoderadoId());
        if (alumnoApoderadoRepository
                .findByAlumnoIdAndApoderadoIdAndActivoTrue(alumnoId, apoderado.getId())
                .isPresent()) {
            throw new ConflictException(
                    "VINCULO_DUPLICADO", "El apoderado ya esta vinculado al alumno");
        }
        if (request.principal()) {
            alumnoApoderadoRepository
                    .findAllByAlumnoIdAndActivoTrue(alumnoId)
                    .forEach(
                            link -> {
                                link.setPrincipal(false);
                                alumnoApoderadoRepository.save(link);
                            });
        }
        AlumnoApoderado link =
                alumnoApoderadoRepository
                        .findByAlumnoIdAndApoderadoId(alumnoId, apoderado.getId())
                        .orElseGet(
                                () -> {
                                    AlumnoApoderado nuevo = new AlumnoApoderado();
                                    nuevo.setAlumno(alumno);
                                    nuevo.setApoderado(apoderado);
                                    return nuevo;
                                });
        link.setParentesco(
                request.parentesco() == null
                        ? com.asiscontrol.entity.enums.Parentesco.NO_DECLARADO
                        : request.parentesco());
        link.setPrincipal(request.principal());
        link.setActivo(true);
        alumnoApoderadoRepository.saveAndFlush(link);
        auditoriaService.registrar(
                "ASOCIAR_APODERADO",
                ModuloAuditoria.PERSONAS,
                "AlumnoApoderado",
                link.getId().toString(),
                ResultadoAuditoria.EXITOSO,
                null,
                null,
                "alumno=" + alumnoId + ",apoderado=" + apoderado.getId());
        return toGuardianLink(link);
    }

    @Transactional
    public void unlinkGuardian(Long alumnoId, Long apoderadoId) {
        AlumnoApoderado link =
                alumnoApoderadoRepository
                        .findByAlumnoIdAndApoderadoIdAndActivoTrue(alumnoId, apoderadoId)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "VINCULO_NO_ENCONTRADO",
                                                "No se encontro el vinculo entre alumno y"
                                                        + " apoderado"));
        if (link.isPrincipal()) {
            long otherLinks =
                    alumnoApoderadoRepository.findAllByAlumnoIdAndActivoTrue(alumnoId).stream()
                            .filter(existing -> !existing.getId().equals(link.getId()))
                            .count();
            if (otherLinks > 0) {
                throw new BusinessRuleException(
                        "APODERADO_PRINCIPAL_REQUERIDO",
                        "Designe otro apoderado principal antes de retirar el actual");
            }
        }
        link.setActivo(false);
        alumnoApoderadoRepository.save(link);
        auditoriaService.registrar(
                "DESVINCULAR_APODERADO",
                ModuloAuditoria.PERSONAS,
                "AlumnoApoderado",
                link.getId().toString(),
                ResultadoAuditoria.EXITOSO,
                null,
                "alumno="
                        + alumnoId
                        + ",apoderado="
                        + apoderadoId
                        + ",parentesco="
                        + link.getParentesco()
                        + ",principal="
                        + link.isPrincipal(),
                null);
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

    private void deactivateProfile(Persona persona, String role) {
        boolean tieneOtroPerfil =
                alumnoRepository.findByPersonaIdAndActivoTrue(persona.getId()).isPresent()
                        || docenteRepository
                                .findByPersonaIdAndActivoTrue(persona.getId())
                                .isPresent()
                        || apoderadoRepository
                                .findByPersonaIdAndActivoTrue(persona.getId())
                                .isPresent()
                        || administradorRepository
                                .findByPersonaIdAndActivoTrue(persona.getId())
                                .isPresent();
        if (!tieneOtroPerfil) {
            persona.setActivo(false);
            personaRepository.save(persona);
        }
        usuarioRepository.findAllByPersonaIdAndActivoTrue(persona.getId()).stream()
                .filter(usuario -> role.equals(usuario.getRol().getNombre()))
                .forEach(this::deactivateUser);
    }

    private void deactivateUser(AccesoUsuario usuario) {
        usuario.setActivo(false);
        usuario.setEstado(EstadoAcceso.DESACTIVADO);
        usuarioRepository.save(usuario);
        sesionRepository.revokeAllByUsuarioId(
                usuario.getId(), EstadoSesion.REVOCADA, Instant.now());
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
        List<PersonaDtos.ApoderadoVinculadoResponse> guardians =
                alumnoApoderadoRepository.findAllByAlumnoIdAndActivoTrue(alumno.getId()).stream()
                        .map(this::toGuardianLink)
                        .toList();
        return new PersonaDtos.AlumnoResponse(
                alumno.getId(),
                alumno.getCodigoAlumno(),
                toPersonaResponse(alumno.getPersona()),
                guardians,
                alumno.isActivo());
    }

    private PersonaDtos.DocenteResponse toDocenteResponse(Docente docente) {
        return new PersonaDtos.DocenteResponse(
                docente.getId(),
                docente.getCodigoDocente(),
                docente.getFechaIngreso(),
                docente.getEspecialidad(),
                toPersonaResponse(docente.getPersona()),
                docente.isActivo());
    }

    private PersonaDtos.ApoderadoResponse toApoderadoResponse(Apoderado apoderado) {
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
                toPersonaResponse(apoderado.getPersona()),
                students,
                apoderado.isActivo());
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
