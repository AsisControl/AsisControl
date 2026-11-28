package com.asiscontrol.dto.persona;

import com.asiscontrol.entity.enums.Especialidad;
import com.asiscontrol.entity.enums.Parentesco;
import com.asiscontrol.entity.enums.Sexo;
import com.asiscontrol.entity.enums.TipoDocumento;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.groups.ConvertGroup;
import jakarta.validation.groups.Default;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class PersonaDtos {

    private PersonaDtos() {}

    /** Validaciones específicas de la ficha secundaria; no cambian los otros perfiles. */
    public interface FichaAlumno {}

    @ValidAlumnoPersona(groups = FichaAlumno.class)
    @ValidDocumentoPersona
    public record PersonaRequest(
            @NotBlank(
                            message = "Los nombres son obligatorios",
                            groups = {Default.class, FichaAlumno.class})
                    @Size(max = 100)
                    @Size(
                            min = 2,
                            max = 100,
                            message = "Los nombres deben tener de 2 a 100 caracteres",
                            groups = FichaAlumno.class)
                    @Pattern(
                            regexp = "[\\p{L}\\p{M} '\u2019-]+",
                            message = "Usa solo letras, espacios, guion o apóstrofo",
                            groups = FichaAlumno.class)
                    String nombres,
            @NotBlank(
                            message = "El primer apellido es obligatorio",
                            groups = {Default.class, FichaAlumno.class})
                    @Size(max = 80)
                    @Size(
                            min = 2,
                            max = 80,
                            message = "El primer apellido debe tener de 2 a 80 caracteres",
                            groups = FichaAlumno.class)
                    @Pattern(
                            regexp = "[\\p{L}\\p{M} '\u2019-]+",
                            message = "Usa solo letras, espacios, guion o apóstrofo",
                            groups = FichaAlumno.class)
                    String apellidoPaterno,
            @NotBlank(message = "El apellido materno es obligatorio")
                    @Size(
                            max = 80,
                            message = "El segundo apellido admite hasta 80 caracteres",
                            groups = {Default.class, FichaAlumno.class})
                    @Pattern(
                            regexp = "[\\p{L}\\p{M} '\u2019-]*",
                            message = "Usa solo letras, espacios, guion o apóstrofo",
                            groups = FichaAlumno.class)
                    String apellidoMaterno,
            @NotNull(message = "El tipo de documento es obligatorio") TipoDocumento tipoDocumento,
            @NotBlank(message = "El numero de documento es obligatorio")
                    @Pattern(
                            regexp = "[A-Za-z0-9-]{6,20}",
                            message = "El documento no tiene un formato valido")
                    String numeroDocumento,
            Long nacionalidadId,
            @NotNull(message = "El sexo es obligatorio") Sexo sexo,
            @NotNull(
                            message = "La fecha de nacimiento es obligatoria",
                            groups = {Default.class, FichaAlumno.class})
                    @Past(message = "La fecha de nacimiento debe estar en el pasado")
                    LocalDate fechaNacimiento,
            @Pattern(
                            regexp = "[0-9+() -]{7,20}",
                            message = "El telefono no tiene un formato valido")
                    @Pattern(
                            regexp = "(?:|9[0-9]{8})",
                            message =
                                    "El teléfono debe tener exactamente 9 dígitos y empezar con 9",
                            groups = FichaAlumno.class)
                    String telefono,
            // Opcional: nadie está obligado a registrar su correo de contacto.
            @Email(
                            message = "Ingresa un correo válido, por ejemplo nombre@dominio.com",
                            groups = {Default.class, FichaAlumno.class})
                    @Size(
                            max = 150,
                            message = "El correo admite hasta 150 caracteres",
                            groups = {Default.class, FichaAlumno.class})
                    String correo,
            @Size(
                            max = 250,
                            message = "La dirección admite hasta 250 caracteres",
                            groups = {Default.class, FichaAlumno.class})
                    String direccion) {}

    public record PersonaResponse(
            Long id,
            String nombres,
            String apellidoPaterno,
            String apellidoMaterno,
            String nombreCompleto,
            TipoDocumento tipoDocumento,
            String numeroDocumento,
            Long nacionalidadId,
            String nacionalidad,
            Sexo sexo,
            LocalDate fechaNacimiento,
            String telefono,
            String correo,
            String direccion,
            boolean activo,
            Instant fechaCreacion,
            Instant fechaActualizacion) {}

    /**
     * Filtros opcionales de las fichas de estudiantes. El grado se toma de la matrícula activa y el
     * apoderado debe estar habilitado.
     */
    public record FiltroAlumnos(Boolean conMatricula, Integer grado, Boolean conApoderado) {
        public static final FiltroAlumnos NINGUNO = new FiltroAlumnos(null, null, null);
    }

    public record AlumnoCreateRequest(
            @NotNull(message = "Completa los datos de identidad")
                    @Valid
                    @ConvertGroup(from = Default.class, to = FichaAlumno.class)
                    PersonaRequest persona) {}

    public record AlumnoUpdateRequest(
            @NotNull(message = "Completa los datos de identidad")
                    @Valid
                    @ConvertGroup(from = Default.class, to = FichaAlumno.class)
                    PersonaRequest persona,
            @Size(max = 30, message = "El código institucional admite hasta 30 caracteres")
                    String codigoAlumno,
            @Size(max = 1000, message = "El motivo admite hasta 1000 caracteres") String motivo) {
        public AlumnoUpdateRequest(PersonaRequest persona, String codigoAlumno) {
            this(persona, codigoAlumno, null);
        }
    }

    public record AlumnoResponse(
            Long id,
            String codigoAlumno,
            PersonaResponse persona,
            List<ApoderadoVinculadoResponse> apoderados,
            boolean activo,
            CredencialAcceso credencialAcceso) {}

    /** El código del docente lo genera el sistema (DOC{AÑO}{####}). */
    public record DocenteCreateRequest(
            @NotNull @Valid PersonaRequest persona,
            @NotNull(message = "La fecha de ingreso es obligatoria") LocalDate fechaIngreso,
            @NotNull(message = "La especialidad es obligatoria") Especialidad especialidad) {}

    /** El código es permanente: si se envía, debe ser el registrado. */
    public record DocenteUpdateRequest(
            @NotNull @Valid PersonaRequest persona,
            @Size(max = 30) String codigoDocente,
            @NotNull(message = "La fecha de ingreso es obligatoria") LocalDate fechaIngreso,
            @NotNull(message = "La especialidad es obligatoria") Especialidad especialidad) {}

    public record DocenteResponse(
            Long id,
            String codigoDocente,
            LocalDate fechaIngreso,
            Especialidad especialidad,
            PersonaResponse persona,
            boolean activo,
            CredencialAcceso credencialAcceso) {}

    public record ApoderadoCreateRequest(@NotNull @Valid PersonaRequest persona) {}

    public record ApoderadoUpdateRequest(@NotNull @Valid PersonaRequest persona) {}

    public record ApoderadoResponse(
            Long id,
            String codigoApoderado,
            PersonaResponse persona,
            List<AlumnoVinculadoResponse> alumnos,
            boolean activo,
            CredencialAcceso credencialAcceso) {}

    public record VinculoApoderadoRequest(
            @NotNull(message = "El apoderado es obligatorio") Long apoderadoId,
            Parentesco parentesco,
            boolean principal) {}

    public record ApoderadoVinculadoResponse(
            Long id,
            Long apoderadoId,
            String nombreCompleto,
            Parentesco parentesco,
            boolean principal) {}

    public record EdicionVinculoApoderadoRequest(
            Parentesco parentesco,
            @NotNull Boolean principal,
            @NotBlank @Size(max = 500) String motivo) {}

    public record EstadoVinculoApoderadoRequest(
            @NotNull Boolean activo,
            @NotBlank @Size(max = 500) String motivo) {}

    public record VinculoApoderadoResponse(
            Long id,
            Long apoderadoId,
            String nombreCompleto,
            Parentesco parentesco,
            boolean principal,
            boolean activo,
            boolean apoderadoActivo) {}

    public record AlumnoVinculadoResponse(
            Long id,
            Long alumnoId,
            String codigoAlumno,
            String nombreCompleto,
            Parentesco parentesco,
            boolean principal) {}

    public record ImportIssue(int row, String field, String message) {}

    /**
     * Resultado de una importación. {@code credentials}: accesos de las filas registradas; la
     * contraseña temporal solo viaja cuando la carga quedó guardada y no se vuelve a mostrar.
     * {@code warnings}: observaciones de filas registradas que conviene revisar.
     */
    public record ImportResult(
            int totalRows,
            int importedRows,
            int rejectedRows,
            List<ImportIssue> issues,
            List<CredencialAcceso> credentials,
            List<ImportIssue> warnings) {
        public ImportResult(
                int totalRows, int importedRows, int rejectedRows, List<ImportIssue> issues) {
            this(totalRows, importedRows, rejectedRows, issues, List.of(), List.of());
        }

        /** El mismo resultado sin contraseñas: para validaciones y cargas que no se guardaron. */
        public ImportResult sinContrasenas() {
            return new ImportResult(
                    totalRows, importedRows, rejectedRows, issues,
                    credentials.stream().map(CredencialAcceso::sinContrasena).toList(),
                    warnings);
        }
    }

    /**
     * Acceso de una persona registrada. {@code existing}: ya tenía acceso y no se creó
     * otro; entonces no hay contraseña temporal. La contraseña nunca se escribe en logs ni en la
     * auditoría: {@link #toString()} la oculta.
     */
    public record CredencialAcceso(
            int row,
            String fullName,
            String role,
            String code,
            String username,
            String email,
            String temporaryPassword,
            Instant expiresAt,
            boolean existing,
            String accessCode) {
        /** Sin código de acceso: lo usan los datos de prueba y las respuestas que no lo informan. */
        public CredencialAcceso(
                int row, String fullName, String role, String code, String username, String email,
                String temporaryPassword, Instant expiresAt, boolean existing) {
            this(row, fullName, role, code, username, email, temporaryPassword, expiresAt, existing, null);
        }

        public CredencialAcceso enFila(int fila) {
            return new CredencialAcceso(fila, fullName, role, code, username, email,
                    temporaryPassword, expiresAt, existing, accessCode);
        }

        public CredencialAcceso sinContrasena() {
            return new CredencialAcceso(
                    row, fullName, role, code, username, email, null, expiresAt, existing, accessCode);
        }

        @Override
        public String toString() {
            return "CredencialAcceso[row=" + row + ", role=" + role + ", accessCode=" + accessCode
                    + ", username=" + username
                    + ", existing=" + existing + ", temporaryPassword="
                    + (temporaryPassword == null ? "null" : "***") + "]";
        }
    }

    /**
     * Un archivo de la carga masiva de familias: {@code error} si no se pudo leer el archivo
     * (formato, cabeceras); si no, {@code result} con sus filas validas y con error.
     */
    public record FamilyImportFile(String error, ImportResult result) {}

    /**
     * Carga masiva de familias. {@code valid}: ningun archivo tiene errores. {@code saved}: se
     * guardo todo en una sola transaccion; si no, no se guardo ninguna fila.
     */
    public record FamilyImportResult(
            boolean valid, boolean saved, java.util.Map<String, FamilyImportFile> files) {}

    public record NacionalidadRequest(
            @NotBlank(message = "El nombre es obligatorio") @Size(max = 80) String nombre) {}

    public record NacionalidadResponse(Long id, String nombre) {}
}
