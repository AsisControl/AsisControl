package com.asiscontrol.config;

import com.asiscontrol.entity.AccesoUsuario;
import com.asiscontrol.entity.Alumno;
import com.asiscontrol.entity.AlumnoApoderado;
import com.asiscontrol.entity.Apoderado;
import com.asiscontrol.entity.AsignacionCurso;
import com.asiscontrol.entity.Aula;
import com.asiscontrol.entity.Curso;
import com.asiscontrol.entity.Docente;
import com.asiscontrol.entity.GradoAcademico;
import com.asiscontrol.entity.Horario;
import com.asiscontrol.entity.Matricula;
import com.asiscontrol.entity.Nacionalidad;
import com.asiscontrol.entity.Persona;
import com.asiscontrol.entity.Rol;
import com.asiscontrol.entity.Seccion;
import com.asiscontrol.entity.SolicitudMatricula;
import com.asiscontrol.entity.enums.DiaSemana;
import com.asiscontrol.entity.enums.Especialidad;
import com.asiscontrol.entity.enums.EstadoAcceso;
import com.asiscontrol.entity.enums.EstadoAnioAcademico;
import com.asiscontrol.entity.enums.EstadoMatricula;
import com.asiscontrol.entity.enums.EstadoPeriodoAcademico;
import com.asiscontrol.entity.enums.EstadoSolicitudMatricula;
import com.asiscontrol.entity.enums.Parentesco;
import com.asiscontrol.entity.enums.Sexo;
import com.asiscontrol.entity.enums.TipoAula;
import com.asiscontrol.entity.enums.TipoDocumento;
import com.asiscontrol.entity.planificacion.AnioAcademico;
import com.asiscontrol.entity.planificacion.PeriodoAcademico;
import com.asiscontrol.repository.AccesoUsuarioRepository;
import com.asiscontrol.repository.AlumnoApoderadoRepository;
import com.asiscontrol.repository.AlumnoRepository;
import com.asiscontrol.repository.ApoderadoRepository;
import com.asiscontrol.repository.AsignacionCursoRepository;
import com.asiscontrol.repository.AulaRepository;
import com.asiscontrol.repository.CursoRepository;
import com.asiscontrol.repository.DocenteRepository;
import com.asiscontrol.repository.GradoAcademicoRepository;
import com.asiscontrol.repository.HorarioRepository;
import com.asiscontrol.repository.MatriculaRepository;
import com.asiscontrol.repository.NacionalidadRepository;
import com.asiscontrol.repository.PersonaRepository;
import com.asiscontrol.repository.RolRepository;
import com.asiscontrol.repository.SeccionRepository;
import com.asiscontrol.repository.SolicitudMatriculaRepository;
import com.asiscontrol.repository.planificacion.AnioAcademicoRepository;
import com.asiscontrol.repository.planificacion.PeriodoAcademicoRepository;
import com.asiscontrol.security.PasswordPolicy;
import com.asiscontrol.service.asistencia.CalendarioSesionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Datos de demostracion para horarios y asistencia: un año activo con dos secciones, dos cursos,
 * dos docentes con horario de lunes a viernes y estudiantes matriculados. Solo existe con el
 * perfil {@code demo} y nunca es una migracion, para que no llegue a una base real por accidente.
 *
 * <p>Corre al quedar lista la aplicacion, despues de {@link BootstrapDataInitializer}, que crea
 * los roles y la nacionalidad. Si ya hay un año activo o el año actual existe, no escribe nada:
 * asi no se mezcla con datos reales y reiniciar con una base persistente no duplica la demo.
 */
@Component
@Profile("demo")
public class DemoDataInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger(DemoDataInitializer.class);
    private static final int GRADO = 1;
    private static final int CAPACIDAD_SECCION = 30;
    private static final int HORAS_SEMANALES = 8;
    private static final LocalTime PRIMER_BLOQUE = LocalTime.of(8, 0);
    private static final LocalTime SEGUNDO_BLOQUE = LocalTime.of(10, 0);
    private static final long DURACION_BLOQUE_MINUTOS = 90;
    private static final String DOMINIO_CORREO = "@demo.asiscontrol.test";
    private static final List<DiaSemana> DIAS_DE_CLASE = List.of(
            DiaSemana.LUNES, DiaSemana.MARTES, DiaSemana.MIERCOLES, DiaSemana.JUEVES, DiaSemana.VIERNES);
    private static final List<String[]> ALUMNOS_SECCION_A = List.of(
            new String[]{"Lucía", "Paredes", "Rojas"},
            new String[]{"Mateo", "Quispe", "Vargas"},
            new String[]{"Valeria", "Salazar", "Torres"},
            new String[]{"Diego", "Huamán", "Flores"},
            new String[]{"Camila", "Mendoza", "Ríos"},
            new String[]{"Sebastián", "Castro", "Luna"});
    private static final List<String[]> ALUMNOS_SECCION_B = List.of(
            new String[]{"Daniela", "Ramos", "Cárdenas"},
            new String[]{"Thiago", "Chávez", "Medina"},
            new String[]{"Fernanda", "Gutiérrez", "Soto"},
            new String[]{"Adrián", "Vega", "Espinoza"},
            new String[]{"Renata", "Campos", "Aguilar"},
            new String[]{"Joaquín", "Navarro", "Paz"});

    private final AnioAcademicoRepository anios;
    private final PeriodoAcademicoRepository periodos;
    private final GradoAcademicoRepository grados;
    private final SeccionRepository secciones;
    private final CursoRepository cursos;
    private final AulaRepository aulas;
    private final PersonaRepository personas;
    private final DocenteRepository docentes;
    private final AlumnoRepository alumnos;
    private final ApoderadoRepository apoderados;
    private final AlumnoApoderadoRepository vinculos;
    private final AsignacionCursoRepository asignaciones;
    private final HorarioRepository horarios;
    private final SolicitudMatriculaRepository solicitudes;
    private final MatriculaRepository matriculas;
    private final AccesoUsuarioRepository usuarios;
    private final RolRepository roles;
    private final NacionalidadRepository nacionalidades;
    private final CalendarioSesionService calendario;
    private final PasswordEncoder passwordEncoder;
    private final Clock reloj;
    private final String contrasena;

    private int siguienteDocumento = 90000001;

    public DemoDataInitializer(
            AnioAcademicoRepository anios,
            PeriodoAcademicoRepository periodos,
            GradoAcademicoRepository grados,
            SeccionRepository secciones,
            CursoRepository cursos,
            AulaRepository aulas,
            PersonaRepository personas,
            DocenteRepository docentes,
            AlumnoRepository alumnos,
            ApoderadoRepository apoderados,
            AlumnoApoderadoRepository vinculos,
            AsignacionCursoRepository asignaciones,
            HorarioRepository horarios,
            SolicitudMatriculaRepository solicitudes,
            MatriculaRepository matriculas,
            AccesoUsuarioRepository usuarios,
            RolRepository roles,
            NacionalidadRepository nacionalidades,
            CalendarioSesionService calendario,
            PasswordEncoder passwordEncoder,
            Clock reloj,
            @Value("${app.demo.password:}") String contrasena) {
        this.anios = anios;
        this.periodos = periodos;
        this.grados = grados;
        this.secciones = secciones;
        this.cursos = cursos;
        this.aulas = aulas;
        this.personas = personas;
        this.docentes = docentes;
        this.alumnos = alumnos;
        this.apoderados = apoderados;
        this.vinculos = vinculos;
        this.asignaciones = asignaciones;
        this.horarios = horarios;
        this.solicitudes = solicitudes;
        this.matriculas = matriculas;
        this.usuarios = usuarios;
        this.roles = roles;
        this.nacionalidades = nacionalidades;
        this.calendario = calendario;
        this.passwordEncoder = passwordEncoder;
        this.reloj = reloj;
        this.contrasena = contrasena;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void cargar() {
        LocalDate hoy = LocalDate.now(reloj);
        if (anios.existsByEstado(EstadoAnioAcademico.ACTIVO) || anios.findByAnio(hoy.getYear()).isPresent()) {
            LOGGER.warn("Datos de demo omitidos: ya existe un año activo o el año {}", hoy.getYear());
            return;
        }
        if (contrasena == null || contrasena.isBlank()) {
            throw new IllegalStateException("El perfil demo requiere la variable DEMO_PASSWORD");
        }
        PasswordPolicy.validate(contrasena, "demo");

        GradoAcademico grado = crearAnio(hoy);
        Seccion seccionA = crearSeccion("A", grado);
        Seccion seccionB = crearSeccion("B", grado);
        Curso matematica = crearCurso("DEMO-MAT", "Matemática", grado);
        Curso comunicacion = crearCurso("DEMO-COM", "Comunicación", grado);
        Aula aula101 = crearAula("DEMO-101", "Aula 101");
        Aula aula102 = crearAula("DEMO-102", "Aula 102");

        Docente docenteMatematica = crearDocente(
                "DOC-DEMO-1", "Rosa", "Aguirre", "Delgado", Especialidad.MATEMATICA, "demo.docente1");
        Docente docenteComunicacion = crearDocente(
                "DOC-DEMO-2", "Carlos", "Benites", "Ochoa", Especialidad.COMUNICACION, "demo.docente2");

        crearHorarioSemanal(seccionA, aula101, true,
                asignar(matematica, seccionA, docenteMatematica), asignar(comunicacion, seccionA, docenteComunicacion));
        crearHorarioSemanal(seccionB, aula102, false,
                asignar(matematica, seccionB, docenteMatematica), asignar(comunicacion, seccionB, docenteComunicacion));

        List<Alumno> alumnosA = matricular(seccionA, ALUMNOS_SECCION_A, "A");
        matricular(seccionB, ALUMNOS_SECCION_B, "B");
        crearAccesosFamilia(alumnosA.get(0));

        asignaciones.findAll().forEach(asignacion -> calendario.sincronizar(asignacion.getId()));
        LOGGER.info("Datos de demo cargados para el año {}", hoy.getYear());
    }

    /** Dos semestres que cubren todo el año, con el semestre de hoy como período activo. */
    private GradoAcademico crearAnio(LocalDate hoy) {
        AnioAcademico anio = new AnioAcademico();
        anio.setAnio(hoy.getYear());
        anio.setFechaInicio(hoy.withDayOfYear(1));
        anio.setFechaFin(LocalDate.of(hoy.getYear(), 12, 31));
        anio.setEstado(EstadoAnioAcademico.ACTIVO);
        anio.setClaveActiva(1);
        anio.setAdmisionAbierta(false);
        anios.save(anio);

        LocalDate finPrimero = LocalDate.of(hoy.getYear(), 6, 30);
        boolean enPrimero = !hoy.isAfter(finPrimero);
        crearPeriodo(anio, "Primer semestre", 1, anio.getFechaInicio(), finPrimero,
                enPrimero ? EstadoPeriodoAcademico.ACTIVO : EstadoPeriodoAcademico.CERRADO);
        crearPeriodo(anio, "Segundo semestre", 2, finPrimero.plusDays(1), anio.getFechaFin(),
                enPrimero ? EstadoPeriodoAcademico.PLANIFICADO : EstadoPeriodoAcademico.ACTIVO);

        GradoAcademico grado = new GradoAcademico();
        grado.setAnioAcademico(anio);
        grado.setNumero(GRADO);
        grado.setCapacidadDefault(CAPACIDAD_SECCION);
        return grados.save(grado);
    }

    private void crearPeriodo(
            AnioAcademico anio, String nombre, int orden, LocalDate inicio, LocalDate fin,
            EstadoPeriodoAcademico estado) {
        PeriodoAcademico periodo = new PeriodoAcademico();
        periodo.setAnioAcademico(anio);
        periodo.setNombre(nombre);
        periodo.setOrden(orden);
        periodo.setFechaInicio(inicio);
        periodo.setFechaFin(fin);
        periodo.setEstado(estado);
        periodo.setClaveActiva(estado == EstadoPeriodoAcademico.ACTIVO ? 1 : null);
        periodos.save(periodo);
    }

    private Seccion crearSeccion(String nombre, GradoAcademico grado) {
        Seccion seccion = new Seccion();
        seccion.setNombre(nombre);
        seccion.setGrado(grado.getNumero());
        seccion.setAnioAcademico(grado.getAnioAcademico().getAnio());
        seccion.setGradoAcademico(grado);
        seccion.setCapacidadMaxima(CAPACIDAD_SECCION);
        return secciones.save(seccion);
    }

    private Curso crearCurso(String codigo, String nombre, GradoAcademico grado) {
        Curso curso = new Curso();
        curso.setCodigo(codigo);
        curso.setNombre(nombre);
        curso.setHorasSemanales(HORAS_SEMANALES);
        curso.setGradoAcademico(grado);
        return cursos.save(curso);
    }

    private Aula crearAula(String codigo, String nombre) {
        Aula aula = new Aula();
        aula.setCodigo(codigo);
        aula.setNombre(nombre);
        aula.setTipoAula(TipoAula.AULA_REGULAR);
        aula.setCapacidad(CAPACIDAD_SECCION);
        aula.setUbicacion("Pabellón A");
        return aulas.save(aula);
    }

    private Docente crearDocente(
            String codigo, String nombres, String paterno, String materno, Especialidad especialidad,
            String usuario) {
        Persona persona = crearPersona(nombres, paterno, materno, LocalDate.of(1985, 4, 15), usuario);
        Docente docente = new Docente();
        docente.setPersona(persona);
        docente.setCodigoDocente(codigo);
        docente.setFechaIngreso(LocalDate.now(reloj).minusYears(3));
        docente.setEspecialidad(especialidad);
        docentes.save(docente);
        crearAcceso(persona, "DOCENTE", usuario);
        return docente;
    }

    private AsignacionCurso asignar(Curso curso, Seccion seccion, Docente docente) {
        AsignacionCurso asignacion = new AsignacionCurso();
        asignacion.setCurso(curso);
        asignacion.setSeccion(seccion);
        asignacion.setDocente(docente);
        return asignaciones.save(asignacion);
    }

    /**
     * Cada dia de clases tiene un bloque de cada curso, y se alternan para que ningun docente
     * tenga dos secciones a la vez: si la A empieza con Matematica, la B empieza con Comunicacion.
     */
    private void crearHorarioSemanal(
            Seccion seccion, Aula aula, boolean matematicaPrimeroLosLunes,
            AsignacionCurso matematica, AsignacionCurso comunicacion) {
        boolean matematicaPrimero = matematicaPrimeroLosLunes;
        for (DiaSemana dia : DIAS_DE_CLASE) {
            crearBloque(matematicaPrimero ? matematica : comunicacion, aula, dia, PRIMER_BLOQUE);
            crearBloque(matematicaPrimero ? comunicacion : matematica, aula, dia, SEGUNDO_BLOQUE);
            matematicaPrimero = !matematicaPrimero;
        }
    }

    private void crearBloque(AsignacionCurso asignacion, Aula aula, DiaSemana dia, LocalTime inicio) {
        Horario horario = new Horario();
        horario.setAsignacionCurso(asignacion);
        horario.setAula(aula);
        horario.setDiaSemana(dia);
        horario.setHoraInicio(inicio);
        horario.setHoraFin(inicio.plusMinutes(DURACION_BLOQUE_MINUTOS));
        horarios.save(horario);
    }

    private List<Alumno> matricular(Seccion seccion, List<String[]> nombres, String prefijo) {
        List<Alumno> resultado = new ArrayList<>();
        for (int i = 0; i < nombres.size(); i++) {
            String[] nombre = nombres.get(i);
            String clave = prefijo + (i + 1);
            Persona persona = crearPersona(nombre[0], nombre[1], nombre[2],
                    LocalDate.of(LocalDate.now(reloj).getYear() - 12, 3, i + 1), "alumno" + clave);
            Alumno alumno = new Alumno();
            alumno.setPersona(persona);
            alumno.setCodigoAlumno("EST-DEMO-" + clave);
            alumnos.save(alumno);

            SolicitudMatricula solicitud = new SolicitudMatricula();
            solicitud.setAlumno(alumno);
            solicitud.setSeccion(seccion);
            solicitud.setEstado(EstadoSolicitudMatricula.MATRICULA_FINALIZADA);
            solicitudes.save(solicitud);

            Matricula matricula = new Matricula();
            matricula.setAlumno(alumno);
            matricula.setSeccion(seccion);
            matricula.setSolicitudMatricula(solicitud);
            matricula.setCodigoMatricula("MAT-DEMO-" + clave);
            matricula.setAnioAcademico(seccion.getAnioAcademico());
            matricula.setFechaMatricula(Instant.now(reloj));
            matricula.setEstado(EstadoMatricula.ACTIVA);
            matriculas.save(matricula);
            resultado.add(alumno);
        }
        return resultado;
    }

    /** Un estudiante y su apoderado con acceso, para ver la asistencia y justificar desde su lado. */
    private void crearAccesosFamilia(Alumno alumno) {
        crearAcceso(alumno.getPersona(), "ALUMNO", "demo.alumno");

        Persona persona = crearPersona("Patricia", "Paredes", "Linares", LocalDate.of(1983, 9, 20), "demo.apoderado");
        Apoderado apoderado = new Apoderado();
        apoderado.setPersona(persona);
        apoderados.save(apoderado);
        crearAcceso(persona, "APODERADO", "demo.apoderado");

        AlumnoApoderado vinculo = new AlumnoApoderado();
        vinculo.setAlumno(alumno);
        vinculo.setApoderado(apoderado);
        vinculo.setParentesco(Parentesco.MADRE);
        vinculo.setPrincipal(true);
        vinculos.save(vinculo);
    }

    private Persona crearPersona(
            String nombres, String paterno, String materno, LocalDate nacimiento, String claveCorreo) {
        Nacionalidad peruana = nacionalidades.findByNombreIgnoreCase("Peruana").orElseThrow(() ->
                new IllegalStateException("Los datos de demo requieren que el bootstrap este habilitado"));
        Persona persona = new Persona();
        persona.setNombres(nombres);
        persona.setApellidoPaterno(paterno);
        persona.setApellidoMaterno(materno);
        persona.setTipoDocumento(TipoDocumento.DNI);
        persona.setNumeroDocumento(String.valueOf(siguienteDocumento++));
        persona.setNacionalidad(peruana);
        persona.setSexo(Sexo.PREFIERE_NO_INDICAR);
        persona.setFechaNacimiento(nacimiento);
        persona.setCorreo(claveCorreo + DOMINIO_CORREO);
        return personas.save(persona);
    }

    private void crearAcceso(Persona persona, String nombreRol, String usuario) {
        Rol rol = roles.findByNombreIgnoreCase(nombreRol).orElseThrow(() ->
                new IllegalStateException("Los datos de demo requieren que el bootstrap este habilitado"));
        AccesoUsuario acceso = new AccesoUsuario();
        acceso.setPersona(persona);
        acceso.setRol(rol);
        acceso.setUsername(usuario);
        acceso.setCorreo(persona.getCorreo());
        acceso.setContrasenaHash(passwordEncoder.encode(contrasena));
        acceso.setEstado(EstadoAcceso.ACTIVO);
        acceso.setRequiereCambioContrasena(false);
        acceso.setCorreoVerificado(true);
        usuarios.save(acceso);
    }
}
