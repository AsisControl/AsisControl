package com.asiscontrol.service;

import com.asiscontrol.dto.persona.PersonaDtos;
import com.asiscontrol.entity.enums.Parentesco;
import com.asiscontrol.entity.enums.Sexo;
import com.asiscontrol.entity.enums.TipoDocumento;
import com.asiscontrol.exception.BusinessRuleException;
import com.asiscontrol.exception.ResourceNotFoundException;
import com.asiscontrol.repository.AlumnoRepository;
import com.asiscontrol.repository.ApoderadoRepository;
import com.asiscontrol.repository.PersonaRepository;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Importacion de apoderados y de sus parentescos con los alumnos, con las plantillas de
 * {@link PlantillaFamiliaService}. Se importan por separado y en ese orden: los parentescos
 * buscan al alumno por su codigo y al apoderado por su documento.
 */
@Service
public class ImportacionFamiliaService {

    /**
     * Un apoderado necesita documento, nombres completos, sexo y fecha de nacimiento para
     * identificarlo. El teléfono y el correo son opcionales: si faltan los dos, la fila se registra
     * con una observación para que la institución consiga un contacto.
     */
    private static final List<String> GUARDIAN_REQUIRED = List.of(
            "tipoDocumento", "numeroDocumento", "nombres", "apellidoPaterno",
            "apellidoMaterno", "sexo", "fechaNacimiento");
    private static final List<String> RELATIONSHIP_REQUIRED =
            PlantillaFamiliaService.RELATIONSHIP_HEADERS;
    /** La plantilla de parentescos requiere uno de los tipos de relación declarados. */
    static final List<Parentesco> PARENTESCOS_IMPORTABLES = List.of(
            Parentesco.PADRE, Parentesco.MADRE, Parentesco.TUTOR_LEGAL, Parentesco.OTRO_FAMILIAR);

    private final PersonaService personaService;
    private final PersonaRepository personaRepository;
    private final AlumnoRepository alumnoRepository;
    private final ApoderadoRepository apoderadoRepository;
    private final Validator validator;
    private final AuditoriaService auditoria;
    private final AccesoImportacionService accesos;

    public ImportacionFamiliaService(
            PersonaService personaService,
            PersonaRepository personaRepository,
            AlumnoRepository alumnoRepository,
            ApoderadoRepository apoderadoRepository,
            Validator validator,
            AuditoriaService auditoria,
            AccesoImportacionService accesos) {
        this.personaService = personaService;
        this.personaRepository = personaRepository;
        this.alumnoRepository = alumnoRepository;
        this.apoderadoRepository = apoderadoRepository;
        this.validator = validator;
        this.auditoria = auditoria;
        this.accesos = accesos;
    }

    /**
     * Una fila por apoderado, con su acceso al sistema. Si su documento ya esta registrado con la
     * misma identidad (por ejemplo, como docente, o como apoderado de otro hijo), se reutiliza a
     * esa persona y su cuenta: ver PersonaService#registrarOReutilizarApoderado.
     */
    public PersonaDtos.ImportResult importGuardians(MultipartFile file) {
        List<PersonaDtos.CredencialAcceso> credenciales = new java.util.ArrayList<>();
        List<PersonaDtos.ImportIssue> observaciones = new java.util.ArrayList<>();
        var resumen = LectorExcel.recorrer(file, GUARDIAN_REQUIRED, fila -> {
            var persona = new PersonaDtos.PersonaRequest(
                    fila.obligatorio("nombres"),
                    fila.obligatorio("apellidoPaterno"),
                    fila.obligatorio("apellidoMaterno"),
                    fila.enumerado(TipoDocumento.class, "tipoDocumento",
                            List.of(TipoDocumento.values())),
                    fila.obligatorio("numeroDocumento"),
                    nacionalidad(fila),
                    fila.enumerado(Sexo.class, "sexo", List.of(Sexo.values())),
                    fila.fecha("fechaNacimiento"),
                    fila.opcional("telefono"),
                    fila.opcional("correo"),
                    fila.opcional("direccion"));
            var request = new PersonaDtos.ApoderadoCreateRequest(persona);
            LectorExcel.validar(validator, request);
            credenciales.add(accesos.apoderado(fila.numero(), request));
            if (persona.telefono() == null && persona.correo() == null) {
                observaciones.add(new PersonaDtos.ImportIssue(fila.numero(), "contacto",
                        "El apoderado no tiene teléfono ni correo de contacto"));
            }
        });
        resumen.auditar(auditoria, "IMPORTAR_APODERADOS", "Apoderado", file, credenciales);
        return resumen.resultado(credenciales, observaciones);
    }

    private Long nacionalidad(LectorExcel.Fila fila) {
        String nombre = fila.opcional("nacionalidad");
        return nombre == null ? null : personaService.nacionalidadPorNombre(nombre);
    }

    /**
     * Una fila por vinculo alumno-apoderado, ambos identificados por su documento. En un mismo
     * archivo solo se acepta un apoderado principal por alumno; al vincular uno principal, los
     * demas vinculos dejan de serlo.
     */
    public PersonaDtos.ImportResult importRelationships(MultipartFile file) {
        Map<String, Integer> principalPorAlumno = new HashMap<>();
        var resumen = LectorExcel.recorrer(file, RELATIONSHIP_REQUIRED, fila -> {
            TipoDocumento tipoAlumno = fila.enumerado(TipoDocumento.class, "tipoDocumentoAlumno",
                    List.of(TipoDocumento.values()));
            String numeroAlumno = fila.obligatorio("numeroDocumentoAlumno").toUpperCase(Locale.ROOT);
            String codigo = tipoAlumno + " " + numeroAlumno;
            TipoDocumento tipo = fila.enumerado(TipoDocumento.class, "tipoDocumentoApoderado",
                    List.of(TipoDocumento.values()));
            String numero = fila.obligatorio("numeroDocumentoApoderado").toUpperCase(Locale.ROOT);
            Parentesco parentesco =
                    fila.enumerado(Parentesco.class, "parentesco", PARENTESCOS_IMPORTABLES);
            boolean principal = fila.siNo("principal");

            var alumno = personaRepository.findByTipoDocumentoAndNumeroDocumento(tipoAlumno, numeroAlumno)
                    .flatMap(persona -> alumnoRepository.findByPersonaIdAndActivoTrue(persona.getId()))
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "ALUMNO_NO_ENCONTRADO",
                            "No hay un estudiante habilitado con el documento " + codigo));
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
        resumen.auditar(auditoria, "IMPORTAR_PARENTESCOS", "AlumnoApoderado", file);
        return resumen.resultado();
    }
}
