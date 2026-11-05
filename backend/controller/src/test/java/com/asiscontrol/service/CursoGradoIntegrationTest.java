package com.asiscontrol.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.asiscontrol.dto.academico.AcademicoDtos.ActualizarCursoRequest;
import com.asiscontrol.dto.academico.AcademicoDtos.CrearAsignacionCursoRequest;
import com.asiscontrol.dto.academico.AcademicoDtos.CrearCursoRequest;
import com.asiscontrol.dto.academico.AcademicoDtos.CrearSeccionRequest;
import com.asiscontrol.dto.academico.AcademicoDtos.CursoResponse;
import com.asiscontrol.dto.academico.ConfiguracionDtos.CrearAnio;
import com.asiscontrol.dto.academico.ConfiguracionDtos.CrearGrado;
import com.asiscontrol.dto.persona.PersonaDtos;
import com.asiscontrol.entity.enums.Especialidad;
import com.asiscontrol.entity.enums.EstadoAnioAcademico;
import com.asiscontrol.entity.enums.Sexo;
import com.asiscontrol.entity.enums.TipoDocumento;
import com.asiscontrol.exception.BusinessRuleException;
import com.asiscontrol.exception.ConflictException;
import com.asiscontrol.exception.ResourceNotFoundException;
import com.asiscontrol.repository.GradoAcademicoRepository;
import com.asiscontrol.repository.planificacion.AnioAcademicoRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

/** CP-020: el curso se registra con su contexto academico (anio y grado). */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CursoGradoIntegrationTest {
    private static final int ANIO = 2085;

    @Autowired CursoService cursos;
    @Autowired ConfiguracionAcademicaService configuracion;
    @Autowired GradoAcademicoRepository grados;
    @Autowired AnioAcademicoRepository anios;
    @Autowired SeccionService secciones;
    @Autowired PersonaService personas;
    @Autowired AsignacionCursoService asignaciones;

    private Long grado(int numero) {
        return grados.findByAnioAcademicoAnioAndNumero(ANIO, numero).orElseThrow().getId();
    }

    private void anio(int... numeros) {
        configuracion.crear(new CrearAnio(ANIO, LocalDate.of(ANIO, 1, 1), LocalDate.of(ANIO, 12, 31)));
        for (int numero : numeros) configuracion.agregarGrado(ANIO, new CrearGrado(numero, 30));
    }

    private CursoResponse curso(String codigo, Long gradoId) {
        return cursos.crear(new CrearCursoRequest(codigo, "Matematica QA", null, 4, gradoId));
    }

    private void asignar(Long cursoId, int gradoSeccion) {
        String clave = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        var seccion = secciones.crear(new CrearSeccionRequest("A", gradoSeccion, ANIO, 30, null, null));
        var persona = new PersonaDtos.PersonaRequest("Docente", "Prueba", "Curso",
                TipoDocumento.PASAPORTE, clave, null, Sexo.PREFIERE_NO_INDICAR,
                LocalDate.of(1985, 1, 1), null, clave + "@example.test", null);
        var docente = personas.createDocente(new PersonaDtos.DocenteCreateRequest(
                persona, "D" + clave, LocalDate.of(2020, 1, 1), Especialidad.MATEMATICA));
        asignaciones.crear(new CrearAsignacionCursoRequest(cursoId, seccion.id(), docente.id()));
    }

    @Test
    void registraElCursoConSuAnioYGradoYLoFiltra() {
        anio(1, 2);
        var creado = curso("QA-MAT-01", grado(1));

        assertThat(creado.anioAcademico()).isEqualTo(ANIO);
        assertThat(creado.grado()).isEqualTo(1);
        assertThat(cursos.obtener(creado.id()).gradoAcademicoId()).isEqualTo(grado(1));
        assertThat(cursos.listar(null, ANIO, 1, PageRequest.of(0, 20)).getContent())
                .extracting(CursoResponse::id).containsExactly(creado.id());
        assertThat(cursos.listar(null, ANIO, 2, PageRequest.of(0, 20)).getContent()).isEmpty();
    }

    @Test
    void rechazaUnGradoInexistenteOUnAnioCerrado() {
        anio(1);
        assertThatThrownBy(() -> curso("QA-X", Long.MAX_VALUE))
                .isInstanceOf(ResourceNotFoundException.class);

        var anio = anios.findByAnio(ANIO).orElseThrow();
        anio.setEstado(EstadoAnioAcademico.CERRADO);
        anios.saveAndFlush(anio);
        assertThatThrownBy(() -> curso("QA-Y", grado(1)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("cerrado");
    }

    @Test
    void soloSeAsignaASeccionesDeSuGradoYNoCambiaDeGradoSiYaEstaAsignado() {
        anio(1, 2);
        var curso = curso("QA-MAT-02", grado(1));
        assertThatThrownBy(() -> asignar(curso.id(), 2))
                .isInstanceOfSatisfying(ConflictException.class,
                        conflict -> assertThat(conflict.getCode()).isEqualTo("CURSO_DE_OTRO_GRADO"));

        var sinAsignar = curso("QA-MAT-03", grado(1));
        var movido = cursos.actualizar(sinAsignar.id(), new ActualizarCursoRequest(
                "QA-MAT-03", "Matematica QA", null, 4, sinAsignar.version(), grado(2)));
        assertThat(movido.grado()).isEqualTo(2);

        asignar(curso.id(), 1);
        assertThatThrownBy(() -> cursos.actualizar(curso.id(), new ActualizarCursoRequest(
                        "QA-MAT-02", "Matematica QA", null, 4, curso.version(), grado(2))))
                .isInstanceOfSatisfying(ConflictException.class,
                        conflict -> assertThat(conflict.getCode()).isEqualTo("CURSO_CON_ASIGNACIONES"));
    }
}
