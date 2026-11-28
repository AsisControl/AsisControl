package com.asiscontrol.controller;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.util.Arrays;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** Las demás pruebas no ejecutan Flyway: dos migraciones con la misma versión solo fallarían al arrancar con MySQL. */
class VersionesMigracionTest {
    @Test
    void cadaVersionDeMigracionEsUnica() throws Exception {
        var archivos = Arrays.stream(new PathMatchingResourcePatternResolver()
                        .getResources("classpath*:db/migration/V*__*.sql"))
                .map(Resource::getFilename).toList();

        var repetidas = archivos.stream()
                .collect(Collectors.groupingBy(nombre -> nombre.substring(1, nombre.indexOf("__"))))
                .values().stream().filter(grupo -> grupo.size() > 1).toList();

        assertThat(archivos).isNotEmpty();
        assertThat(repetidas).as("migraciones con la misma versión").isEmpty();
    }
}
