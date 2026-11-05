package com.asiscontrol.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.asiscontrol.dto.academico.AcademicoDtos.CrearCursoRequest;
import com.asiscontrol.dto.academico.AcademicoDtos.CrearSeccionRequest;
import com.asiscontrol.dto.academico.ConfiguracionDtos.CrearAnio;
import com.asiscontrol.dto.academico.ConfiguracionDtos.CrearGrado;
import com.asiscontrol.dto.academico.ConfiguracionDtos.CrearPeriodo;
import com.asiscontrol.entity.enums.EstadoAnioAcademico;
import com.asiscontrol.exception.BusinessRuleException;
import com.asiscontrol.exception.ConflictException;
import com.asiscontrol.exception.ResourceNotFoundException;
import com.asiscontrol.repository.GradoAcademicoRepository;
import com.asiscontrol.repository.RegistroAuditoriaRepository;
import com.asiscontrol.repository.planificacion.AnioAcademicoRepository;
import com.asiscontrol.repository.planificacion.DiaNoLectivoRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;

/** Un año creado por error se elimina mientras siga en borrador y nada lo use. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EliminacionAnioIntegrationTest {
    private static final int ANIO = 2087;

    @Autowired ConfiguracionAcademicaService configuracion;
    @Autowired DiaNoLectivoService diasNoLectivos;
    @Autowired SeccionService secciones;
    @Autowired CursoService cursos;
    @Autowired AnioAcademicoRepository anios;
    @Autowired GradoAcademicoRepository grados;
    @Autowired DiaNoLectivoRepository dias;
    @Autowired RegistroAuditoriaRepository auditorias;
    @Autowired WebApplicationContext context;

    private long borrador() {
        configuracion.crear(new CrearAnio(ANIO, LocalDate.of(ANIO, 1, 1), LocalDate.of(ANIO, 12, 31)));
        configuracion.agregarPeriodo(ANIO, new CrearPeriodo(
                "Bimestre I", 1, LocalDate.of(ANIO, 3, 1), LocalDate.of(ANIO, 5, 31)));
        configuracion.agregarGrado(ANIO, new CrearGrado(1, 30));
        diasNoLectivos.crear(ANIO, LocalDate.of(ANIO, 7, 28), "Feriado QA");
        return configuracion.obtener(ANIO).version();
    }

    @Test
    void eliminaElBorradorConSusPeriodosGradosYDiasYPermiteVolverACrearlo() {
        long version = borrador();

        configuracion.eliminar(ANIO, version);

        assertThat(anios.findByAnio(ANIO)).isEmpty();
        assertThat(grados.findByAnioAcademicoAnioOrderByNumero(ANIO)).isEmpty();
        assertThat(dias.findByAnioAcademicoAnioOrderByFechaAsc(ANIO)).isEmpty();
        assertThatThrownBy(() -> configuracion.obtener(ANIO)).isInstanceOf(ResourceNotFoundException.class);
        assertThat(auditorias.findAll()).anySatisfy(registro -> {
            assertThat(registro.getAccion()).isEqualTo("ELIMINAR_ANIO");
            assertThat(registro.getValorAnterior()).contains("anio=" + ANIO, "periodos=1", "grados=1");
        });
        configuracion.crear(new CrearAnio(ANIO, LocalDate.of(ANIO, 1, 1), LocalDate.of(ANIO, 12, 31)));
        assertThat(anios.findByAnio(ANIO)).isPresent();
    }

    @Test
    void rechazaUnaVersionDesactualizadaOUnAnioYaActivado() {
        long version = borrador();
        assertThatThrownBy(() -> configuracion.eliminar(ANIO, version + 5))
                .isInstanceOf(ConflictException.class);

        var anio = anios.findByAnio(ANIO).orElseThrow();
        anio.setEstado(EstadoAnioAcademico.ACTIVO);
        anios.saveAndFlush(anio);
        assertThatThrownBy(() -> configuracion.eliminar(ANIO, anio.getVersion()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("nunca se activó");
    }

    @Test
    void noSeEliminaSiYaTieneSeccionesOCursos() {
        long version = borrador();
        cursos.crear(new CrearCursoRequest("QA-DEL-1", "Curso QA", null, 4,
                grados.findByAnioAcademicoAnioAndNumero(ANIO, 1).orElseThrow().getId()));
        assertThatThrownBy(() -> configuracion.eliminar(ANIO, version))
                .isInstanceOfSatisfying(ConflictException.class,
                        conflict -> assertThat(conflict.getCode()).isEqualTo("ANIO_EN_USO"));
    }

    @Test
    void conSeccionTampocoSeElimina() {
        long version = borrador();
        secciones.crear(new CrearSeccionRequest("A", 1, ANIO, 30, null, null));
        assertThatThrownBy(() -> configuracion.eliminar(ANIO, version))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("secciones");
    }

    @Test
    void soloElAdministradorPuedeEliminar() throws Exception {
        var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        mvc.perform(delete("/api/configuracion-academica/anios/" + ANIO).param("version", "0")
                        .with(user("docente-prueba").roles("DOCENTE")))
                .andExpect(status().isForbidden());
    }
}
