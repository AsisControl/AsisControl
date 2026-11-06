package com.asiscontrol.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.asiscontrol.dto.academico.AcademicoDtos.ActualizarAsignacionCursoRequest;
import com.asiscontrol.dto.academico.AcademicoDtos.AsignacionCursoResponse;
import com.asiscontrol.dto.academico.AcademicoDtos.CrearAsignacionCursoRequest;
import com.asiscontrol.dto.academico.AcademicoDtos.CrearAulaRequest;
import com.asiscontrol.dto.academico.AcademicoDtos.CrearCursoRequest;
import com.asiscontrol.dto.academico.AcademicoDtos.CrearHorarioRequest;
import com.asiscontrol.dto.academico.AcademicoDtos.CrearSeccionRequest;
import com.asiscontrol.dto.academico.AcademicoDtos.EstadoAsignacion;
import com.asiscontrol.dto.academico.AcademicoDtos.HorarioProgramadoAsignacion;
import com.asiscontrol.dto.academico.AcademicoDtos.RetirarAsignacionCursoRequest;
import com.asiscontrol.dto.academico.ConfiguracionDtos.CrearAnio;
import com.asiscontrol.dto.academico.ConfiguracionDtos.CrearGrado;
import com.asiscontrol.dto.persona.PersonaDtos;
import com.asiscontrol.entity.RegistroAuditoria;
import com.asiscontrol.entity.enums.DiaSemana;
import com.asiscontrol.entity.enums.Especialidad;
import com.asiscontrol.entity.enums.EstadoAnioAcademico;
import com.asiscontrol.entity.enums.Sexo;
import com.asiscontrol.entity.enums.TipoAula;
import com.asiscontrol.entity.enums.TipoDocumento;
import com.asiscontrol.exception.BusinessRuleException;
import com.asiscontrol.exception.ConflictException;
import com.asiscontrol.repository.AsignacionCursoRepository;
import com.asiscontrol.repository.DocenteRepository;
import com.asiscontrol.repository.GradoAcademicoRepository;
import com.asiscontrol.repository.RegistroAuditoriaRepository;
import com.asiscontrol.repository.planificacion.AnioAcademicoRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/** US-58 / PLA-06: asignacion de docentes a cursos y secciones. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AsignacionDocenteIntegrationTest {
    private static final int ANIO = 2084;

    @Autowired AsignacionCursoService asignaciones;
    @Autowired ConfiguracionAcademicaService configuracion;
    @Autowired SeccionService secciones;
    @Autowired CursoService cursos;
    @Autowired PersonaService personas;
    @Autowired AulaService aulas;
    @Autowired HorarioService horarios;
    @Autowired ModuloAccessGuard accessGuard;
    @Autowired AsignacionCursoRepository asignacionRepository;
    @Autowired DocenteRepository docenteRepository;
    @Autowired GradoAcademicoRepository gradoRepository;
    @Autowired AnioAcademicoRepository anioRepository;
    @Autowired RegistroAuditoriaRepository auditorias;

    private Long seccionA;
    private Long seccionB;
    private Long curso;

    @AfterEach void limpiarSesion() {
        SecurityContextHolder.clearContext();
    }

    private void oferta() {
        configuracion.crear(new CrearAnio(ANIO, LocalDate.of(ANIO, 3, 1), LocalDate.of(ANIO, 12, 20)));
        configuracion.agregarGrado(ANIO, new CrearGrado(1, 30));
        seccionA = secciones.crear(new CrearSeccionRequest("A", 1, ANIO, 30, null, null)).id();
        seccionB = secciones.crear(new CrearSeccionRequest("B", 1, ANIO, 30, null, null)).id();
        curso = nuevoCurso();
    }

    private Long nuevoCurso() {
        String clave = clave();
        Long grado = gradoRepository.findByAnioAcademicoAnioAndNumero(ANIO, 1).orElseThrow().getId();
        return cursos.crear(new CrearCursoRequest("C" + clave, "Curso " + clave, null, 4, grado)).id();
    }

    private Long docente() {
        String clave = clave();
        var persona = new PersonaDtos.PersonaRequest("Docente", clave, "Prueba",
                TipoDocumento.PASAPORTE, clave, null, Sexo.PREFIERE_NO_INDICAR,
                LocalDate.of(1985, 1, 1), null, clave.toLowerCase() + "@example.test", null);
        return personas.createDocente(new PersonaDtos.DocenteCreateRequest(
                persona, "D" + clave, LocalDate.of(2020, 1, 1), Especialidad.MATEMATICA)).id();
    }

    private Long horario(AsignacionCursoResponse asignacion, LocalTime inicio) {
        Long aula = aulas.crear(new CrearAulaRequest("A" + clave(), "Aula", TipoAula.AULA_REGULAR, 35, null)).id();
        return horarios.crear(new CrearHorarioRequest(
                asignacion.id(), aula, DiaSemana.LUNES, inicio, inicio.plusHours(1))).id();
    }

    private void comoUsuario(String rol, Long docenteId) {
        Long personaId = docenteId == null ? 999_999L : docenteRepository.findById(docenteId).orElseThrow().getPersona().getId();
        Instant ahora = Instant.now();
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject("usuario-" + rol)
                .issuedAt(ahora).expiresAt(ahora.plusSeconds(600))
                .claim("personaId", personaId).claim("rol", rol).build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_" + rol))));
    }

    private List<RegistroAuditoria> auditoria(Long asignacionId, String accion) {
        return auditorias.findByEntidadAndRegistroIdOrderByFechaHoraAsc("AsignacionCurso", asignacionId.toString())
                .stream().filter(r -> r.getAccion().equals(accion)).toList();
    }

    private static String clave() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
    }

    @Test
    void creaUnaAsignacionActivaYAuditaElContexto() {
        oferta();
        Long docente = docente();

        AsignacionCursoResponse creada = asignaciones.crear(new CrearAsignacionCursoRequest(curso, seccionA, docente));

        assertThat(creada.activo()).isTrue();
        assertThat(creada.grado()).isEqualTo(1);
        assertThat(creada.horariosActivos()).isZero();
        assertThat(auditoria(creada.id(), "CREAR_ASIGNACION_CURSO")).singleElement()
                .satisfies(r -> assertThat(r.getNuevoValor())
                        .contains("docenteId=" + docente, "cursoId=" + curso, "seccionId=" + seccionA,
                                "anio=" + ANIO, "activo=true"));
    }

    @Test
    void rechazaLaMismaCombinacionYUnAnioCerrado() {
        oferta();
        asignaciones.crear(new CrearAsignacionCursoRequest(curso, seccionA, docente()));

        assertThatThrownBy(() -> asignaciones.crear(new CrearAsignacionCursoRequest(curso, seccionA, docente())))
                .isInstanceOfSatisfying(ConflictException.class,
                        e -> assertThat(e.getCode()).isEqualTo("ASIGNACION_CURSO_DUPLICADA"));

        var anio = anioRepository.findByAnio(ANIO).orElseThrow();
        anio.setEstado(EstadoAnioAcademico.CERRADO);
        anioRepository.saveAndFlush(anio);
        assertThatThrownBy(() -> asignaciones.crear(new CrearAsignacionCursoRequest(curso, seccionB, docente())))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        e -> assertThat(e.getCode()).isEqualTo("ANIO_NO_VIGENTE"));
    }

    @Test
    void noRetiraAUnDocenteConBloquesDeHorarioYLosInforma() {
        oferta();
        var asignacion = asignaciones.crear(new CrearAsignacionCursoRequest(curso, seccionA, docente()));
        Long bloque = horario(asignacion, LocalTime.of(8, 0));
        long version = asignacionRepository.findById(asignacion.id()).orElseThrow().getVersion();

        assertThatThrownBy(() -> asignaciones.retirar(asignacion.id(),
                        new RetirarAsignacionCursoRequest("Cambio de carga", version)))
                .isInstanceOfSatisfying(ConflictException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("ASIGNACION_CON_HORARIOS_FUTUROS");
                    assertThat(e.getDetails()).singleElement()
                            .isInstanceOfSatisfying(HorarioProgramadoAsignacion.class,
                                    h -> assertThat(h.horarioId()).isEqualTo(bloque));
                });

        horarios.eliminar(bloque);
        AsignacionCursoResponse retirada = asignaciones.retirar(asignacion.id(),
                new RetirarAsignacionCursoRequest("Cambio de carga", version));
        assertThat(retirada.activo()).isFalse();
        assertThat(auditoria(asignacion.id(), "RETIRAR_ASIGNACION_CURSO")).singleElement()
                .satisfies(r -> {
                    assertThat(r.getMotivo()).isEqualTo("Cambio de carga");
                    assertThat(r.getValorAnterior()).contains("activo=true", "seccionId=" + seccionA);
                    assertThat(r.getNuevoValor()).contains("activo=false");
                });
    }

    @Test
    void elRetiroExigeMotivoYVersionVigente() {
        oferta();
        var asignacion = asignaciones.crear(new CrearAsignacionCursoRequest(curso, seccionA, docente()));
        long version = asignacionRepository.findById(asignacion.id()).orElseThrow().getVersion();

        assertThatThrownBy(() -> asignaciones.retirar(asignacion.id(), new RetirarAsignacionCursoRequest(" ", version)))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> asignaciones.retirar(asignacion.id(),
                        new RetirarAsignacionCursoRequest("Motivo", version + 5)))
                .isInstanceOfSatisfying(ConflictException.class,
                        e -> assertThat(e.getCode()).isEqualTo("VERSION_DESACTUALIZADA"));
    }

    @Test
    void trasUnRetiroSePuedeVolverAAsignarElCursoConOtroDocente() {
        oferta();
        Long primero = docente();
        Long segundo = docente();
        var asignacion = asignaciones.crear(new CrearAsignacionCursoRequest(curso, seccionA, primero));
        long version = asignacionRepository.findById(asignacion.id()).orElseThrow().getVersion();
        asignaciones.retirar(asignacion.id(), new RetirarAsignacionCursoRequest("Licencia", version));

        AsignacionCursoResponse nueva = asignaciones.crear(new CrearAsignacionCursoRequest(curso, seccionA, segundo));

        assertThat(nueva.activo()).isTrue();
        assertThat(nueva.docenteId()).isEqualTo(segundo);
        assertThat(auditoria(nueva.id(), "REACTIVAR_ASIGNACION_CURSO")).singleElement()
                .satisfies(r -> {
                    assertThat(r.getValorAnterior()).contains("docenteId=" + primero, "activo=false");
                    assertThat(r.getNuevoValor()).contains("docenteId=" + segundo, "activo=true");
                });
    }

    @Test
    void unDocenteRetiradoYaNoOperaEnEseCursoPeroElAdministradorSi() {
        oferta();
        Long docente = docente();
        var asignacion = asignaciones.crear(new CrearAsignacionCursoRequest(curso, seccionA, docente));

        comoUsuario("DOCENTE", docente);
        assertThatCode(() -> accessGuard.verificarAsignacionDocente(
                asignacionRepository.findById(asignacion.id()).orElseThrow())).doesNotThrowAnyException();

        long version = asignacionRepository.findById(asignacion.id()).orElseThrow().getVersion();
        asignaciones.retirar(asignacion.id(), new RetirarAsignacionCursoRequest("Fin de contrato", version));
        var retirada = asignacionRepository.findById(asignacion.id()).orElseThrow();
        assertThatThrownBy(() -> accessGuard.verificarAsignacionDocente(retirada))
                .isInstanceOf(AccessDeniedException.class);

        comoUsuario("ADMINISTRADOR", null);
        assertThatCode(() -> accessGuard.verificarAsignacionDocente(retirada)).doesNotThrowAnyException();
    }

    @Test
    void unDocenteSoloVeSusAsignaciones() {
        oferta();
        Long propio = docente();
        Long ajeno = docente();
        var mia = asignaciones.crear(new CrearAsignacionCursoRequest(curso, seccionA, propio));
        var otra = asignaciones.crear(new CrearAsignacionCursoRequest(curso, seccionB, ajeno));

        comoUsuario("DOCENTE", propio);
        assertThat(asignaciones.listar(EstadoAsignacion.TODAS, null, null, null, ANIO, PageRequest.of(0, 50)).getContent())
                .extracting(AsignacionCursoResponse::id).containsExactly(mia.id());
        assertThatThrownBy(() -> asignaciones.obtener(otra.id())).isInstanceOf(AccessDeniedException.class);

        comoUsuario("ADMINISTRADOR", null);
        assertThat(asignaciones.listar(EstadoAsignacion.ACTIVAS, null, curso, null, ANIO, PageRequest.of(0, 50)).getContent())
                .extracting(AsignacionCursoResponse::id).containsExactlyInAnyOrder(mia.id(), otra.id());
        assertThat(asignaciones.listar(EstadoAsignacion.ACTIVAS, ajeno, null, null, null, PageRequest.of(0, 50)).getContent())
                .extracting(AsignacionCursoResponse::id).containsExactly(otra.id());
    }

    @Test
    void conBloquesNoSeCambiaElContextoYElNuevoDocenteNoPuedeTenerCruces() {
        oferta();
        Long titular = docente();
        Long ocupado = docente();
        var asignacion = asignaciones.crear(new CrearAsignacionCursoRequest(curso, seccionA, titular));
        horario(asignacion, LocalTime.of(8, 0));
        var otraClase = asignaciones.crear(new CrearAsignacionCursoRequest(nuevoCurso(), seccionB, ocupado));
        horario(otraClase, LocalTime.of(8, 0));
        long version = asignacionRepository.findById(asignacion.id()).orElseThrow().getVersion();

        assertThatThrownBy(() -> asignaciones.actualizar(asignacion.id(),
                        new ActualizarAsignacionCursoRequest(curso, seccionB, titular, version)))
                .isInstanceOfSatisfying(ConflictException.class,
                        e -> assertThat(e.getCode()).isEqualTo("ASIGNACION_CON_HORARIOS"));
        assertThatThrownBy(() -> asignaciones.actualizar(asignacion.id(),
                        new ActualizarAsignacionCursoRequest(curso, seccionA, ocupado, version)))
                .isInstanceOfSatisfying(ConflictException.class,
                        e -> assertThat(e.getCode()).isEqualTo("DOCENTE_CON_CRUCE_DE_HORARIO"));

        Long libre = docente();
        assertThat(asignaciones.actualizar(asignacion.id(),
                new ActualizarAsignacionCursoRequest(curso, seccionA, libre, version)).docenteId())
                .isEqualTo(libre);
    }
}
