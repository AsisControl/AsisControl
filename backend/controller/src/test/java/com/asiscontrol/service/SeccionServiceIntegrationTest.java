package com.asiscontrol.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.asiscontrol.dto.academico.AcademicoDtos.ActualizarSeccionRequest;
import com.asiscontrol.dto.academico.AcademicoDtos.ConfirmarPropuestasSeccionesRequest;
import com.asiscontrol.dto.academico.AcademicoDtos.CrearAsignacionCursoRequest;
import com.asiscontrol.dto.academico.AcademicoDtos.CrearCursoRequest;
import com.asiscontrol.dto.academico.AcademicoDtos.CrearSeccionRequest;
import com.asiscontrol.dto.academico.AcademicoDtos.GenerarPropuestasSeccionesRequest;
import com.asiscontrol.dto.academico.AcademicoDtos.SeccionPropuestaRequest;
import com.asiscontrol.dto.academico.AcademicoDtos.SeccionPropuestaResponse;
import com.asiscontrol.dto.academico.AcademicoDtos.SeccionResponse;
import com.asiscontrol.dto.academico.ConfiguracionDtos.CrearAnio;
import com.asiscontrol.dto.academico.ConfiguracionDtos.CrearGrado;
import com.asiscontrol.dto.academico.ConfiguracionDtos.CrearPeriodo;
import com.asiscontrol.dto.academico.ConfiguracionDtos.EstadoAnio;
import com.asiscontrol.dto.academico.ConfiguracionDtos.EstadoSeccion;
import com.asiscontrol.dto.persona.PersonaDtos;
import com.asiscontrol.entity.enums.Especialidad;
import com.asiscontrol.entity.enums.EstadoAnioAcademico;
import com.asiscontrol.entity.enums.Sexo;
import com.asiscontrol.entity.enums.TipoDocumento;
import com.asiscontrol.exception.BusinessRuleException;
import com.asiscontrol.exception.ConflictException;
import com.asiscontrol.exception.ResourceNotFoundException;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Gestion de secciones: alta, edicion, propuestas, estado y eliminacion. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SeccionServiceIntegrationTest {
    private static final int ANIO = 2081;

    @Autowired SeccionService secciones;
    @Autowired ConfiguracionAcademicaService configuracion;
    @Autowired CursoService cursos;
    @Autowired PersonaService personas;
    @Autowired AsignacionCursoService asignaciones;

    private void anio(int... grados) {
        configuracion.crear(new CrearAnio(ANIO, LocalDate.of(ANIO, 1, 1), LocalDate.of(ANIO, 12, 31)));
        for (int grado : grados) configuracion.agregarGrado(ANIO, new CrearGrado(grado, 30));
    }

    private void activarAnio() {
        configuracion.agregarPeriodo(ANIO, new CrearPeriodo(
                "Bimestre 1", 1, LocalDate.of(ANIO, 3, 1), LocalDate.of(ANIO, 5, 31)));
        var actual = configuracion.obtener(ANIO);
        configuracion.estado(ANIO, new EstadoAnio(EstadoAnioAcademico.ACTIVO, actual.version(), "Inicio"));
    }

    private SeccionResponse seccion(String nombre) {
        return secciones.crear(new CrearSeccionRequest(nombre, 1, ANIO, 30, null, null));
    }

    private void asignarCurso(Long seccionId) {
        String clave = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        var curso = cursos.crear(new CrearCursoRequest("C" + clave, "Curso " + clave, null, 4));
        var persona = new PersonaDtos.PersonaRequest("Docente", "Prueba", "Seccion",
                TipoDocumento.PASAPORTE, clave, null, Sexo.PREFIERE_NO_INDICAR,
                LocalDate.of(1985, 1, 1), null, clave + "@example.test", null);
        var docente = personas.createDocente(new PersonaDtos.DocenteCreateRequest(
                persona, "D" + clave, LocalDate.of(2020, 1, 1), Especialidad.MATEMATICA));
        asignaciones.crear(new CrearAsignacionCursoRequest(curso.id(), seccionId, docente.id()));
    }

    @Test
    void creaListaObtieneYActualizaUnaSeccion() {
        anio(1);
        SeccionResponse creada = seccion(" a ");
        assertThat(creada.nombre()).isEqualTo("A");

        assertThat(secciones.listar(null, ANIO, 1, PageRequest.of(0, 20)).getContent())
                .extracting(SeccionResponse::id).contains(creada.id());
        assertThat(secciones.obtener(creada.id()).capacidadMaxima()).isEqualTo(30);

        SeccionResponse actualizada = secciones.actualizar(creada.id(), new ActualizarSeccionRequest(
                "A", 1, ANIO, 25, null, creada.version(), "Ajuste de aforo"));
        assertThat(actualizada.capacidadMaxima()).isEqualTo(25);
    }

    @Test
    void rechazaGradoNoHabilitadoDuplicadosYCambiosDeIdentidad() {
        anio(1, 2);
        assertThatThrownBy(() -> secciones.crear(new CrearSeccionRequest("A", 3, ANIO, 30, null, null)))
                .isInstanceOf(BusinessRuleException.class);

        SeccionResponse creada = seccion("A");
        assertThatThrownBy(() -> seccion("a"))
                .isInstanceOf(ConflictException.class).hasMessageContaining("Ya existe");
        assertThatThrownBy(() -> secciones.actualizar(creada.id(), new ActualizarSeccionRequest(
                        "A", 2, ANIO, 30, null, creada.version(), "Cambio")))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("fijos");
        assertThatThrownBy(() -> secciones.actualizar(creada.id(), new ActualizarSeccionRequest(
                        "A", 1, ANIO, 30, null, creada.version() + 9, "Cambio")))
                .isInstanceOf(ConflictException.class).hasMessageContaining("modificada");
    }

    @Test
    void enUnAnioActivoAgregarSeccionesExigeMotivo() {
        anio(1);
        activarAnio();
        assertThatThrownBy(() -> seccion("A"))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("motivo");
        assertThatThrownBy(() -> secciones.confirmarPropuestas(new ConfirmarPropuestasSeccionesRequest(
                        ANIO, 1, List.of(new SeccionPropuestaRequest("B", 30)), " ")))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("motivo");

        assertThat(secciones.crear(new CrearSeccionRequest("A", 1, ANIO, 30, null, "Nueva aula"))
                .nombre()).isEqualTo("A");
    }

    @Test
    void generaPropuestasSaltandoNombresExistentesYMasAllaDeLaZ() {
        anio(1);
        seccion("A");

        List<SeccionPropuestaResponse> propuestas = secciones.generarPropuestas(
                new GenerarPropuestasSeccionesRequest(ANIO, 1, 28, 30));

        assertThat(propuestas).hasSize(28);
        assertThat(propuestas.getFirst().nombre()).isEqualTo("B");
        assertThat(propuestas).extracting(SeccionPropuestaResponse::nombre)
                .doesNotContain("A").contains("Z", "AA", "AB");
    }

    @Test
    void validaLosDatosDeUnaPropuesta() {
        assertThatThrownBy(() -> secciones.generarPropuestas(new GenerarPropuestasSeccionesRequest(1999, 1, 1, 30)))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> secciones.generarPropuestas(new GenerarPropuestasSeccionesRequest(ANIO, 6, 1, 30)))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> secciones.generarPropuestas(new GenerarPropuestasSeccionesRequest(ANIO, 1, 0, 30)))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> secciones.generarPropuestas(new GenerarPropuestasSeccionesRequest(ANIO, 1, 1, 101)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void confirmaPropuestasYRechazaDuplicadosODatosInvalidos() {
        anio(1);
        seccion("A");

        List<SeccionResponse> creadas = secciones.confirmarPropuestas(new ConfirmarPropuestasSeccionesRequest(
                ANIO, 1, List.of(new SeccionPropuestaRequest("b", 30), new SeccionPropuestaRequest("C", 28)), null));
        assertThat(creadas).extracting(SeccionResponse::nombre).containsExactly("B", "C");

        assertThatThrownBy(() -> secciones.confirmarPropuestas(new ConfirmarPropuestasSeccionesRequest(
                        ANIO, 1, List.of(new SeccionPropuestaRequest("D", 30), new SeccionPropuestaRequest("d", 30)), null)))
                .isInstanceOf(ConflictException.class).hasMessageContaining("duplicados");
        assertThatThrownBy(() -> secciones.confirmarPropuestas(new ConfirmarPropuestasSeccionesRequest(
                        ANIO, 1, List.of(new SeccionPropuestaRequest("a", 30)), null)))
                .isInstanceOf(ConflictException.class).hasMessageContaining("Ya existe");
        assertThatThrownBy(() -> secciones.confirmarPropuestas(new ConfirmarPropuestasSeccionesRequest(
                        ANIO, 1, List.of(), null)))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> secciones.confirmarPropuestas(new ConfirmarPropuestasSeccionesRequest(
                        ANIO, 1, List.of(new SeccionPropuestaRequest(" ", 30)), null)))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> secciones.confirmarPropuestas(new ConfirmarPropuestasSeccionesRequest(
                        ANIO, 1, List.of(new SeccionPropuestaRequest("E", 0)), null)))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> secciones.confirmarPropuestas(new ConfirmarPropuestasSeccionesRequest(
                        1999, 1, List.of(new SeccionPropuestaRequest("E", 30)), null)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void inhabilitaYHabilitaConMotivo() {
        anio(1);
        SeccionResponse creada = seccion("A");

        assertThatThrownBy(() -> secciones.cambiarEstado(creada.id(), new EstadoSeccion(false, creada.version(), " ")))
                .isInstanceOf(BusinessRuleException.class);
        SeccionResponse inactiva = secciones.cambiarEstado(
                creada.id(), new EstadoSeccion(false, creada.version(), "Sin demanda"));
        assertThat(inactiva.activo()).isFalse();
        assertThat(secciones.listar(false, ANIO, 1, PageRequest.of(0, 20)).getContent())
                .extracting(SeccionResponse::id).contains(creada.id());

        SeccionResponse activa = secciones.cambiarEstado(
                creada.id(), new EstadoSeccion(true, inactiva.version(), "Reapertura"));
        assertThat(activa.activo()).isTrue();
    }

    @Test
    void eliminaSoloSeccionesSinUsoDeAniosEnBorrador() {
        anio(1);
        SeccionResponse libre = seccion("A");
        secciones.eliminar(libre.id());
        assertThatThrownBy(() -> secciones.obtener(libre.id())).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> secciones.eliminar(libre.id())).isInstanceOf(ResourceNotFoundException.class);

        SeccionResponse otra = seccion("B");
        activarAnio();
        assertThatThrownBy(() -> secciones.eliminar(otra.id()))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("borrador");
    }

    @Test
    void unaSeccionUtilizadaNoCambiaDeNombreNiSeInhabilitaNiSeElimina() {
        anio(1);
        SeccionResponse creada = seccion("A");
        asignarCurso(creada.id());

        assertThatThrownBy(() -> secciones.actualizar(creada.id(), new ActualizarSeccionRequest(
                        "Z", 1, ANIO, 30, null, creada.version(), "Renombrar")))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("utilizada");
        assertThatThrownBy(() -> secciones.cambiarEstado(creada.id(), new EstadoSeccion(false, creada.version(), "Cerrar")))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> secciones.eliminar(creada.id()))
                .isInstanceOf(ConflictException.class).hasMessageContaining("relaciones");
    }

    @Test
    void rechazaFiltrosFueraDeRango() {
        assertThatThrownBy(() -> secciones.listar(null, 1999, null, PageRequest.of(0, 20)))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> secciones.listar(null, null, 9, PageRequest.of(0, 20)))
                .isInstanceOf(BusinessRuleException.class);
    }
}
