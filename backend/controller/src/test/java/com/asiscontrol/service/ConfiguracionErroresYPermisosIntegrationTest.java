package com.asiscontrol.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.asiscontrol.config.BootstrapDataInitializer;
import com.asiscontrol.dto.academico.ConfiguracionDtos.CrearAnio;
import com.asiscontrol.dto.academico.ConfiguracionDtos.CrearPeriodo;
import com.asiscontrol.entity.CategoriaPermiso;
import com.asiscontrol.exception.BusinessRuleException;
import com.asiscontrol.exception.ConflictException;
import com.asiscontrol.repository.CategoriaPermisoRepository;
import com.asiscontrol.repository.PermisoRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.LocalDate;

/** Codigos y textos de error de la configuracion academica, y textos de permisos con tildes. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ConfiguracionErroresYPermisosIntegrationTest {
    private static final int ANIO = 2083;

    @Autowired ConfiguracionAcademicaService configuracion;
    @Autowired BootstrapDataInitializer bootstrap;
    @Autowired CategoriaPermisoRepository categorias;
    @Autowired PermisoRepository permisos;

    private void anio() {
        configuracion.crear(new CrearAnio(ANIO, LocalDate.of(ANIO, 3, 1), LocalDate.of(ANIO, 12, 20)));
    }

    @Test
    void cadaReglaDevuelveSuCodigoYUnTextoLegible() {
        assertThatThrownBy(() -> configuracion.crear(
                        new CrearAnio(ANIO, LocalDate.of(ANIO, 12, 26), LocalDate.of(ANIO, 10, 19))))
                .isInstanceOfSatisfying(BusinessRuleException.class, error -> {
                    assertThat(error.getCode()).isEqualTo("FECHAS_INVALIDAS");
                    assertThat(error.getMessage()).isEqualTo("La fecha final no puede ser anterior a la inicial");
                });

        anio();
        assertThatThrownBy(this::anio)
                .isInstanceOfSatisfying(ConflictException.class,
                        error -> assertThat(error.getCode()).isEqualTo("ANIO_DUPLICADO"));

        assertThatThrownBy(() -> configuracion.agregarPeriodo(ANIO, new CrearPeriodo(
                        "Fuera", 1, LocalDate.of(ANIO, 1, 1), LocalDate.of(ANIO, 4, 30))))
                .isInstanceOfSatisfying(BusinessRuleException.class, error -> {
                    assertThat(error.getCode()).isEqualTo("PERIODO_FUERA_DEL_ANIO");
                    assertThat(error.getMessage()).isEqualTo("Las fechas del período deben estar dentro del año");
                });

        configuracion.agregarPeriodo(ANIO, new CrearPeriodo(
                "Primero", 1, LocalDate.of(ANIO, 3, 1), LocalDate.of(ANIO, 5, 31)));
        assertThatThrownBy(() -> configuracion.agregarPeriodo(ANIO, new CrearPeriodo(
                        "Segundo", 2, LocalDate.of(ANIO, 5, 31), LocalDate.of(ANIO, 7, 31))))
                .isInstanceOfSatisfying(BusinessRuleException.class, error -> {
                    assertThat(error.getCode()).isEqualTo("PERIODOS_SUPERPUESTOS");
                    assertThat(error.getMessage()).startsWith("Las fechas del período se superponen con");
                });
    }

    @Test
    void unaBaseConTextosSinTildesSeCorrigeSinDuplicarCategorias() {
        // Simula una base creada antes de la correccion.
        CategoriaPermiso academico = categoriaAcademica();
        academico.setNombre("Academico");
        categorias.saveAndFlush(academico);
        var permiso = permisos.findByCodigoIgnoreCase("ACADEMICO_LEER").orElseThrow();
        permiso.setDescripcion("Consultar estructura academica");
        permisos.saveAndFlush(permiso);

        bootstrap.run(null);

        assertThat(categorias.findAll().stream()
                .filter(c -> sinTildes(c.getNombre()).equalsIgnoreCase("Academico")))
                .singleElement()
                .satisfies(c -> assertThat(c.getNombre()).isEqualTo("Académico"));
        assertThat(permisos.findByCodigoIgnoreCase("ACADEMICO_LEER").orElseThrow().getDescripcion())
                .isEqualTo("Consultar estructura académica");
        assertThat(permisos.findByCodigoIgnoreCase("ACADEMICO_LEER").orElseThrow().getCategoria().getId())
                .isEqualTo(academico.getId());
    }

    private CategoriaPermiso categoriaAcademica() {
        return categorias.findAll().stream()
                .filter(c -> sinTildes(c.getNombre()).equalsIgnoreCase("Academico"))
                .findFirst().orElseThrow();
    }

    private static String sinTildes(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }
}
