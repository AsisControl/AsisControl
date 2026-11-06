package com.asiscontrol.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.asiscontrol.config.DemoDataInitializer;
import com.asiscontrol.entity.AccesoUsuario;
import com.asiscontrol.entity.AsignacionCurso;
import com.asiscontrol.entity.Horario;
import com.asiscontrol.entity.Rol;
import com.asiscontrol.entity.enums.EstadoAnioAcademico;
import com.asiscontrol.entity.enums.EstadoAcceso;
import com.asiscontrol.entity.enums.EstadoPeriodoAcademico;
import com.asiscontrol.entity.Nacionalidad;
import com.asiscontrol.entity.planificacion.AnioAcademico;
import com.asiscontrol.exception.BusinessRuleException;
import com.asiscontrol.entity.planificacion.PeriodoAcademico;
import com.asiscontrol.repository.AccesoUsuarioRepository;
import com.asiscontrol.repository.AsignacionCursoRepository;
import com.asiscontrol.repository.HorarioRepository;
import com.asiscontrol.repository.MatriculaRepository;
import com.asiscontrol.repository.NacionalidadRepository;
import com.asiscontrol.repository.RolRepository;
import com.asiscontrol.repository.asistencia.SesionClaseRepository;
import com.asiscontrol.repository.planificacion.AnioAcademicoRepository;
import com.asiscontrol.repository.planificacion.PeriodoAcademicoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * El inicializador se crea a mano dentro de la transaccion de cada prueba: con el perfil demo
 * activo los datos quedarian confirmados en la base que comparten las demas pruebas.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "app.demo.password=" + DemoDataInitializerIntegrationTest.CONTRASENA_DE_PRUEBA)
@Transactional
class DemoDataInitializerIntegrationTest {
    static final String CONTRASENA_DE_PRUEBA = "SoloPruebas1";

    @Autowired ApplicationContext contexto;
    @Autowired AutowireCapableBeanFactory fabrica;
    @Autowired AnioAcademicoRepository anios;
    @Autowired PeriodoAcademicoRepository periodos;
    @Autowired AsignacionCursoRepository asignaciones;
    @Autowired HorarioRepository horarios;
    @Autowired MatriculaRepository matriculas;
    @Autowired AccesoUsuarioRepository usuarios;
    @Autowired SesionClaseRepository sesiones;
    @Autowired RolRepository roles;
    @Autowired NacionalidadRepository nacionalidades;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired Clock reloj;

    private DemoDataInitializer inicializador;

    @BeforeEach
    void preparar() {
        for (String nombre : List.of("DOCENTE", "ALUMNO", "APODERADO")) {
            if (roles.findByNombreIgnoreCase(nombre).isEmpty()) {
                Rol rol = new Rol();
                rol.setNombre(nombre);
                roles.save(rol);
            }
        }
        if (nacionalidades.findByNombreIgnoreCase("Peruana").isEmpty()) {
            Nacionalidad peruana = new Nacionalidad();
            peruana.setNombre("Peruana");
            nacionalidades.save(peruana);
        }
        inicializador = fabrica.createBean(DemoDataInitializer.class);
    }

    @Test
    void sinElPerfilDemoNoExisteElInicializador() {
        assertThat(contexto.getBeansOfType(DemoDataInitializer.class)).isEmpty();
    }

    @Test
    void cargaUnAnioActivoConCursosDocentesYEstudiantes() {
        inicializador.cargar();

        LocalDate hoy = LocalDate.now(reloj);
        AnioAcademico anio = anios.findByAnio(hoy.getYear()).orElseThrow();
        assertThat(anio.getEstado()).isEqualTo(EstadoAnioAcademico.ACTIVO);
        assertThat(anio.getClaveActiva()).isEqualTo(1);

        List<PeriodoAcademico> delAnio = periodos.findAll().stream()
                .filter(periodo -> periodo.getAnioAcademico().getId().equals(anio.getId()))
                .toList();
        assertThat(delAnio).hasSize(2);
        assertThat(delAnio).filteredOn(p -> p.getEstado() == EstadoPeriodoAcademico.ACTIVO)
                .singleElement()
                .satisfies(activo -> {
                    assertThat(activo.getFechaInicio()).isBeforeOrEqualTo(hoy);
                    assertThat(activo.getFechaFin()).isAfterOrEqualTo(hoy);
                });

        List<AsignacionCurso> delDemo = asignacionesDemo();
        assertThat(delDemo).hasSize(4);
        assertThat(delDemo).extracting(a -> a.getDocente().getCodigoDocente())
                .containsOnly("DOC-DEMO-1", "DOC-DEMO-2");
        assertThat(matriculasDemo()).isEqualTo(12);
    }

    /** Las mismas reglas que HorarioService exige al crear un bloque a mano. */
    @Test
    void elHorarioNoTieneCrucesDeDocenteAulaNiSeccion() {
        inicializador.cargar();

        List<Horario> bloques = horarios.findAll().stream()
                .filter(bloque -> esDelAnioActual(bloque.getAsignacionCurso()))
                .toList();
        assertThat(bloques).hasSize(20);
        Set<String> ocupados = new HashSet<>();
        for (Horario bloque : bloques) {
            String momento = bloque.getDiaSemana() + "-" + bloque.getHoraInicio();
            AsignacionCurso asignacion = bloque.getAsignacionCurso();
            assertThat(ocupados.add("docente" + asignacion.getDocente().getId() + momento)).isTrue();
            assertThat(ocupados.add("aula" + bloque.getAula().getId() + momento)).isTrue();
            assertThat(ocupados.add("seccion" + asignacion.getSeccion().getId() + momento)).isTrue();
            assertThat(bloque.getHoraInicio().getMinute() % 30).isZero();
            assertThat(bloque.getHoraFin().getMinute() % 30).isZero();
        }
    }

    @Test
    void generaLasSesionesDeAsistenciaDesdeElHorario() {
        inicializador.cargar();

        LocalDate hoy = LocalDate.now(reloj);
        for (AsignacionCurso asignacion : asignacionesDemo()) {
            var futuras = sesiones.findAllByAsignacionCursoIdAndFechaGreaterThanEqual(asignacion.getId(), hoy);
            if (hoy.getMonthValue() < 12 || hoy.getDayOfMonth() < 25) {
                assertThat(futuras).isNotEmpty();
            }
            assertThat(futuras).allSatisfy(sesion -> assertThat(sesion.isActivo()).isTrue());
        }
    }

    @Test
    void creaAccesosListosParaIngresar() {
        inicializador.cargar();

        for (String usuario : List.of("demo.docente1", "demo.docente2", "demo.alumno", "demo.apoderado")) {
            AccesoUsuario acceso = usuarios.findActiveByIdentifier(usuario).orElseThrow();
            assertThat(acceso.getEstado()).isEqualTo(EstadoAcceso.ACTIVO);
            assertThat(acceso.isRequiereCambioContrasena()).isFalse();
            assertThat(passwordEncoder.matches(CONTRASENA_DE_PRUEBA, acceso.getContrasenaHash())).isTrue();
        }
    }

    @Test
    void noEscribeNadaSiYaHayDatos() {
        inicializador.cargar();
        long antes = matriculasDemo();

        fabrica.createBean(DemoDataInitializer.class).cargar();

        assertThat(matriculasDemo()).isEqualTo(antes);
        assertThat(asignacionesDemo()).hasSize(4);
    }

    @Test
    void exigeUnaContrasenaValidaAntesDeEscribir() {
        assertThatThrownBy(() -> conContrasena(" ").cargar())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DEMO_PASSWORD");
        assertThatThrownBy(() -> conContrasena("debil").cargar())
                .isInstanceOf(BusinessRuleException.class);
        assertThat(anios.findByAnio(LocalDate.now(reloj).getYear())).isEmpty();
    }

    /** Solo usa el repositorio de años y el reloj: llega a la validacion sin tocar nada mas. */
    private DemoDataInitializer conContrasena(String contrasena) {
        return new DemoDataInitializer(anios, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, reloj, contrasena);
    }

    private List<AsignacionCurso> asignacionesDemo() {
        return asignaciones.findAll().stream().filter(this::esDelAnioActual).toList();
    }

    private long matriculasDemo() {
        int anio = LocalDate.now(reloj).getYear();
        return matriculas.findAll().stream().filter(m -> m.getAnioAcademico() == anio).count();
    }

    private boolean esDelAnioActual(AsignacionCurso asignacion) {
        return asignacion.getSeccion().getAnioAcademico() == LocalDate.now(reloj).getYear();
    }
}
