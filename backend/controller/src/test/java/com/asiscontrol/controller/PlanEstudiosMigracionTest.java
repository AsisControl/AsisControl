package com.asiscontrol.controller;

import org.hibernate.Session;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:plan_estudios_migracion;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PlanEstudiosMigracionTest extends ApiIntegrationSupport {
    @Test void conservaLaAsignacionPreviaDeUnCursoSinGradoEnElCatalogo() throws Exception {
        course.setGradoAcademico(null);
        em.flush();
        em.unwrap(Session.class).doWork(connection -> {
            try (var statement = connection.createStatement()) {
                statement.execute("DROP TABLE PlanEstudiosCurso");
                statement.execute("ALTER TABLE GradoAcademico DROP COLUMN plan_definido");
            }
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/migration/V21__plan_estudios_por_grado.sql"));
        });
        em.clear();

        var plan = getOk("/api/planes-estudios/2090/1");
        assertThat(plan.path("editable").asBoolean()).isFalse();
        assertThat(plan.path("cursos").get(0).path("id").asLong()).isEqualTo(course.getId());
    }
}
