package com.asiscontrol.service;

import com.asiscontrol.entity.*;
import com.asiscontrol.security.AuthenticatedUser;
import jakarta.persistence.*;
import org.junit.jupiter.api.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import java.util.*;
import java.util.function.Function;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ModuloAccessGuardTest {
    private ModuloAccessGuard guard;
    @BeforeEach @SuppressWarnings("unchecked") void setup() {
        var em=mock(EntityManager.class); var query=mock(TypedQuery.class,RETURNS_SELF);
        when(em.createQuery(anyString(),eq(Long.class))).thenReturn(query);
        when(query.getSingleResult()).thenReturn(0L);
        guard=new ModuloAccessGuard(em);
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    private void login(String role) {
        var token=Jwt.withTokenValue("test").header("alg","none").subject("prueba").claim("personaId",10L).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(token,List.of(new SimpleGrantedAuthority("ROLE_"+role))));
    }
    private List<Function<Long,Boolean>> permisos() {
        return List.of(guard::puedeAccederAlumno,guard::puedeAccederDocente,guard::puedeAccederApoderado,
                guard::puedeAccederTarea,guard::puedeAccederAsistenciaAlumno,guard::puedeAccederJustificacion,
                guard::puedeAccederEntrega,guard::puedeAccederEvaluacion,guard::puedeAccederCalificacion,
                guard::puedeAccederArchivo,guard::puedeAccederAlertaRiesgo,guard::puedeEliminarArchivo,
                guard::puedeAccederMatricula,guard::puedeAccederSolicitudMatricula,guard::puedeAccederCompromisoPago,
                guard::puedeAccederTransaccionPago,guard::puedeAccederComprobante);
    }

    @Test void ningunRecursoSeAutorizaConIdNulo() {
        for(var permiso:permisos()) assertThat(permiso.apply(null)).isFalse();
    }
    @Test void matrizDeniegaRolDesconocidoYPermiteAdministracion() {
        login("INVITADO");
        for(var permiso:permisos()) assertThat(permiso.apply(99L)).isFalse();
        assertThat(guard.puedeRegistrarPago(1L,2L)).isFalse();
        login("ADMINISTRADOR");
        for(var permiso:permisos()) assertThat(permiso.apply(99L)).isTrue();
        assertThat(guard.restriccionAlumnoPersonaId()).isNull();
        assertThat(guard.restriccionDocentePersonaId()).isNull();
        assertThat(guard.restriccionApoderadoPersonaId()).isNull();
    }
    @Test void docenteYAlumnoSoloPuedenOperarEnSuNombre() {
        var person=new Persona(); person.setId(10L);
        var other=new Persona(); other.setId(11L);
        var docente=new Docente(); docente.setPersona(person);
        var alumno=new Alumno(); alumno.setPersona(person);
        var asignacion=new AsignacionCurso(); asignacion.setDocente(docente);
        login("DOCENTE");
        assertThatCode(()->guard.verificarDocentePropietario(docente)).doesNotThrowAnyException();
        assertThatCode(()->guard.verificarAsignacionDocente(asignacion)).doesNotThrowAnyException();
        docente.setPersona(other);
        assertThatThrownBy(()->guard.verificarDocentePropietario(docente)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(()->guard.verificarAsignacionDocente(asignacion)).isInstanceOf(AccessDeniedException.class);
        login("ALUMNO");
        assertThatCode(()->guard.verificarEntregaAlumno(alumno)).doesNotThrowAnyException();
        assertThatCode(()->guard.verificarSolicitanteJustificacion(alumno)).doesNotThrowAnyException();
        alumno.setPersona(other);
        assertThatThrownBy(()->guard.verificarEntregaAlumno(alumno)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(()->guard.verificarSolicitanteJustificacion(alumno)).isInstanceOf(AccessDeniedException.class);
        login("APODERADO");
        assertThatThrownBy(()->guard.verificarSolicitanteJustificacion(alumno)).isInstanceOf(AccessDeniedException.class);
    }
    @Test void exigeAutenticacionEIdentidadValida() {
        assertThatThrownBy(guard::personaIdActual).isInstanceOf(AccessDeniedException.class);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("sin-persona","x",List.of()));
        assertThatThrownBy(guard::personaIdActual).isInstanceOf(AccessDeniedException.class);
        var p=new Persona(); p.setId(20L); var r=new Rol(); r.setNombre("DOCENTE");
        var u=new AccesoUsuario(); u.setPersona(p); u.setRol(r);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(new AuthenticatedUser(u),"x",List.of()));
        assertThat(guard.personaIdActual()).isEqualTo(20L);
    }
}
