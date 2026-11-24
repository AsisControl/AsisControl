package com.asiscontrol.config;

import com.asiscontrol.entity.Alumno;
import com.asiscontrol.entity.AlumnoApoderado;
import com.asiscontrol.entity.Apoderado;
import com.asiscontrol.entity.AsignacionCurso;
import com.asiscontrol.entity.AsistenciaAlumno;
import com.asiscontrol.entity.Aula;
import com.asiscontrol.entity.Calificacion;
import com.asiscontrol.entity.Competencia;
import com.asiscontrol.entity.ConceptoCobro;
import com.asiscontrol.entity.Curso;
import com.asiscontrol.entity.CursoCompetencia;
import com.asiscontrol.entity.DetalleInformeProgreso;
import com.asiscontrol.entity.Docente;
import com.asiscontrol.entity.EntregaTarea;
import com.asiscontrol.entity.Evaluacion;
import com.asiscontrol.entity.GradoAcademico;
import com.asiscontrol.entity.Horario;
import com.asiscontrol.entity.InformeProgreso;
import com.asiscontrol.entity.Matricula;
import com.asiscontrol.entity.OfertaCobro;
import com.asiscontrol.entity.Persona;
import com.asiscontrol.entity.RenovacionMatricula;
import com.asiscontrol.entity.Seccion;
import com.asiscontrol.entity.SolicitudMatricula;
import com.asiscontrol.entity.Tarea;
import com.asiscontrol.entity.TarifaPension;
import com.asiscontrol.entity.asistencia.EstadoSesionClase;
import com.asiscontrol.entity.asistencia.SesionClase;
import com.asiscontrol.entity.enums.DiaSemana;
import com.asiscontrol.entity.enums.Especialidad;
import com.asiscontrol.entity.enums.EstadoAnioAcademico;
import com.asiscontrol.entity.enums.EstadoAsistencia;
import com.asiscontrol.entity.enums.EstadoEvaluacion;
import com.asiscontrol.entity.enums.EstadoInformeProgreso;
import com.asiscontrol.entity.enums.EstadoMatricula;
import com.asiscontrol.entity.enums.EstadoPeriodoAcademico;
import com.asiscontrol.entity.enums.EstadoPublicacionTarea;
import com.asiscontrol.entity.enums.EstadoSolicitudMatricula;
import com.asiscontrol.entity.enums.NivelLogro;
import com.asiscontrol.entity.enums.Parentesco;
import com.asiscontrol.entity.enums.PeriodicidadCobro;
import com.asiscontrol.entity.enums.PeriodoEvaluacion;
import com.asiscontrol.entity.enums.ResultadoPromocion;
import com.asiscontrol.entity.enums.TipoAula;
import com.asiscontrol.entity.enums.TipoDocumento;
import com.asiscontrol.entity.enums.TipoEvaluacion;
import com.asiscontrol.entity.planificacion.AnioAcademico;
import com.asiscontrol.entity.planificacion.PeriodoAcademico;
import com.asiscontrol.repository.ConceptoCobroRepository;
import com.asiscontrol.service.AvisosAcademicos;
import com.asiscontrol.service.BeneficioCalculoService.CalculoBeneficio;
import com.asiscontrol.service.CicloCuentaService;
import com.asiscontrol.service.CodigoInstitucionalService;
import com.asiscontrol.service.CodigoInstitucionalService.Prefijo;
import com.asiscontrol.service.CompromisoPagoService;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

/** Escenarios ficticios para revisar el flujo académico sobre la demo local de pagos. */
@Component
@Profile("local & !mysql")
@ConditionalOnProperty(name = "app.retro-jp.demo.enabled", havingValue = "true")
public class RetroJpDemoDataInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger(RetroJpDemoDataInitializer.class);
    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final String CODIGO_ORIGEN = "JP-COM-ORIGEN";

    private record Area(String codigo, String curso, List<String> competencias) {}

    private static final List<Area> AREAS = List.of(
            new Area("COM", "Comunicación", List.of("Se comunica oralmente", "Lee diversos tipos de textos",
                    "Escribe diversos tipos de textos")),
            new Area("MAT", "Matemática", List.of("Resuelve problemas de cantidad", "Resuelve problemas de regularidad",
                    "Resuelve problemas de forma y movimiento", "Resuelve problemas de gestión de datos")),
            new Area("CYT", "Ciencia y Tecnología", List.of("Indaga mediante métodos científicos",
                    "Explica el mundo físico", "Diseña soluciones tecnológicas")),
            new Area("CCSS", "Ciencias Sociales", List.of("Construye interpretaciones históricas",
                    "Gestiona responsablemente el espacio y el ambiente", "Gestiona los recursos económicos")),
            new Area("ING", "Inglés", List.of("Se comunica oralmente en inglés", "Lee textos en inglés",
                    "Escribe textos en inglés")),
            new Area("ART", "Arte y Cultura", List.of("Aprecia manifestaciones artístico-culturales",
                    "Crea proyectos desde los lenguajes artísticos")),
            new Area("EF", "Educación Física", List.of("Se desenvuelve mediante su motricidad",
                    "Asume una vida saludable", "Interactúa mediante sus habilidades sociomotrices")));

    private final EntityManager em;
    private final CicloCuentaService cuentas;
    private final CodigoInstitucionalService codigos;
    private final AvisosAcademicos avisos;
    private final CompromisoPagoService compromisos;
    private final ConceptoCobroRepository conceptos;
    private final String password;

    public RetroJpDemoDataInitializer(EntityManager em, CicloCuentaService cuentas, CodigoInstitucionalService codigos,
            AvisosAcademicos avisos, CompromisoPagoService compromisos, ConceptoCobroRepository conceptos,
            @Value("${app.pagos.demo.password:}") String password) {
        this.em = em;
        this.cuentas = cuentas;
        this.codigos = codigos;
        this.avisos = avisos;
        this.compromisos = compromisos;
        this.conceptos = conceptos;
        this.password = password;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(10)
    @Transactional
    public void preparar() {
        codigos.bloquearSecuenciaCuentas();
        if (!em.createQuery("select c.id from Curso c where c.codigo=:codigo", Long.class)
                .setParameter("codigo", CODIGO_ORIGEN).getResultList().isEmpty()) return;

        var lucia = estudianteDemo("DEMO-PAGOS-1-1");
        var mateo = estudianteDemo("DEMO-PAGOS-1-2");
        var alejandra = estudianteDemo("DEMO-PAGOS-2-1");
        var matriculaLucia = matriculaActual(lucia);
        var seccionBase = matriculaLucia.getSeccion();
        var gradoBase = seccionBase.getGradoAcademico();
        var actual = gradoBase.getAnioAcademico();
        LocalDate hoy = LocalDate.now(LIMA);
        int numero = actual.getAnio();

        var anterior = em.createQuery("select a from AnioAcademico a where a.anio=:anio", AnioAcademico.class)
                .setParameter("anio", numero - 1).getResultList().stream().findFirst()
                .orElseGet(() -> anioAnterior(numero - 1));
        actual.setAdmisionAbierta(true);
        var gradoAnterior = grado(anterior, 1);
        var gradoDestino = grado(actual, 2);
        var origen = seccion(gradoAnterior, "Historia JP", 25);
        var destino = seccion(gradoDestino, "A JP", 20);
        var llena = seccion(gradoDestino, "B JP", 1);
        var periodo = periodo(actual);

        var carla = vinculoPrincipal(lucia).getApoderado();
        var jorge = vinculoPrincipal(alejandra).getApoderado();
        var docente = docente(carla.getPersona(), Especialidad.COMUNICACION);
        var otroDocente = docente(persona("Luis", "Vega", "91910000", hoy.minusYears(34)), Especialidad.MATEMATICA);
        seccionBase.setDocenteTutor(docente);
        acceso(lucia.getPersona(), "ALUMNO", lucia.getCodigoAlumno(), "lucia");
        acceso(mateo.getPersona(), "ALUMNO", mateo.getCodigoAlumno(), "mateo");
        acceso(alejandra.getPersona(), "ALUMNO", alejandra.getCodigoAlumno(), "familia.torres");

        var comunicacion = curso(gradoBase, "JP-COM-ACTUAL", "Comunicación · curso actual", 5);
        var comunicacionAnterior = curso(gradoAnterior, CODIGO_ORIGEN, "Comunicación · curso anterior", 5);
        var cursoDestino = curso(gradoDestino, "JP-COM-2", "Comunicación de segundo", 5);
        var alumnos = List.of(lucia, mateo, alejandra);
        var asignacion = asignacion(comunicacion, seccionBase, docente);
        Competencia lectura = catalogo(gradoBase, comunicacion, comunicacionAnterior, seccionBase, otroDocente);
        asociar(cursoDestino, lectura, 1);
        gradoBase.setPlanDefinido(true);
        gradoAnterior.setPlanDefinido(true);
        horarioYAsistencia(asignacion, alumnos, hoy);
        tareasYResultados(asignacion, lectura, periodo, alumnos, hoy);
        renovarYPrepararCobros(origen, destino, llena, carla, jorge, matriculaLucia, hoy);
        LOGGER.info("Datos JP preparados para el año {} y el plan histórico {}.", numero, numero - 1);
    }

    private Alumno estudianteDemo(String codigo) {
        return em.createQuery("select a from Alumno a where a.codigoAlumno=:codigo", Alumno.class)
                .setParameter("codigo", codigo).getResultList().stream().findFirst().orElseThrow(
                        () -> new IllegalStateException("Activa PAYMENTS_DEMO_ENABLED para preparar los datos JP."));
    }

    private Matricula matriculaActual(Alumno alumno) {
        return em.createQuery("select m from Matricula m where m.alumno=:alumno and m.estado=:estado", Matricula.class)
                .setParameter("alumno", alumno).setParameter("estado", EstadoMatricula.ACTIVA).getSingleResult();
    }

    private AlumnoApoderado vinculoPrincipal(Alumno alumno) {
        return em.createQuery("select v from AlumnoApoderado v where v.alumno=:alumno and v.principal=true",
                AlumnoApoderado.class).setParameter("alumno", alumno).getSingleResult();
    }

    private AnioAcademico anioAnterior(int numero) {
        var anio = new AnioAcademico();
        anio.setAnio(numero);
        anio.setEstado(EstadoAnioAcademico.CERRADO);
        anio.setNombrePlanEstudios("Plan de secundaria " + numero);
        anio.setFechaInicio(LocalDate.of(numero, 1, 1));
        anio.setFechaFin(LocalDate.of(numero, 12, 31));
        em.persist(anio);
        return anio;
    }

    private GradoAcademico grado(AnioAcademico anio, int numero) {
        return em.createQuery("select g from GradoAcademico g where g.anioAcademico=:anio and g.numero=:numero",
                GradoAcademico.class).setParameter("anio", anio).setParameter("numero", numero)
                .getResultList().stream().findFirst().orElseGet(() -> {
                    var grado = new GradoAcademico();
                    grado.setAnioAcademico(anio);
                    grado.setNumero(numero);
                    grado.setCapacidadDefault(25);
                    em.persist(grado);
                    return grado;
                });
    }

    private Seccion seccion(GradoAcademico grado, String nombre, int capacidad) {
        var seccion = new Seccion();
        seccion.setNombre(nombre);
        seccion.setGradoAcademico(grado);
        seccion.setGrado(grado.getNumero());
        seccion.setAnioAcademico(grado.getAnioAcademico().getAnio());
        seccion.setCapacidadMaxima(capacidad);
        em.persist(seccion);
        return seccion;
    }

    private PeriodoAcademico periodo(AnioAcademico anio) {
        var periodo = new PeriodoAcademico();
        periodo.setAnioAcademico(anio);
        periodo.setNombre("Período académico JP");
        periodo.setOrden(1);
        periodo.setFechaInicio(anio.getFechaInicio());
        periodo.setFechaFin(anio.getFechaFin());
        periodo.setEstado(EstadoPeriodoAcademico.ACTIVO);
        periodo.setClaveActiva(1);
        em.persist(periodo);
        return periodo;
    }

    private Persona persona(String nombre, String apellido, String documento, LocalDate nacimiento) {
        var persona = new Persona();
        persona.setNombres(nombre);
        persona.setApellidoPaterno(apellido);
        persona.setApellidoMaterno("Pruebas");
        persona.setNumeroDocumento(documento);
        persona.setTipoDocumento(TipoDocumento.DNI);
        persona.setFechaNacimiento(nacimiento);
        em.persist(persona);
        return persona;
    }

    private void acceso(Persona persona, String rol, String codigoPerfil, String alias) {
        var alta = cuentas.altaConfigurada(persona, rol, codigoPerfil, alias + "@asiscontrol.example.test",
                password, true);
        LOGGER.info("Cuenta JP {}: código {} (usuario {}, rol {}).", alias, alta.accessCode(), alta.username(), rol);
    }

    private Docente docente(Persona persona, Especialidad especialidad) {
        var docente = new Docente();
        docente.setPersona(persona);
        docente.setCodigoDocente(codigos.siguiente(Prefijo.DOC));
        docente.setFechaIngreso(LocalDate.now(LIMA).minusYears(2));
        docente.setEspecialidad(especialidad);
        em.persist(docente);
        acceso(persona, "DOCENTE", docente.getCodigoDocente(), "docente." + especialidad.name().toLowerCase(java.util.Locale.ROOT));
        return docente;
    }

    private Curso curso(GradoAcademico grado, String codigo, String nombre, int horas) {
        var curso = new Curso();
        curso.setCodigo(codigo);
        curso.setNombre(nombre);
        curso.setHorasSemanales(horas);
        curso.setGradoAcademico(grado);
        em.persist(curso);
        grado.incluirCurso(curso);
        return curso;
    }

    private AsignacionCurso asignacion(Curso curso, Seccion seccion, Docente docente) {
        var asignacion = new AsignacionCurso();
        asignacion.setCurso(curso);
        asignacion.setSeccion(seccion);
        asignacion.setDocente(docente);
        em.persist(asignacion);
        return asignacion;
    }

    private Competencia catalogo(GradoAcademico grado, Curso actual, Curso anterior, Seccion seccion, Docente docente) {
        Competencia lectura = null;
        for (var area : AREAS) {
            boolean comunicacion = area.codigo().equals("COM");
            var curso = comunicacion ? anterior : curso(grado, "JP-" + area.codigo(), area.curso(), 4);
            if (area.codigo().equals("MAT")) asignacion(curso, seccion, docente);
            int orden = 1;
            for (var nombre : area.competencias()) {
                var competencia = new Competencia();
                competencia.setCodigo("JP-" + area.codigo() + "-" + orden);
                competencia.setNombre(nombre);
                em.persist(competencia);
                asociar(curso, competencia, orden);
                if (comunicacion && orden == 2) {
                    lectura = competencia;
                    asociar(actual, competencia, 1);
                }
                orden++;
            }
        }
        return java.util.Objects.requireNonNull(lectura);
    }

    private void asociar(Curso curso, Competencia competencia, int orden) {
        var asociacion = new CursoCompetencia();
        asociacion.setCurso(curso);
        asociacion.setCompetencia(competencia);
        asociacion.setOrden(orden);
        em.persist(asociacion);
        curso.getGradoAcademico().getCompetenciasPlan().add(
                new com.asiscontrol.entity.CompetenciaEnPlan(curso.getId(), competencia));
    }

    private void horarioYAsistencia(AsignacionCurso asignacion, List<Alumno> alumnos, LocalDate hoy) {
        var aula = new Aula();
        aula.setCodigo("JP-A101");
        aula.setNombre("Aula 101");
        aula.setTipoAula(TipoAula.AULA_REGULAR);
        aula.setCapacidad(25);
        aula.setUbicacion("Pabellón académico");
        em.persist(aula);
        var horario = new Horario();
        horario.setAsignacionCurso(asignacion);
        horario.setAula(aula);
        horario.setDiaSemana(DiaSemana.VIERNES);
        horario.setHoraInicio(LocalTime.of(8, 0));
        horario.setHoraFin(LocalTime.of(8, 45));
        em.persist(horario);
        LocalDate fecha = hoy.with(TemporalAdjusters.previous(java.time.DayOfWeek.FRIDAY));
        var sesion = new SesionClase();
        sesion.setAsignacionCurso(asignacion);
        sesion.setHorario(horario);
        sesion.setFecha(fecha);
        sesion.setHoraInicio(horario.getHoraInicio());
        sesion.setHoraFin(horario.getHoraFin());
        sesion.setEstado(EstadoSesionClase.TOMADA);
        sesion.setDocenteRegistrador(asignacion.getDocente());
        sesion.setFechaHoraFinalizacion(fecha.atTime(8, 40));
        em.persist(sesion);
        var estados = List.of(EstadoAsistencia.INASISTENCIA, EstadoAsistencia.PRESENTE, EstadoAsistencia.TARDANZA);
        for (int i = 0; i < alumnos.size(); i++) {
            var registro = new AsistenciaAlumno();
            registro.setAlumno(alumnos.get(i));
            registro.setAsignacionCurso(asignacion);
            registro.setDocenteRegistrador(asignacion.getDocente());
            registro.setSesionClase(sesion);
            registro.setFechaRegistro(fecha);
            registro.setHoraRegistro(LocalTime.of(8, 40));
            registro.setEstadoAsistencia(estados.get(i));
            em.persist(registro);
        }
    }

    private void tareasYResultados(AsignacionCurso asignacion, Competencia lectura, PeriodoAcademico periodo,
            List<Alumno> alumnos, LocalDate hoy) {
        var tarea = new Tarea();
        tarea.setAsignacionCurso(asignacion);
        tarea.setNombre("Comentario de lectura");
        tarea.setDescripcion("Explica con tus propias palabras la idea principal y cita un fragmento.");
        tarea.setFechaPublicacion(hoy.minusDays(3).atTime(8, 0));
        tarea.setFechaLimite(hoy.plusDays(12).atTime(20, 0));
        tarea.setPuntajeMaximo(20);
        tarea.setMaxArchivosEntrega(2);
        tarea.setEstadoPublicacion(EstadoPublicacionTarea.PUBLICADA);
        em.persist(tarea);
        var entrega = new EntregaTarea();
        entrega.setAlumno(alumnos.get(1));
        entrega.setTarea(tarea);
        entrega.setComentario("Entrego mi análisis del texto.");
        entrega.setFechaHoraEntrega(hoy.minusDays(1).atTime(16, 0));
        em.persist(entrega);

        var publicada = evaluacion(asignacion, lectura, "Comprensión de textos", hoy.minusDays(4), EstadoEvaluacion.PUBLICADA);
        evaluacion(asignacion, lectura, "Análisis de una noticia", hoy, EstadoEvaluacion.BORRADOR);
        var niveles = List.of(NivelLogro.A, NivelLogro.B, NivelLogro.AD);
        for (int i = 0; i < alumnos.size(); i++) {
            var alumno = alumnos.get(i);
            var calificacion = new Calificacion();
            calificacion.setEvaluacion(publicada);
            calificacion.setAlumno(alumno);
            calificacion.setCompetencia(lectura);
            calificacion.setNivelLogro(niveles.get(i));
            calificacion.setFechaRegistro(Instant.now());
            em.persist(calificacion);
            publicarInforme(asignacion, periodo, calificacion);
            avisos.enviar(alumno, "Resultados publicados", publicada.getTitulo());
        }
    }

    private Evaluacion evaluacion(AsignacionCurso asignacion, Competencia competencia, String titulo,
            LocalDate fecha, EstadoEvaluacion estado) {
        var evaluacion = new Evaluacion();
        evaluacion.setAsignacionCurso(asignacion);
        evaluacion.setTitulo(titulo);
        evaluacion.setFechaEvaluacion(fecha);
        evaluacion.setTipoEvaluacion(TipoEvaluacion.PRACTICA_CALIFICADA);
        evaluacion.setPeriodoEvaluacion(PeriodoEvaluacion.PERIODO_ACADEMICO);
        evaluacion.setPonderacionPorcentaje(BigDecimal.valueOf(50));
        evaluacion.setEstado(estado);
        if (estado == EstadoEvaluacion.PUBLICADA) evaluacion.setFechaPublicacion(Instant.now());
        evaluacion.getCompetencias().add(competencia);
        em.persist(evaluacion);
        return evaluacion;
    }

    private void publicarInforme(AsignacionCurso asignacion, PeriodoAcademico periodo, Calificacion calificacion) {
        var evaluacion = calificacion.getEvaluacion();
        var competencia = calificacion.getCompetencia();
        var informe = new InformeProgreso();
        informe.setAlumno(calificacion.getAlumno());
        informe.setAsignacionCurso(asignacion);
        informe.setPeriodoAcademico(periodo);
        informe.setDocente(asignacion.getDocente());
        informe.setEstado(EstadoInformeProgreso.PUBLICADA);
        informe.setFechaPublicacion(evaluacion.getFechaPublicacion());
        em.persist(informe);
        var detalle = new DetalleInformeProgreso();
        detalle.setInforme(informe);
        detalle.setCalificacion(calificacion);
        detalle.setCompetencia(competencia);
        detalle.setEvaluacion(evaluacion);
        detalle.setNombreCompetencia(competencia.getNombre());
        detalle.setTituloEvaluacion(evaluacion.getTitulo());
        detalle.setFechaEvaluacion(evaluacion.getFechaEvaluacion());
        detalle.setPonderacionPorcentaje(evaluacion.getPonderacionPorcentaje());
        detalle.setNivelLogro(calificacion.getNivelLogro());
        em.persist(detalle);
    }

    private void renovarYPrepararCobros(Seccion origen, Seccion destino, Seccion llena,
            Apoderado carla, Apoderado jorge, Matricula matriculaLucia, LocalDate hoy) {
        var pension = conceptos.findByCodigoIgnoreCase("PENSION").orElseThrow();
        tarifa(pension, destino.getAnioAcademico(), 1, 380);
        tarifa(pension, destino.getAnioAcademico(), 2, 420);
        var tarifaHistorica = tarifa(pension, origen.getAnioAcademico(), 1, 350);

        var actividad = new ConceptoCobro();
        actividad.setCodigo("JP-VISITA");
        actividad.setNombre("Visita educativa");
        actividad.setPeriodicidad(PeriodicidadCobro.UNICO);
        actividad.setRequiereAceptacion(true);
        em.persist(actividad);
        var oferta = new OfertaCobro();
        oferta.setMatricula(matriculaLucia);
        oferta.setConcepto(actividad);
        oferta.setConceptoNombre(actividad.getNombre());
        oferta.setReferencia("VISITA-JP");
        oferta.setClaveOferta("VISITA-JP-LUCIA");
        oferta.setTitulo(actividad.getNombre());
        oferta.setDescripcion("Salida opcional al museo de ciencias.");
        oferta.setMontoBase(BigDecimal.valueOf(45));
        oferta.setFechaLimiteAceptacion(hoy.plusDays(10));
        oferta.setFechaVencimiento(hoy.plusDays(25));
        em.persist(oferta);

        var emilia = nuevaFicha("Emilia", carla, 1, hoy);
        var sofia = nuevaFicha("Sofía", carla, 2, hoy);
        var diego = nuevaFicha("Diego", jorge, 3, hoy);
        var bruno = nuevaFicha("Bruno", jorge, 4, hoy);
        var plaza = nuevaFicha("Adriana", jorge, 5, hoy);
        nuevaFicha("Nicolás", carla, 6, hoy);
        matricula(plaza, llena, null);

        renovacion(matricula(emilia, origen, ResultadoPromocion.PROMOVIDO), destino);
        renovacion(matricula(sofia, origen, ResultadoPromocion.NO_PROMOVIDO), destino);
        var deuda = matricula(diego, origen, ResultadoPromocion.PROMOVIDO);
        var monto = tarifaHistorica.getMontoMensual();
        compromisos.emitirPension(deuda, pension, tarifaHistorica, 12,
                new CalculoBeneficio(monto, BigDecimal.ZERO, monto, null, null, null));
        renovacion(deuda, destino);
        renovacion(matricula(bruno, origen, ResultadoPromocion.PROMOVIDO), llena);
    }

    private TarifaPension tarifa(ConceptoCobro pension, int anio, int grado, int monto) {
        var tarifa = new TarifaPension();
        tarifa.setConcepto(pension);
        tarifa.setAnioAcademico(anio);
        tarifa.setGrado(grado);
        tarifa.setMontoMensual(BigDecimal.valueOf(monto));
        em.persist(tarifa);
        return tarifa;
    }

    private Alumno nuevaFicha(String nombre, Apoderado apoderado, int numero, LocalDate hoy) {
        var alumno = new Alumno();
        alumno.setCodigoAlumno("DEMO-JP-" + numero);
        alumno.setPersona(persona(nombre, apoderado.getPersona().getApellidoPaterno(), "9191100" + numero, hoy.minusYears(13)));
        em.persist(alumno);
        var vinculo = new AlumnoApoderado();
        vinculo.setAlumno(alumno);
        vinculo.setApoderado(apoderado);
        vinculo.setParentesco(numero == 1 || numero == 2 || numero == 6 ? Parentesco.MADRE : Parentesco.PADRE);
        vinculo.setPrincipal(true);
        em.persist(vinculo);
        return alumno;
    }

    private Matricula matricula(Alumno alumno, Seccion seccion, ResultadoPromocion promocion) {
        var solicitud = new SolicitudMatricula();
        solicitud.setAlumno(alumno);
        solicitud.setSeccion(seccion);
        solicitud.setAnioAcademico(seccion.getAnioAcademico());
        solicitud.setGrado(seccion.getGrado());
        solicitud.setEstado(EstadoSolicitudMatricula.MATRICULA_FINALIZADA);
        em.persist(solicitud);
        var matricula = new Matricula();
        matricula.setAlumno(alumno);
        matricula.setSeccion(seccion);
        matricula.setSolicitudMatricula(solicitud);
        matricula.setCodigoMatricula("MAT-" + alumno.getCodigoAlumno());
        matricula.setAnioAcademico(seccion.getAnioAcademico());
        matricula.setEstado(EstadoMatricula.ACTIVA);
        matricula.setFechaMatricula(LocalDate.of(seccion.getAnioAcademico(), 3, 1).atStartOfDay(LIMA).toInstant());
        matricula.setResultadoPromocion(promocion);
        if (promocion != null) matricula.setMotivoPromocion("Resultado oficial de fin de año para la prueba local.");
        em.persist(matricula);
        return matricula;
    }

    private void renovacion(Matricula anterior, Seccion destino) {
        anterior.setEstado(EstadoMatricula.FINALIZADA);
        var renovacion = new RenovacionMatricula();
        renovacion.setAlumno(anterior.getAlumno());
        renovacion.setMatriculaAnterior(anterior);
        renovacion.setAnioDestino(destino.getAnioAcademico());
        renovacion.setGradoDestino(destino.getGrado());
        renovacion.setSeccion(destino);
        em.persist(renovacion);
    }
}
