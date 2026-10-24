package com.asiscontrol.service;

import static org.assertj.core.api.Assertions.*;

import com.asiscontrol.dto.academico.AcademicoDtos;
import com.asiscontrol.dto.academico.ConfiguracionDtos.*;
import com.asiscontrol.dto.auditoria.AuditoriaDtos;
import com.asiscontrol.dto.matricula.MatriculaDtos.*;
import com.asiscontrol.dto.persona.PersonaDtos;
import com.asiscontrol.entity.RegistroError;
import com.asiscontrol.entity.enums.*;
import com.asiscontrol.exception.BusinessRuleException;
import com.asiscontrol.exception.ConflictException;
import com.asiscontrol.repository.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AdministracionMatriculaIntegrationTest {
    @Autowired ConfiguracionAcademicaService configuracion;
    @Autowired SeccionService secciones;
    @Autowired PersonaService personas;
    @Autowired SolicitudMatriculaService solicitudes;
    @Autowired MatriculaService matriculas;
    @Autowired AuditoriaService auditoria;
    @Autowired UsuarioService usuarios;
    @Autowired RegistroErrorRepository errores;
    @Autowired RolRepository roles;

    private void oferta(int capacidad) {
        configuracion.crear(
                new CrearAnio(2060, LocalDate.of(2060, 1, 1), LocalDate.of(2060, 12, 31)));
        configuracion.agregarPeriodo(
                2060,
                new CrearPeriodo(
                        "Bimestre 1", 1, LocalDate.of(2060, 3, 1), LocalDate.of(2060, 5, 31)));
        configuracion.agregarGrado(2060, new CrearGrado(1, capacidad));
        secciones.crear(new AcademicoDtos.CrearSeccionRequest("A", 1, 2060, capacidad, null, null));
        var year = configuracion.obtener(2060);
        configuracion.estado(
                2060, new EstadoAnio(EstadoAnioAcademico.ACTIVO, year.version(), "Inicio del año"));
    }

    private long estudiante() {
        String key = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        var person =
                new PersonaDtos.PersonaRequest(
                        "Prueba",
                        "Estudiante",
                        "Sistema",
                        TipoDocumento.PASAPORTE,
                        key,
                        null,
                        Sexo.PREFIERE_NO_INDICAR,
                        LocalDate.of(2010, 1, 1),
                        null,
                        key + "@example.test",
                        null);
        return personas.createAlumno(new PersonaDtos.AlumnoCreateRequest(person, "EST" + key)).id();
    }

    private SolicitudMatriculaResponse enRevision() {
        long section = configuracion.obtener(2060).secciones().getFirst().id();
        var request =
                solicitudes.crear(new CrearSolicitudMatriculaRequest(estudiante(), section, null));
        request =
                solicitudes.actualizar(
                        request.id(),
                        new ActualizarSolicitudMatriculaRequest(
                                null,
                                EstadoSolicitudMatricula.ENVIADA,
                                null,
                                null,
                                request.version()));
        return solicitudes.actualizar(
                request.id(),
                new ActualizarSolicitudMatriculaRequest(
                        null, EstadoSolicitudMatricula.EN_REVISION, null, null, request.version()));
    }

    @Test
    void rechazaFechasSolapadasYFueraDelAnio() {
        configuracion.crear(
                new CrearAnio(2060, LocalDate.of(2060, 1, 1), LocalDate.of(2060, 12, 31)));
        configuracion.agregarPeriodo(
                2060,
                new CrearPeriodo(
                        "Primero", 1, LocalDate.of(2060, 3, 1), LocalDate.of(2060, 5, 31)));
        assertThatThrownBy(
                        () ->
                                configuracion.agregarPeriodo(
                                        2060,
                                        new CrearPeriodo(
                                                "Segundo",
                                                2,
                                                LocalDate.of(2060, 5, 31),
                                                LocalDate.of(2060, 7, 31))))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(
                        () ->
                                configuracion.agregarPeriodo(
                                        2060,
                                        new CrearPeriodo(
                                                "Fuera",
                                                3,
                                                LocalDate.of(2059, 12, 1),
                                                LocalDate.of(2060, 1, 31))))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(configuracion.obtener(2060).periodos()).hasSize(1);
    }

    @Test
    void limitaUnAnioActivoYExigeCerrarPeriodos() {
        oferta(2);
        configuracion.crear(
                new CrearAnio(2061, LocalDate.of(2061, 1, 1), LocalDate.of(2061, 12, 31)));
        configuracion.agregarPeriodo(
                2061,
                new CrearPeriodo(
                        "Primero", 1, LocalDate.of(2061, 1, 1), LocalDate.of(2061, 3, 31)));
        var second = configuracion.obtener(2061);
        assertThatThrownBy(
                        () ->
                                configuracion.estado(
                                        2061,
                                        new EstadoAnio(
                                                EstadoAnioAcademico.ACTIVO,
                                                second.version(),
                                                "Inicio")))
                .isInstanceOf(ConflictException.class);
        var year = configuracion.obtener(2060);
        long initialVersion = year.version();
        assertThatThrownBy(
                        () ->
                                configuracion.estado(
                                        2060,
                                        new EstadoAnio(
                                                EstadoAnioAcademico.CERRADO,
                                                initialVersion,
                                                "Cierre")))
                .isInstanceOf(BusinessRuleException.class);
        var period = year.periodos().getFirst();
        year =
                configuracion.estadoPeriodo(
                        period.id(),
                        new EstadoPeriodo(
                                EstadoPeriodoAcademico.ACTIVO, period.version(), "Inicio"));
        period = year.periodos().getFirst();
        year =
                configuracion.estadoPeriodo(
                        period.id(),
                        new EstadoPeriodo(
                                EstadoPeriodoAcademico.CERRADO, period.version(), "Cierre"));
        year =
                configuracion.estado(
                        2060,
                        new EstadoAnio(
                                EstadoAnioAcademico.CERRADO, year.version(), "Cierre del año"));
        assertThat(year.estado()).isEqualTo(EstadoAnioAcademico.CERRADO);
        assertThatThrownBy(() -> configuracion.agregarGrado(2060, new CrearGrado(2, 30)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void noReservaVacanteAntesDeFinalizarYRespetaCapacidad() {
        oferta(1);
        var first = enRevision();
        var second = enRevision();
        assertThat(configuracion.obtener(2060).secciones().getFirst().vacantes()).isEqualTo(1);
        var enrollment = matriculas.crear(new CrearMatriculaRequest(first.id(), first.version()));
        assertThat(enrollment.estado()).isEqualTo(EstadoMatricula.ACTIVA);
        assertThat(solicitudes.obtener(first.id()).estado())
                .isEqualTo(EstadoSolicitudMatricula.MATRICULA_FINALIZADA);
        assertThat(configuracion.obtener(2060).secciones().getFirst().vacantes()).isZero();
        assertThatThrownBy(
                        () ->
                                matriculas.crear(
                                        new CrearMatriculaRequest(second.id(), second.version())))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(solicitudes.obtener(second.id()).estado())
                .isEqualTo(EstadoSolicitudMatricula.EN_REVISION);
        assertThat(auditoria.historial("Matricula", enrollment.id())).hasSize(1);
    }

    @Test
    void anularLiberaVacanteConHistorialYPermiteNuevaSolicitud() {
        oferta(1);
        var request = enRevision();
        var enrollment =
                matriculas.crear(new CrearMatriculaRequest(request.id(), request.version()));
        enrollment =
                matriculas.actualizar(
                        enrollment.id(),
                        new ActualizarMatriculaRequest(
                                EstadoMatricula.ANULADA,
                                enrollment.version(),
                                "Error administrativo"));
        assertThat(configuracion.obtener(2060).secciones().getFirst().vacantes()).isEqualTo(1);
        var next =
                solicitudes.crear(
                        new CrearSolicitudMatriculaRequest(
                                request.alumnoId(), request.seccionId(), null));
        assertThat(next.id()).isNotEqualTo(request.id());
        assertThat(auditoria.historial("Matricula", enrollment.id())).hasSize(2);
    }

    @Test
    void estadosYVersionesBloqueanCambiosInvalidos() {
        oferta(2);
        var request = enRevision();
        assertThat(request.version()).isEqualTo(solicitudes.obtener(request.id()).version());
        assertThatThrownBy(
                        () ->
                                solicitudes.actualizar(
                                        request.id(),
                                        new ActualizarSolicitudMatriculaRequest(
                                                null,
                                                EstadoSolicitudMatricula.RECHAZADA,
                                                null,
                                                null,
                                                request.version())))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(
                        () ->
                                solicitudes.actualizar(
                                        request.id(),
                                        new ActualizarSolicitudMatriculaRequest(
                                                null,
                                                EstadoSolicitudMatricula.OBSERVADA,
                                                "Corregir datos",
                                                null,
                                                request.version() - 1)))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> solicitudes.eliminar(request.id()))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void impideReducirCapacidadBajoOcupacionYCambiarIdentidad() {
        oferta(2);
        var first = enRevision();
        var second = enRevision();
        matriculas.crear(new CrearMatriculaRequest(first.id(), first.version()));
        matriculas.crear(new CrearMatriculaRequest(second.id(), second.version()));
        var section = configuracion.obtener(2060).secciones().getFirst();
        assertThatThrownBy(
                        () ->
                                secciones.actualizar(
                                        section.id(),
                                        new AcademicoDtos.ActualizarSeccionRequest(
                                                "A",
                                                1,
                                                2060,
                                                1,
                                                null,
                                                section.version(),
                                                "Reducir")))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(
                        () ->
                                secciones.actualizar(
                                        section.id(),
                                        new AcademicoDtos.ActualizarSeccionRequest(
                                                "B",
                                                1,
                                                2060,
                                                2,
                                                null,
                                                section.version(),
                                                "Renombrar")))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(
                        () ->
                                secciones.cambiarEstado(
                                        section.id(),
                                        new EstadoSeccion(false, section.version(), "Cerrar")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void listaSeccionesActivasEInactivasYNoPermiteUsarLasInactivas() {
        oferta(2);
        var section = configuracion.obtener(2060).secciones().getFirst();

        assertThat(
                        secciones
                                .listar(true, 2060, 1, PageRequest.of(0, 20))
                                .getContent())
                .extracting(response -> response.id())
                .containsExactly(section.id());

        secciones.cambiarEstado(
                section.id(), new EstadoSeccion(false, section.version(), "Retirar oferta"));

        assertThat(
                        secciones
                                .listar(false, 2060, 1, PageRequest.of(0, 20))
                                .getContent())
                .extracting(response -> response.id())
                .containsExactly(section.id());
        assertThat(
                        secciones.listar(null, 2060, 1, PageRequest.of(0, 20)).getContent())
                .isEmpty();
        assertThatThrownBy(
                        () ->
                                solicitudes.crear(
                                        new CrearSolicitudMatriculaRequest(
                                                estudiante(), section.id(), null)))
                .isInstanceOf(com.asiscontrol.exception.ResourceNotFoundException.class);
    }

    @Test
    void matrizContieneCuatroRolesYRechazaMutaciones() {
        assertThat(usuarios.listRoles())
                .extracting(r -> r.nombre())
                .containsExactlyInAnyOrder("ADMINISTRADOR", "DOCENTE", "ALUMNO", "APODERADO");
        assertThatThrownBy(() -> usuarios.createRole(null))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(
                        () ->
                                usuarios.updateRole(
                                        roles.findByNombreIgnoreCase("DOCENTE")
                                                .orElseThrow()
                                                .getId(),
                                        null))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(
                        () ->
                                usuarios.deleteRole(
                                        roles.findByNombreIgnoreCase("DOCENTE")
                                                .orElseThrow()
                                                .getId()))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void filtrosTecnicosYSeguimientoNoBorranEventoOriginal() {
        RegistroError error = new RegistroError();
        error.setFechaHora(Instant.parse("2026-10-02T03:30:00Z"));
        error.setSeveridad(SeveridadError.ERROR);
        error.setComponente("ServicioPrueba");
        error.setMensajeSeguro("Mensaje original seguro");
        error.setCorrelacionId("prueba-correlacion");
        errores.saveAndFlush(error);
        var page =
                auditoria.filtrarErrores(
                        EstadoError.PENDIENTE,
                        SeveridadError.ERROR,
                        "prueba",
                        "prueba-correlacion",
                        LocalDate.of(2026, 10, 1),
                        LocalDate.of(2026, 10, 1),
                        0,
                        20);
        assertThat(page.content()).hasSize(1);
        var updated =
                auditoria.actualizarError(
                        error.getId(),
                        new AuditoriaDtos.UpdateErrorRequest(
                                EstadoError.RESUELTO, "Solución verificada", error.getVersion()));
        assertThat(updated.mensajeSeguro()).isEqualTo("Mensaje original seguro");
        assertThat(auditoria.seguimientos(error.getId())).hasSize(1);
        assertThat(
                        auditoria
                                .filtrarErrores(
                                        null,
                                        null,
                                        null,
                                        "prueba-correlacion",
                                        LocalDate.of(2026, 10, 2),
                                        LocalDate.of(2026, 10, 2),
                                        0,
                                        20)
                                .content())
                .isEmpty();
        assertThatThrownBy(
                        () ->
                                auditoria.actualizarError(
                                        error.getId(),
                                        new AuditoriaDtos.UpdateErrorRequest(
                                                EstadoError.EN_SEGUIMIENTO,
                                                "Nueva observación",
                                                updated.version() - 1)))
                .isInstanceOf(ConflictException.class);
    }
}
