package com.asiscontrol.controller;

import com.asiscontrol.entity.*;
import com.asiscontrol.entity.enums.*;
import com.asiscontrol.entity.planificacion.PeriodoAcademico;
import org.hibernate.Session;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/** Ejecuta las migraciones de libretas en H2/MySQL y persiste con los mapeos JPA actuales. */
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:informe_migracion_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class InformeProgresoPersistenciaTest extends ApiIntegrationSupport {
    @Test void migracionCompatibleConJpaConservaResultadosYVersiones() {
        // Sustituye unicamente las tablas nuevas generadas por Hibernate por el SQL entregado.
        em.unwrap(Session.class).doWork(connection -> {
            try (var statement = connection.createStatement()) {
                statement.execute("DROP TABLE DetalleInformeProgreso");
                statement.execute("DROP TABLE InformeProgreso");
                statement.execute("DROP TABLE ReglaCalificacionComponente");
                statement.execute("DROP TABLE ReglaCalificacion");
            }
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/migration/V20__informes_progreso.sql"));
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/migration/V29__reglas_calificacion.sql"));
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/migration/V30__peso_resultado_libreta.sql"));
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/migration/V33__escala_publicacion_libreta.sql"));
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/migration/V35__conclusiones_descriptivas.sql"));
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/migration/V36__revision_informe_progreso.sql"));
        });

        var periodo = new PeriodoAcademico();
        periodo.setAnioAcademico(year); periodo.setNombre("Primer bimestre"); periodo.setOrden(1);
        periodo.setFechaInicio(LocalDate.of(2090, 3, 1)); periodo.setFechaFin(LocalDate.of(2090, 4, 30));
        em.persist(periodo);
        var evaluacion = new Evaluacion(); evaluacion.setAsignacionCurso(assignment);
        evaluacion.setTitulo("Practica"); evaluacion.setFechaEvaluacion(periodo.getFechaInicio());
        evaluacion.setPonderacionPorcentaje(new BigDecimal("30.00"));
        evaluacion.setTipoEvaluacion(TipoEvaluacion.values()[0]);
        evaluacion.setPeriodoEvaluacion(PeriodoEvaluacion.BIMESTRAL);
        evaluacion.setEstado(EstadoEvaluacion.PUBLICADA);
        evaluacion.setCompetencias(Set.of(competence)); em.persist(evaluacion);
        var nota = new Calificacion(); nota.setAlumno(student); nota.setEvaluacion(evaluacion);
        nota.setCompetencia(competence); nota.setNivelLogro(NivelLogro.B);
        nota.setObservacion("Reforzar procedimiento"); nota.setFechaRegistro(Instant.now()); em.persist(nota);

        var primera = informe(periodo, 1);
        primera.setEstado(EstadoInformeProgreso.PUBLICADA); primera.setFechaPublicacion(Instant.now());
        em.persist(primera);
        var detalle = new DetalleInformeProgreso(); detalle.setInforme(primera);
        detalle.setCompetencia(competence); detalle.setEvaluacion(evaluacion); detalle.setCalificacion(nota);
        detalle.setNombreCompetencia(competence.getNombre()); detalle.setTituloEvaluacion(evaluacion.getTitulo());
        detalle.setFechaEvaluacion(evaluacion.getFechaEvaluacion());
        detalle.setPonderacionPorcentaje(evaluacion.getPonderacionPorcentaje());
        detalle.setNivelLogro(nota.getNivelLogro()); detalle.setObservacion(nota.getObservacion());
        em.persist(detalle); em.flush();

        nota.setNivelLogro(NivelLogro.AD); nota.setObservacion("Corregida");
        evaluacion.setTitulo("Titulo actualizado");
        var regla = new ReglaCalificacion();
        regla.setCurso(course);
        regla.setGrado(grade);
        regla.setFormula(ReglaCalificacion.Formula.PROMEDIO_PONDERADO);
        regla.setEstado(ReglaCalificacion.Estado.VIGENTE);
        regla.setNumeroVersion(1);
        regla.getComponentes().put(TipoEvaluacion.EXAMEN,
                new ReglaCalificacion.Componente("Exámenes", new BigDecimal("100.00")));
        em.persist(regla);
        var segunda = informe(periodo, 2);
        segunda.setReglaCalificacion(regla);
        segunda.setConsolidadoRegla("[]");
        em.persist(segunda); em.flush();
        Long idDetalle = detalle.getId(), idPrimera = primera.getId(), idSegunda = segunda.getId();
        em.clear();
        var conservado = em.find(DetalleInformeProgreso.class, idDetalle);
        assertThat(conservado.getNivelLogro()).isEqualTo(NivelLogro.B);
        assertThat(conservado.getObservacion()).isEqualTo("Reforzar procedimiento");
        assertThat(conservado.getTituloEvaluacion()).isEqualTo("Practica");
        assertThat(conservado.getCalificacion().getNivelLogro()).isEqualTo(NivelLogro.AD);
        assertThat(conservado.getFechaCreacion()).isNotNull();
        assertThat(em.find(InformeProgreso.class, idPrimera).getEstado()).isEqualTo(EstadoInformeProgreso.PUBLICADA);
        assertThat(em.find(InformeProgreso.class, idPrimera).getReglaCalificacion()).isNull();
        var conRegla = em.find(InformeProgreso.class, idSegunda);
        assertThat(conRegla.getEstado()).isEqualTo(EstadoInformeProgreso.BORRADOR);
        assertThat(conRegla.getReglaCalificacion().getNumeroVersion()).isEqualTo(1);
        assertThat(conRegla.getReglaCalificacion().getComponentes().get(TipoEvaluacion.EXAMEN).getPeso())
                .isEqualByComparingTo("100.00");
        assertThat(conRegla.getConsolidadoRegla()).isEqualTo("[]");

        // No puede existir otra libreta con la misma clave funcional y numero de version.
        var repetida = informe(em.getReference(PeriodoAcademico.class, periodo.getId()), 1);
        assertThatThrownBy(() -> { em.persist(repetida); em.flush(); })
                .isInstanceOf(jakarta.persistence.PersistenceException.class);
    }

    private InformeProgreso informe(PeriodoAcademico periodo, int numero) {
        var informe = new InformeProgreso(); informe.setAlumno(student);
        informe.setAsignacionCurso(assignment); informe.setPeriodoAcademico(periodo);
        informe.setDocente(teacher); informe.setNumeroVersion(numero);
        return informe;
    }
}
