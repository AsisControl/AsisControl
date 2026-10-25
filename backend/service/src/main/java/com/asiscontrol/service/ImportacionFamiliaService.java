package com.asiscontrol.service;

import com.asiscontrol.dto.persona.PersonaDtos;
import com.asiscontrol.entity.enums.Parentesco;
import com.asiscontrol.entity.enums.ResultadoAuditoria;
import com.asiscontrol.entity.enums.Sexo;
import com.asiscontrol.entity.enums.TipoDocumento;
import com.asiscontrol.exception.BusinessRuleException;
import com.asiscontrol.exception.ResourceNotFoundException;
import com.asiscontrol.repository.AlumnoRepository;
import com.asiscontrol.repository.ApoderadoRepository;
import com.asiscontrol.repository.PersonaRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Importacion de apoderados y de sus parentescos con los alumnos, con las plantillas de
 * {@link PlantillaFamiliaService}. Se importan por separado y en ese orden: los parentescos
 * buscan al alumno por su codigo y al apoderado por su documento.
 */
@Service
public class ImportacionFamiliaService {

    /**
     * Desde V5 la base admite personas sin segundo apellido, sexo ni correo (fichas de alumnos),
     * pero un apoderado necesita esos datos para identificarlo y contactarlo.
     */
    private static final List<String> GUARDIAN_REQUIRED = List.of(
            "tipoDocumento", "numeroDocumento", "nombres", "apellidoPaterno",
            "apellidoMaterno", "sexo", "fechaNacimiento", "correo");
    private static final List<String> RELATIONSHIP_REQUIRED =
            PlantillaFamiliaService.RELATIONSHIP_HEADERS;
    /** El ENUM de AlumnoApoderado.parentesco en MySQL (V1) no incluye NO_DECLARADO. */
    static final List<Parentesco> PARENTESCOS_IMPORTABLES = List.of(
            Parentesco.PADRE, Parentesco.MADRE, Parentesco.TUTOR_LEGAL, Parentesco.OTRO_FAMILIAR);

    private final PersonaService personaService;
    private final PersonaRepository personaRepository;
    private final AlumnoRepository alumnoRepository;
    private final ApoderadoRepository apoderadoRepository;
    private final Validator validator;
    private final AuditoriaService auditoria;

    public ImportacionFamiliaService(
            PersonaService personaService,
            PersonaRepository personaRepository,
            AlumnoRepository alumnoRepository,
            ApoderadoRepository apoderadoRepository,
            Validator validator,
            AuditoriaService auditoria) {
        this.personaService = personaService;
        this.personaRepository = personaRepository;
        this.alumnoRepository = alumnoRepository;
        this.apoderadoRepository = apoderadoRepository;
        this.validator = validator;
        this.auditoria = auditoria;
    }

    /**
     * Una fila por apoderado. Si su documento ya esta registrado con la misma identidad (por
     * ejemplo, como docente), se reutiliza a esa persona: ver PersonaService#createApoderado.
     */
    public PersonaDtos.ImportResult importGuardians(MultipartFile file) {
        var resumen = LectorExcel.recorrer(file, GUARDIAN_REQUIRED, fila -> {
            var persona = new PersonaDtos.PersonaRequest(
                    fila.obligatorio("nombres"),
                    fila.obligatorio("apellidoPaterno"),
                    fila.obligatorio("apellidoMaterno"),
                    fila.enumerado(TipoDocumento.class, "tipoDocumento",
                            List.of(TipoDocumento.values())),
                    fila.obligatorio("numeroDocumento"),
                    null,
                    fila.enumerado(Sexo.class, "sexo", List.of(Sexo.values())),
                    fila.fecha("fechaNacimiento"),
                    fila.opcional("telefono"),
                    fila.obligatorio("correo"),
                    fila.opcional("direccion"));
            var request = new PersonaDtos.ApoderadoCreateRequest(persona);
            validar(request);
            personaService.createApoderado(request);
        });
        auditar("IMPORTAR_APODERADOS", "Apoderado", file, resumen);
        return resumen.resultado();
    }

    /**
     * Una fila por vinculo alumno-apoderado. En un mismo archivo solo se acepta un apoderado
     * principal por alumno; al vincular uno principal, los demas vinculos dejan de serlo.
     */
    public PersonaDtos.ImportResult importRelationships(MultipartFile file) {
        Map<String, Integer> principalPorAlumno = new HashMap<>();
        var resumen = LectorExcel.recorrer(file, RELATIONSHIP_REQUIRED, fila -> {
            String codigo = fila.obligatorio("codigoAlumno").toUpperCase(Locale.ROOT);
            TipoDocumento tipo = fila.enumerado(TipoDocumento.class, "tipoDocumentoApoderado",
                    List.of(TipoDocumento.values()));
            String numero = fila.obligatorio("numeroDocumentoApoderado").toUpperCase(Locale.ROOT);
            Parentesco parentesco =
                    fila.enumerado(Parentesco.class, "parentesco", PARENTESCOS_IMPORTABLES);
            boolean principal = fila.siNo("principal");

            var alumno = alumnoRepository.findByCodigoAlumnoIgnoreCase(codigo)
                    .filter(found -> found.isActivo())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "ALUMNO_NO_ENCONTRADO",
                            "No hay un estudiante habilitado con el codigo " + codigo));
            var apoderado = personaRepository.findByTipoDocumentoAndNumeroDocumento(tipo, numero)
                    .flatMap(persona -> apoderadoRepository.findByPersonaIdAndActivoTrue(persona.getId()))
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "APODERADO_NO_ENCONTRADO",
                            "No hay un apoderado habilitado con el documento " + tipo + " " + numero
                                    + ". Importalo primero en la plantilla de apoderados"));
            if (principal) {
                Integer previa = principalPorAlumno.get(codigo);
                if (previa != null) {
                    throw new BusinessRuleException(
                            "PRINCIPAL_DUPLICADO",
                            "El estudiante " + codigo + " ya tiene un apoderado principal en la fila "
                                    + previa);
                }
            }
            personaService.linkGuardian(
                    alumno.getId(),
                    new PersonaDtos.VinculoApoderadoRequest(apoderado.getId(), parentesco, principal));
            if (principal) {
                principalPorAlumno.put(codigo, fila.numero());
            }
        });
        auditar("IMPORTAR_PARENTESCOS", "AlumnoApoderado", file, resumen);
        return resumen.resultado();
    }

    private <T> void validar(T request) {
        var errors = validator.validate(request);
        if (!errors.isEmpty()) {
            throw new BusinessRuleException("FILA_INVALIDA", errors.stream()
                    .map(ConstraintViolation::getMessage)
                    .sorted()
                    .collect(Collectors.joining("; ")));
        }
    }

    /** Un registro por archivo; cada alta o vinculo ya deja su propio registro de auditoria. */
    private void auditar(
            String accion, String entidad, MultipartFile file, LectorExcel.Resumen resumen) {
        String filasConError = resumen.issues().stream()
                .map(issue -> String.valueOf(issue.row()))
                .collect(Collectors.joining(","));
        auditoria.registrar(
                accion,
                ModuloAuditoria.PERSONAS,
                entidad,
                null,
                resumen.importadas() > 0 || resumen.total() == 0
                        ? ResultadoAuditoria.EXITOSO
                        : ResultadoAuditoria.RECHAZADO,
                resumen.issues().isEmpty() ? null : "Filas con errores: " + filasConError,
                null,
                "archivo=" + file.getOriginalFilename()
                        + ",filas=" + resumen.total()
                        + ",importados=" + resumen.importadas()
                        + ",fallidos=" + (resumen.total() - resumen.importadas()));
    }
}
