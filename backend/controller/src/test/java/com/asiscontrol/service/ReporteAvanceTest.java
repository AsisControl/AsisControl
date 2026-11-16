package com.asiscontrol.service;

import com.asiscontrol.dto.ReporteAvanceDto;
import com.asiscontrol.entity.*;
import com.asiscontrol.entity.enums.*;
import com.asiscontrol.entity.planificacion.*;
import com.asiscontrol.repository.*;
import com.asiscontrol.repository.planificacion.PeriodoAcademicoRepository;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.nio.file.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class ReporteAvanceTest {
    final AlumnoRepository alumnos = mock(AlumnoRepository.class);
    final AsignacionCursoRepository asignaciones = mock(AsignacionCursoRepository.class);
    final PeriodoAcademicoRepository periodos = mock(PeriodoAcademicoRepository.class);
    final MatriculaRepository matriculas = mock(MatriculaRepository.class);
    final CalificacionRepository calificaciones = mock(CalificacionRepository.class);
    final ModuloAccessGuard acceso = mock(ModuloAccessGuard.class);
    final ReporteAvanceService servicio = new ReporteAvanceService(alumnos, asignaciones, periodos, matriculas, calificaciones, acceso);

    PeriodoAcademico preparar() {
        Persona persona = new Persona(); persona.setNombres("Ana María"); persona.setApellidoPaterno("Pérez");
        Alumno alumno = new Alumno(); alumno.setPersona(persona); alumno.setCodigoAlumno("A001");
        Docente docente = new Docente(); docente.setPersona(persona);
        Curso curso = new Curso(); curso.setNombre("Matemática");
        Seccion seccion = new Seccion(); seccion.setId(4L); seccion.setGrado(2); seccion.setNombre("A"); seccion.setAnioAcademico(2026);
        AsignacionCurso asignacion = new AsignacionCurso(); asignacion.setSeccion(seccion); asignacion.setCurso(curso); asignacion.setDocente(docente);
        AnioAcademico anio = new AnioAcademico(); anio.setAnio(2026);
        PeriodoAcademico periodo = new PeriodoAcademico(); periodo.setAnioAcademico(anio); periodo.setNombre("Primer bimestre");
        periodo.setFechaInicio(LocalDate.of(2026,3,1)); periodo.setFechaFin(LocalDate.of(2026,4,30));
        when(alumnos.findByIdAndActivoTrue(1L)).thenReturn(Optional.of(alumno));
        when(asignaciones.findByIdAndActivoTrue(2L)).thenReturn(Optional.of(asignacion));
        when(periodos.findById(3L)).thenReturn(Optional.of(periodo));
        when(matriculas.existsByAlumnoIdAndSeccionIdAndEstadoAndActivoTrue(1L,4L,EstadoMatricula.ACTIVA)).thenReturn(true);
        return periodo;
    }

    Calificacion nota(long id, LocalDate fecha) {
        Competencia competencia = new Competencia(); competencia.setNombre("Resuelve problemas de cantidad");
        Evaluacion evaluacion = new Evaluacion(); evaluacion.setTitulo("Práctica " + id); evaluacion.setFechaEvaluacion(fecha);
        evaluacion.setPonderacionPorcentaje(new BigDecimal("30.00"));
        Calificacion nota = new Calificacion(); nota.setId(id); nota.setCompetencia(competencia); nota.setEvaluacion(evaluacion); nota.setNivelLogro(NivelLogro.A);
        return nota;
    }

    @Test void filtraPeriodoIncluyendoAmbosLimitesYConservaNiveles() {
        var p = preparar();
        when(calificaciones.buscarParaConsolidado(1L,2L)).thenReturn(List.of(
                nota(1,p.getFechaInicio().minusDays(1)), nota(2,p.getFechaInicio()),
                nota(3,p.getFechaFin()), nota(4,p.getFechaFin().plusDays(1))));
        var dto = servicio.preparar(1L,2L,3L);
        assertThat(dto.filas()).hasSize(2);
        assertThat(dto.filas().getFirst().resultado()).isEqualTo("A");
        assertThat(dto.filas().getFirst().ponderacion()).isEqualTo("30 %");
        assertThat(dto.filas().getFirst().observacion()).isEqualTo("Sin observaciones");
        verify(acceso).verificarAsignacionDocente(any());
    }

    @Test void rechazaAnioDistinto() {
        preparar().getAnioAcademico().setAnio(2025);
        assertThatThrownBy(() -> servicio.preparar(1L,2L,3L)).hasMessageContaining("año del curso");
        verifyNoInteractions(calificaciones);
    }

    @Test void rechazaAlumnoAjeno() {
        preparar();
        when(matriculas.existsByAlumnoIdAndSeccionIdAndEstadoAndActivoTrue(1L,4L,EstadoMatricula.ACTIVA)).thenReturn(false);
        assertThatThrownBy(() -> servicio.preparar(1L,2L,3L)).hasMessageContaining("matricula");
        verifyNoInteractions(calificaciones);
    }

    @Test void rechazaPeriodoSinNotas() {
        preparar(); when(calificaciones.buscarParaConsolidado(1L,2L)).thenReturn(List.of());
        assertThatThrownBy(() -> servicio.preparar(1L,2L,3L)).hasMessageContaining("No hay calificaciones");
    }

    @Test void rechazaDocenteAjenoAntesDeConsultarNotas() {
        preparar(); doThrow(new org.springframework.security.access.AccessDeniedException("Curso ajeno"))
                .when(acceso).verificarAsignacionDocente(any());
        assertThatThrownBy(() -> servicio.preparar(1L,2L,3L)).hasMessageContaining("Curso ajeno");
        verifyNoInteractions(calificaciones);
    }

    @Test void generaPdfConPlantillaRealYTextoLargo() throws Exception {
        var filas = new ArrayList<ReporteAvanceDto.Fila>();
        for (int i=1; i<=35; i++) filas.add(new ReporteAvanceDto.Fila("Resuelve problemas de cantidad",
                "Evaluación " + i, "15/03/2026", "AD", "30 %", i == 1
                ? "Debe reforzar la interpretación y explicar el procedimiento. ".repeat(16) : "Sin observaciones"));
        var dto = new ReporteAvanceDto("Ana María Pérez", "A001", "Matemática", "José Sánchez",
                2,"A",2026,"Primer bimestre","01/03/2026 - 30/04/2026",Instant.parse("2026-04-30T20:00:00Z"),filas);
        byte[] pdf = new GeneradorReporteAvance().generar(dto);
        assertThat(new String(pdf,0,5,java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        assertThat(pdf.length).isGreaterThan(5000);
        Files.createDirectories(Path.of("target", "reportes-prueba"));
        Files.write(Path.of("target", "reportes-prueba", "avance.pdf"), pdf);
    }
}
