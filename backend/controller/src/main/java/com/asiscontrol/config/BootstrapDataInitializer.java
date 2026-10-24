package com.asiscontrol.config;

import com.asiscontrol.entity.AccesoUsuario;
import com.asiscontrol.entity.Administrador;
import com.asiscontrol.entity.CategoriaPermiso;
import com.asiscontrol.entity.Nacionalidad;
import com.asiscontrol.entity.Permiso;
import com.asiscontrol.entity.Persona;
import com.asiscontrol.entity.Rol;
import com.asiscontrol.entity.enums.EstadoAcceso;
import com.asiscontrol.entity.enums.Sexo;
import com.asiscontrol.entity.enums.TipoDocumento;
import com.asiscontrol.repository.AccesoUsuarioRepository;
import com.asiscontrol.repository.AdministradorRepository;
import com.asiscontrol.repository.CategoriaPermisoRepository;
import com.asiscontrol.repository.NacionalidadRepository;
import com.asiscontrol.repository.PermisoRepository;
import com.asiscontrol.repository.PersonaRepository;
import com.asiscontrol.repository.RolRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@Component
@ConditionalOnProperty(name = "app.bootstrap.enabled", havingValue = "true", matchIfMissing = true)
public class BootstrapDataInitializer implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(BootstrapDataInitializer.class);

    private final CategoriaPermisoRepository categoriaRepository;
    private final PermisoRepository permisoRepository;
    private final RolRepository rolRepository;
    private final NacionalidadRepository nacionalidadRepository;
    private final PersonaRepository personaRepository;
    private final AdministradorRepository administradorRepository;
    private final AccesoUsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminDocument;
    private final String adminEmail;
    private final String adminPassword;
    private final String adminUsername;

    public BootstrapDataInitializer(
            CategoriaPermisoRepository categoriaRepository,
            PermisoRepository permisoRepository,
            RolRepository rolRepository,
            NacionalidadRepository nacionalidadRepository,
            PersonaRepository personaRepository,
            AdministradorRepository administradorRepository,
            AccesoUsuarioRepository usuarioRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.bootstrap.admin-document:}") String adminDocument,
            @Value("${app.bootstrap.admin-email:}") String adminEmail,
            @Value("${app.bootstrap.admin-password:}") String adminPassword,
            @Value("${app.bootstrap.admin-username:admin}") String adminUsername
    ) {
        this.categoriaRepository = categoriaRepository;
        this.permisoRepository = permisoRepository;
        this.rolRepository = rolRepository;
        this.nacionalidadRepository = nacionalidadRepository;
        this.personaRepository = personaRepository;
        this.administradorRepository = administradorRepository;
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminDocument = adminDocument;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
        this.adminUsername = adminUsername;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        Map<String, Permiso> permissions = initializePermissions();
        initializeRoles(permissions);
        initializeNationality();
        initializeOptionalAdministrator();
    }

    private Map<String, Permiso> initializePermissions() {
        Map<String, String[]> definitions = new LinkedHashMap<>();
        definitions.put("USUARIOS_LEER", new String[]{"Seguridad", "Consultar usuarios y roles"});
        definitions.put("USUARIOS_ESCRIBIR", new String[]{"Seguridad", "Gestionar usuarios y roles"});
        definitions.put("ACADEMICO_LEER", new String[]{"Académico", "Consultar estructura académica"});
        definitions.put("ACADEMICO_ESCRIBIR", new String[]{"Académico", "Gestionar estructura académica"});
        definitions.put("SOLICITUDES_ESCRIBIR", new String[]{"Matrícula", "Crear y enviar solicitudes de estudiantes asociados"});
        definitions.put("MATRICULA_LEER", new String[]{"Matrícula", "Consultar solicitudes y matrículas"});
        definitions.put("MATRICULA_ESCRIBIR", new String[]{"Matrícula", "Gestionar solicitudes y matrículas"});
        definitions.put("ASISTENCIA_LEER", new String[]{"Asistencia", "Consultar asistencias"});
        definitions.put("ASISTENCIA_ESCRIBIR", new String[]{"Asistencia", "Registrar y corregir asistencias"});
        definitions.put("TAREAS_LEER", new String[]{"Tareas", "Consultar tareas y entregas"});
        definitions.put("TAREAS_ESCRIBIR", new String[]{"Tareas", "Gestionar tareas y calificar entregas"});
        definitions.put("TAREAS_ENTREGAR", new String[]{"Tareas", "Entregar tareas"});
        definitions.put("CALIFICACIONES_LEER", new String[]{"Evaluación", "Consultar calificaciones"});
        definitions.put("CALIFICACIONES_ESCRIBIR", new String[]{"Evaluación", "Gestionar evaluaciones y notas"});
        definitions.put("PAGOS_LEER", new String[]{"Pagos", "Consultar obligaciones y pagos"});
        definitions.put("PAGOS_ESCRIBIR", new String[]{"Pagos", "Registrar y anular pagos"});
        definitions.put("RIESGOS_LEER", new String[]{"Riesgo", "Consultar alertas de riesgo"});
        definitions.put("RIESGOS_ESCRIBIR", new String[]{"Riesgo", "Configurar y gestionar alertas"});
        definitions.put("AUDITORIA_LEER", new String[]{"Auditoría", "Consultar auditoría y errores"});
        definitions.put("ARCHIVOS_ESCRIBIR", new String[]{"Archivos", "Cargar y vincular archivos"});

        Map<String, CategoriaPermiso> categories = new LinkedHashMap<>();
        Map<String, Permiso> result = new LinkedHashMap<>();
        definitions.forEach((code, data) -> {
            CategoriaPermiso category = categories.computeIfAbsent(data[0], this::categoriaPermiso);
            Permiso permission = permisoRepository.findByCodigoIgnoreCase(code).orElseGet(() -> {
                Permiso created = new Permiso();
                created.setCodigo(code);
                created.setNombre(code.replace('_', ' '));
                return created;
            });
            // Se actualizan tambien los permisos existentes para corregir textos en bases ya creadas.
            permission.setDescripcion(data[1]);
            permission.setCategoria(category);
            result.put(code, permisoRepository.save(permission));
        });
        return result;
    }

    /**
     * Busca la categoria sin distinguir tildes ni mayusculas y la renombra si hace falta: asi una
     * base creada con "Academico" pasa a "Académico" sin duplicar la categoria.
     */
    private CategoriaPermiso categoriaPermiso(String nombre) {
        String clave = sinTildes(nombre);
        CategoriaPermiso categoria = categoriaRepository.findAll().stream()
                .filter(existente -> sinTildes(existente.getNombre()).equalsIgnoreCase(clave))
                .findFirst()
                .orElseGet(CategoriaPermiso::new);
        categoria.setNombre(nombre);
        categoria.setDescripcion("Permisos del módulo " + nombre);
        return categoriaRepository.save(categoria);
    }

    private static String sinTildes(String texto) {
        return java.text.Normalizer.normalize(texto, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
    }

    private void initializeRoles(Map<String, Permiso> permissions) {
        createOrUpdateRole("ADMINISTRADOR", "Acceso total al sistema", permissions.keySet(), permissions);
        createOrUpdateRole("DIRECTOR", "Supervision academica y administrativa", Set.of(
                "ACADEMICO_LEER", "MATRICULA_LEER", "ASISTENCIA_LEER", "TAREAS_LEER",
                "CALIFICACIONES_LEER", "PAGOS_LEER", "RIESGOS_LEER", "AUDITORIA_LEER"
        ), permissions);
        createOrUpdateRole("SECRETARIA", "Gestion de personas y matriculas", Set.of(
                "USUARIOS_LEER", "ACADEMICO_LEER", "MATRICULA_LEER", "MATRICULA_ESCRIBIR",
                "ASISTENCIA_LEER", "PAGOS_LEER", "ARCHIVOS_ESCRIBIR", "SOLICITUDES_ESCRIBIR"
        ), permissions);
        createOrUpdateRole("DOCENTE", "Gestion pedagogica y asistencia", Set.of(
                "ACADEMICO_LEER", "ASISTENCIA_LEER", "ASISTENCIA_ESCRIBIR", "TAREAS_LEER",
                "TAREAS_ESCRIBIR", "CALIFICACIONES_LEER", "CALIFICACIONES_ESCRIBIR",
                "RIESGOS_LEER", "ARCHIVOS_ESCRIBIR"
        ), permissions);
        createOrUpdateRole("ALUMNO", "Acceso del estudiante", Set.of(
                "ACADEMICO_LEER", "MATRICULA_LEER", "ASISTENCIA_LEER", "TAREAS_LEER", "TAREAS_ENTREGAR",
                "CALIFICACIONES_LEER", "ARCHIVOS_ESCRIBIR"
        ), permissions);
        createOrUpdateRole("APODERADO", "Acceso del apoderado", Set.of(
                "MATRICULA_LEER", "ASISTENCIA_LEER", "TAREAS_LEER", "CALIFICACIONES_LEER",
                "PAGOS_LEER", "ARCHIVOS_ESCRIBIR", "SOLICITUDES_ESCRIBIR"
        ), permissions);
        createOrUpdateRole("TESORERIA", "Gestion de compromisos y pagos", Set.of(
                "PAGOS_LEER", "PAGOS_ESCRIBIR", "MATRICULA_LEER", "AUDITORIA_LEER"
        ), permissions);
    }

    private void createOrUpdateRole(
            String name,
            String description,
            Set<String> permissionCodes,
            Map<String, Permiso> permissions
    ) {
        Rol role = rolRepository.findByNombreIgnoreCase(name).orElseGet(Rol::new);
        role.setNombre(name);
        role.setDescripcion(description);
        role.setActivo(true);
        LinkedHashSet<Permiso> assigned = new LinkedHashSet<>();
        permissionCodes.stream().map(permissions::get).forEach(assigned::add);
        role.setPermisos(assigned);
        rolRepository.save(role);
    }

    private void initializeNationality() {
        nacionalidadRepository.findByNombreIgnoreCase("Peruana").orElseGet(() -> {
            Nacionalidad nationality = new Nacionalidad();
            nationality.setNombre("Peruana");
            return nacionalidadRepository.save(nationality);
        });
    }

    private void initializeOptionalAdministrator() {
        if (isBlank(adminDocument) || isBlank(adminEmail) || isBlank(adminPassword)) {
            LOGGER.info("No se creo administrador inicial: configure BOOTSTRAP_ADMIN_DOCUMENT, "
                    + "BOOTSTRAP_ADMIN_EMAIL y BOOTSTRAP_ADMIN_PASSWORD si lo necesita");
            return;
        }
        if (usuarioRepository.findActiveByIdentifier(adminUsername).isPresent()
                || usuarioRepository.findActiveByIdentifier(adminEmail).isPresent()) {
            return;
        }
        Rol administratorRole = rolRepository.findByNombreIgnoreCase("ADMINISTRADOR")
                .orElseThrow();
        Nacionalidad nationality = nacionalidadRepository.findByNombreIgnoreCase("Peruana")
                .orElseThrow();
        Persona person = personaRepository.findByTipoDocumentoAndNumeroDocumento(
                TipoDocumento.DNI,
                adminDocument.trim()
        ).orElseGet(() -> {
            Persona created = new Persona();
            created.setNombres("Administrador");
            created.setApellidoPaterno("General");
            created.setApellidoMaterno("AsisControl");
            created.setTipoDocumento(TipoDocumento.DNI);
            created.setNumeroDocumento(adminDocument.trim());
            created.setNacionalidad(nationality);
            created.setSexo(Sexo.PREFIERE_NO_INDICAR);
            created.setFechaNacimiento(LocalDate.of(1990, 1, 1));
            created.setCorreo(adminEmail.trim().toLowerCase());
            return personaRepository.save(created);
        });
        Administrador administrator = administradorRepository.findByPersonaIdAndActivoTrue(person.getId())
                .orElseGet(() -> {
                    Administrador created = new Administrador();
                    created.setPersona(person);
                    return administradorRepository.save(created);
                });
        AccesoUsuario user = new AccesoUsuario();
        user.setPersona(administrator.getPersona());
        user.setRol(administratorRole);
        user.setUsername(adminUsername.trim());
        user.setCorreo(adminEmail.trim().toLowerCase());
        user.setContrasenaHash(passwordEncoder.encode(adminPassword));
        user.setEstado(EstadoAcceso.ACTIVO);
        com.asiscontrol.security.PasswordPolicy.validate(adminPassword.trim(), adminUsername.trim());
        user.setRequiereCambioContrasena(true);
        user.setContrasenaTemporalExpira(java.time.Instant.now().plus(java.time.Duration.ofHours(24)));
        usuarioRepository.save(user);
        LOGGER.info("Administrador inicial creado; debe cambiar su contrasena en el primer acceso");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
