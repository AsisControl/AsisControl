package com.asiscontrol.service;

import com.asiscontrol.entity.Persona;
import com.asiscontrol.repository.AccesoUsuarioRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CredencialesInstitucionalesTest {

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "María José|Quispe|maria.quispe",
                "José|Peña|jose.pena",
                "Ana|D'Ángelo|ana.dangelo",
                "Luis|De la Cruz|luis.delacruz",
                "  Ñuño  Andrés |Núñez-Ibáñez|nuno.nunezibanez",
                "Ana María|O’Brien|ana.obrien",
                "JUAN|PÉREZ|juan.perez"
            })
    void normalizaPrimerNombreYApellidoPaterno(String nombres, String apellido, String esperado) {
        assertThat(CredencialesInstitucionales.baseUsername(nombres, apellido)).isEqualTo(esperado);
    }

    @Test
    void agregaSufijoNumericoAHomonimosIncluyendoAccesosDesactivados() {
        var accesos = mock(AccesoUsuarioRepository.class);
        Set<String> ocupados = Set.of("maria.quispe", "maria.quispe2");
        when(accesos.existsByUsernameIgnoreCase(anyString()))
                .thenAnswer(call -> ocupados.contains(call.<String>getArgument(0).toLowerCase()));
        var credenciales = new CredencialesInstitucionales(accesos, "asiscontrol.edu.pe");

        assertThat(credenciales.username(persona("María", "Quispe"))).isEqualTo("maria.quispe3");
        assertThat(credenciales.username(persona("José", "Peña"))).isEqualTo("jose.pena");
    }

    @Test
    void respetaElMaximoDe80CaracteresConYSinSufijo() {
        String apellido = "a".repeat(120);
        String base = CredencialesInstitucionales.baseUsername("Luis", apellido);
        assertThat(CredencialesInstitucionales.conSufijo(base, 1)).hasSize(80).startsWith("luis.");
        assertThat(CredencialesInstitucionales.conSufijo(base, 12)).hasSize(80).endsWith("12");
    }

    @Test
    void completaElMinimoDeCuatroCaracteresConSufijo() {
        var accesos = mock(AccesoUsuarioRepository.class);
        var credenciales = new CredencialesInstitucionales(accesos, "asiscontrol.edu.pe");
        assertThat(credenciales.username(persona("A", "B"))).isEqualTo("a.b2");
        assertThat(credenciales.username(persona("李", "王"))).isEqualTo("usuario");
    }

    @Test
    void generaCorreoConElCodigoYElDominioConfigurado() {
        var accesos = mock(AccesoUsuarioRepository.class);
        assertThat(new CredencialesInstitucionales(accesos, " AsisControl.edu.pe ").correo("EST20260001"))
                .isEqualTo("est20260001@asiscontrol.edu.pe");
        assertThat(new CredencialesInstitucionales(accesos, "colegio.test").correo("DOC20260003"))
                .isEqualTo("doc20260003@colegio.test");
        assertThatThrownBy(() -> new CredencialesInstitucionales(accesos, "sin-punto"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Persona persona(String nombres, String apellido) {
        var persona = new Persona();
        persona.setNombres(nombres);
        persona.setApellidoPaterno(apellido);
        return persona;
    }
}
