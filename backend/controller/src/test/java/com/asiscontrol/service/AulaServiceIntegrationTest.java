package com.asiscontrol.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.asiscontrol.dto.academico.AcademicoDtos.ActualizarAulaRequest;
import com.asiscontrol.dto.academico.AcademicoDtos.AulaResponse;
import com.asiscontrol.dto.academico.AcademicoDtos.CrearAsignacionCursoRequest;
import com.asiscontrol.dto.academico.AcademicoDtos.CrearAulaRequest;
import com.asiscontrol.dto.academico.AcademicoDtos.CrearCursoRequest;
import com.asiscontrol.dto.academico.AcademicoDtos.CrearHorarioRequest;
import com.asiscontrol.dto.academico.AcademicoDtos.CrearSeccionRequest;
import com.asiscontrol.dto.academico.AcademicoDtos.EstadoAula;
import com.asiscontrol.dto.academico.AcademicoDtos.HabilitarAulaRequest;
import com.asiscontrol.dto.academico.AcademicoDtos.HorarioEnUsoAula;
import com.asiscontrol.dto.academico.AcademicoDtos.InhabilitarAulaRequest;
import com.asiscontrol.dto.academico.ConfiguracionDtos.CrearAnio;
import com.asiscontrol.dto.academico.ConfiguracionDtos.CrearGrado;
import com.asiscontrol.dto.persona.PersonaDtos;
import com.asiscontrol.entity.RegistroAuditoria;
import com.asiscontrol.entity.enums.DiaSemana;
import com.asiscontrol.entity.enums.Especialidad;
import com.asiscontrol.entity.enums.Sexo;
import com.asiscontrol.entity.enums.TipoAula;
import com.asiscontrol.entity.enums.TipoDocumento;
import com.asiscontrol.exception.BusinessRuleException;
import com.asiscontrol.exception.ConflictException;
import com.asiscontrol.exception.ResourceNotFoundException;
import com.asiscontrol.repository.RegistroAuditoriaRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/** Gestion de aulas: alta, edicion, estado, eliminacion y reglas con horarios. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AulaServiceIntegrationTest {
    private static final int ANIO = 2082;

    @Autowired AulaService aulas;
    @Autowired ConfiguracionAcademicaService configuracion;
    @Autowired SeccionService secciones;
    @Autowired CursoService cursos;
    @Autowired PersonaService personas;
    @Autowired AsignacionCursoService asignaciones;
    @Autowired HorarioService horarios;
    @Autowired RegistroAuditoriaRepository auditorias;

    private static String clave() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
    }

    private AulaResponse aula(String codigo, int capacidad) {
        return aulas.crear(new CrearAulaRequest(codigo, "Aula " + codigo, TipoAula.AULA_REGULAR, capacidad, null));
    }

    /** Aula con un horario activo de una seccion de 30 alumnos. */
    private AulaResponse aulaConHorario() {
        configuracion.crear(new CrearAnio(ANIO, LocalDate.of(ANIO, 1, 1), LocalDate.of(ANIO, 12, 31)));
        configuracion.agregarGrado(ANIO, new CrearGrado(1, 30));
        var seccion = secciones.crear(new CrearSeccionRequest("A", 1, ANIO, 30, null, null));
        String clave = clave();
        var curso = cursos.crear(new CrearCursoRequest("C" + clave, "Curso " + clave, null, 4));
        var persona = new PersonaDtos.PersonaRequest("Docente", "Prueba", "Aula",
                TipoDocumento.PASAPORTE, clave, null, Sexo.PREFIERE_NO_INDICAR,
                LocalDate.of(1985, 1, 1), null, clave.toLowerCase() + "@example.test", null);
        var docente = personas.createDocente(new PersonaDtos.DocenteCreateRequest(
                persona, "D" + clave, LocalDate.of(2020, 1, 1), Especialidad.MATEMATICA));
        var asignacion = asignaciones.crear(new CrearAsignacionCursoRequest(curso.id(), seccion.id(), docente.id()));
        AulaResponse aula = aula("A" + clave, 35);
        horarios.crear(new CrearHorarioRequest(
                asignacion.id(), aula.id(), DiaSemana.LUNES, LocalTime.of(8, 0), LocalTime.of(9, 0)));
        return aula;
    }

    @Test
    void creaNormalizandoCodigoYUbicacion() {
        String clave = clave();
        AulaResponse creada = aulas.crear(new CrearAulaRequest(
                "  lab   " + clave + " ", " Laboratorio ", TipoAula.LABORATORIO_COMPUTO, 25, "  "));

        assertThat(creada.codigo()).isEqualTo("LAB " + clave);
        assertThat(creada.nombre()).isEqualTo("Laboratorio");
        assertThat(creada.ubicacion()).isEmpty();
        assertThat(creada.activo()).isTrue();
        assertThat(AulaService.esSuficiente(30, 30)).isTrue();
        assertThat(AulaService.esSuficiente(29, 30)).isFalse();
    }

    @Test
    void rechazaCodigoDuplicadoYCapacidadInvalida() {
        String codigo = "A" + clave();
        aula(codigo, 30);
        assertThatThrownBy(() -> aula(codigo.toLowerCase(), 30))
                .isInstanceOf(ConflictException.class).hasMessageContaining("codigo");
        assertThatThrownBy(() -> aula("B" + clave(), 0))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void actualizaYAuditaSoloLoQueCambia() {
        AulaResponse creada = aula("A" + clave(), 30);
        AulaResponse actualizada = aulas.actualizar(creada.id(), new ActualizarAulaRequest(
                creada.codigo(), "Aula renovada", TipoAula.AULA_REGULAR, 40, "Piso 2", creada.version()));

        assertThat(actualizada.capacidad()).isEqualTo(40);
        assertThat(auditorias.findByEntidadAndRegistroIdOrderByFechaHoraAsc("Aula", creada.id().toString()))
                .extracting(RegistroAuditoria::getAccion)
                .containsExactly("CREAR_AULA", "ACTUALIZAR_AULA", "CAMBIAR_CAPACIDAD_AULA");
        assertThatThrownBy(() -> aulas.actualizar(creada.id(), new ActualizarAulaRequest(
                        creada.codigo(), "Otra", TipoAula.AULA_REGULAR, 40, null, creada.version())))
                .isInstanceOf(ConflictException.class).hasMessageContaining("modificada");
    }

    @Test
    void inhabilitaYHabilitaConsultandoPorEstado() {
        AulaResponse creada = aula("A" + clave(), 30);
        long habilitadasAntes = aulas.resumen().habilitadas();

        assertThatThrownBy(() -> aulas.inhabilitar(creada.id(), new InhabilitarAulaRequest(" ", creada.version())))
                .isInstanceOf(BusinessRuleException.class);
        AulaResponse inactiva = aulas.inhabilitar(creada.id(), new InhabilitarAulaRequest("Mantenimiento", creada.version()));
        assertThat(inactiva.activo()).isFalse();
        assertThat(aulas.resumen().habilitadas()).isEqualTo(habilitadasAntes - 1);
        assertThat(aulas.obtener(creada.id()).activo()).isFalse();
        assertThat(aulas.listar(EstadoAula.INHABILITADAS, PageRequest.of(0, 100)).getContent())
                .extracting(AulaResponse::id).contains(creada.id());
        assertThat(aulas.listar(EstadoAula.TODAS, PageRequest.of(0, 1000)).getContent())
                .extracting(AulaResponse::id).contains(creada.id());
        assertThatThrownBy(() -> aulas.inhabilitar(creada.id(), new InhabilitarAulaRequest("Otra vez", inactiva.version())))
                .isInstanceOf(ConflictException.class);

        AulaResponse activa = aulas.habilitar(creada.id(), new HabilitarAulaRequest(" Reparada ", inactiva.version()));
        assertThat(activa.activo()).isTrue();
        assertThat(aulas.listar(PageRequest.of(0, 1000)).getContent())
                .extracting(AulaResponse::id).contains(creada.id());
        assertThatThrownBy(() -> aulas.habilitar(creada.id(), new HabilitarAulaRequest(null, activa.version())))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void eliminaUnAulaSinHorarios() {
        AulaResponse creada = aula("A" + clave(), 30);
        aulas.eliminar(creada.id());

        assertThatThrownBy(() -> aulas.obtener(creada.id())).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> aulas.obtenerEntidad(creada.id())).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void unAulaConHorariosProtegeSuCodigoCapacidadEstadoYExistencia() {
        AulaResponse aula = aulaConHorario();

        assertThat(aulas.horarios(aula.id())).hasSize(1);
        assertThatThrownBy(() -> aulas.actualizar(aula.id(), new ActualizarAulaRequest(
                        "OTRO" + clave(), aula.nombre(), TipoAula.AULA_REGULAR, 35, null, aula.version())))
                .isInstanceOf(ConflictException.class).hasMessageContaining("codigo");

        assertThatThrownBy(() -> aulas.actualizar(aula.id(), new ActualizarAulaRequest(
                        aula.codigo(), aula.nombre(), TipoAula.AULA_REGULAR, 20, null, aula.version())))
                .isInstanceOfSatisfying(ConflictException.class, error ->
                        assertThat(error.getDetails()).singleElement()
                                .isInstanceOfSatisfying(HorarioEnUsoAula.class, uso ->
                                        assertThat(uso.capacidadMaximaSeccion()).isEqualTo(30)));

        assertThatThrownBy(() -> aulas.inhabilitar(aula.id(), new InhabilitarAulaRequest("Cierre", aula.version())))
                .isInstanceOf(ConflictException.class).hasMessageContaining("horarios");
        assertThatThrownBy(() -> aulas.eliminar(aula.id()))
                .isInstanceOf(ConflictException.class).hasMessageContaining("inhabilitela");

        // Reducir sin quedar por debajo de la seccion si se permite.
        assertThat(aulas.actualizar(aula.id(), new ActualizarAulaRequest(
                aula.codigo(), aula.nombre(), TipoAula.AULA_REGULAR, 30, null, aula.version())).capacidad())
                .isEqualTo(30);
    }
}
