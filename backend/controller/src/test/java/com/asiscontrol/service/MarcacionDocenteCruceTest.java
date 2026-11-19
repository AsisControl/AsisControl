package com.asiscontrol.service;

import com.asiscontrol.dto.asistencia.AsistenciaDtos.AsistenciaDocenteResponse;
import com.asiscontrol.entity.AsignacionCurso;
import com.asiscontrol.entity.AsistenciaDocente;
import com.asiscontrol.entity.CodigoQRAsistencia;
import com.asiscontrol.entity.Docente;
import com.asiscontrol.entity.Persona;
import com.asiscontrol.entity.asistencia.EstadoSesionClase;
import com.asiscontrol.entity.asistencia.SesionClase;
import com.asiscontrol.entity.enums.EstadoCodigoQR;
import com.asiscontrol.entity.enums.EstadoVerificacionClase;
import com.asiscontrol.repository.AsistenciaDocenteRepository;
import com.asiscontrol.repository.CodigoQRAsistenciaRepository;
import com.asiscontrol.repository.DocenteRepository;
import com.asiscontrol.repository.asistencia.SesionClaseRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** ASISCTRL-180: la marcación del docente a las 7:00 p. m. se cruza con la lista de alumnos de su sesión. */
class MarcacionDocenteCruceTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final LocalDate JORNADA = LocalDate.of(2026, 11, 16);

    private final AsistenciaDocenteRepository asistencias = mock(AsistenciaDocenteRepository.class);
    private final CodigoQRAsistenciaRepository codigos = mock(CodigoQRAsistenciaRepository.class);
    private final SesionClaseRepository sesiones = mock(SesionClaseRepository.class);
    private final DocenteRepository docentes = mock(DocenteRepository.class);
    private final ModuloAccessGuard accessGuard = mock(ModuloAccessGuard.class);
    private AsistenciaDocenteService servicio;
    private Docente docente;

    @BeforeEach
    void preparar() {
        Clock sieteDeLaNoche = Clock.fixed(
                JORNADA.atTime(19, 0).atZone(LIMA).toInstant(), LIMA);
        servicio = new AsistenciaDocenteService(
                asistencias, codigos, sesiones, docentes, sieteDeLaNoche,
                mock(EntityManager.class), accessGuard, mock(AuditoriaService.class), 120);
        docente = new Docente();
        docente.setId(7L);
        Persona persona = new Persona();
        persona.setNombres("Patricia");
        persona.setApellidoPaterno("Morales");
        persona.setId(5L);
        docente.setPersona(persona);
        CodigoQRAsistencia codigo = new CodigoQRAsistencia();
        codigo.setEstado(EstadoCodigoQR.VIGENTE);
        codigo.setFechaHoraExpiracion(LocalDateTime.of(2099, 1, 1, 0, 0));
        when(accessGuard.restriccionDocentePersonaId()).thenReturn(5L);
        when(docentes.findByPersonaIdAndActivoTrue(5L)).thenReturn(Optional.of(docente));
        when(codigos.findLockedByTokenHashAndActivoTrue(anyString())).thenReturn(Optional.of(codigo));
        when(asistencias.save(any(AsistenciaDocente.class))).thenAnswer(invocacion -> {
            AsistenciaDocente guardada = invocacion.getArgument(0);
            guardada.setId(1L);
            return guardada;
        });
    }

    private SesionClase sesion(EstadoSesionClase estado, int horaInicio, int horaFin) {
        SesionClase sesion = new SesionClase();
        sesion.setAsignacionCurso(new AsignacionCurso());
        sesion.setFecha(JORNADA);
        sesion.setHoraInicio(LocalTime.of(horaInicio, 0));
        sesion.setHoraFin(LocalTime.of(horaFin, 0));
        sesion.setEstado(estado);
        when(sesiones.findAllByAsignacionCursoDocenteIdAndFechaAndActivoTrueOrderByHoraInicioAsc(
                anyLong(), any(LocalDate.class))).thenReturn(List.of(sesion));
        return sesion;
    }

    @Test
    void claseDeLasSieteConListaDeAlumnosQuedaConfirmada() {
        sesion(EstadoSesionClase.TOMADA, 19, 21);

        AsistenciaDocenteResponse respuesta = servicio.marcarConToken("token");

        assertThat(respuesta.verificacion()).isEqualTo(EstadoVerificacionClase.CONFIRMADA);
        assertThat(respuesta.docenteNombre()).isEqualTo("Patricia Morales");
    }

    @Test
    void claseDeLasSieteSinListaDeAlumnosQuedaObservada() {
        sesion(EstadoSesionClase.PENDIENTE, 19, 21);

        AsistenciaDocenteResponse respuesta = servicio.marcarConToken("token");

        assertThat(respuesta.verificacion()).isEqualTo(EstadoVerificacionClase.OBSERVADA);
    }

    @Test
    void unBorradorSinFinalizarTampocoConfirmaLaMarcacion() {
        sesion(EstadoSesionClase.BORRADOR, 19, 21);

        AsistenciaDocenteResponse respuesta = servicio.marcarConToken("token");

        assertThat(respuesta.verificacion()).isEqualTo(EstadoVerificacionClase.OBSERVADA);
    }

    @Test
    void soloUnDocenteMarcaConElCodigoDeLaInstitucion() {
        when(accessGuard.restriccionDocentePersonaId()).thenReturn(null);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> servicio.marcarConToken("token"))
                .hasMessageContaining("Solo un docente");
    }

    @Test
    void sinClaseProgramadaNoHayNadaQueCruzar() {
        when(sesiones.findAllByAsignacionCursoDocenteIdAndFechaAndActivoTrueOrderByHoraInicioAsc(
                anyLong(), any(LocalDate.class))).thenReturn(List.of());

        AsistenciaDocenteResponse respuesta = servicio.marcarConToken("token");

        assertThat(respuesta.verificacion()).isEqualTo(EstadoVerificacionClase.CONFIRMADA);
        assertThat(respuesta.sesionClaseId()).isNull();
    }
}
