package com.asiscontrol.config;

import com.asiscontrol.dto.PagoDto;
import com.asiscontrol.entity.AccesoUsuario;
import com.asiscontrol.entity.Alumno;
import com.asiscontrol.entity.AlumnoApoderado;
import com.asiscontrol.entity.Apoderado;
import com.asiscontrol.entity.GradoAcademico;
import com.asiscontrol.entity.Matricula;
import com.asiscontrol.entity.Persona;
import com.asiscontrol.entity.Rol;
import com.asiscontrol.entity.Seccion;
import com.asiscontrol.entity.SolicitudMatricula;
import com.asiscontrol.entity.enums.EstadoAcceso;
import com.asiscontrol.entity.enums.EstadoAnioAcademico;
import com.asiscontrol.entity.enums.EstadoMatricula;
import com.asiscontrol.entity.enums.EstadoSolicitudMatricula;
import com.asiscontrol.entity.enums.MedioPago;
import com.asiscontrol.entity.enums.Parentesco;
import com.asiscontrol.entity.enums.TipoComprobante;
import com.asiscontrol.entity.enums.TipoDocumento;
import com.asiscontrol.entity.planificacion.AnioAcademico;
import com.asiscontrol.service.CompromisoPagoService;
import com.asiscontrol.service.PagoService;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;

/** Datos ficticios opt-in; no se crean cuentas ni se restablecen claves en producción. */
@Component
@Profile("local")
@ConditionalOnProperty(name = "app.pagos.demo.enabled", havingValue = "true")
public class PagosDemoDataInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger(PagosDemoDataInitializer.class);
    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private final EntityManager em;
    private final PasswordEncoder passwords;
    private final CompromisoPagoService compromisos;
    private final PagoService pagos;
    private final String password;

    public PagosDemoDataInitializer(EntityManager em, PasswordEncoder passwords,
            CompromisoPagoService compromisos, PagoService pagos,
            @Value("${app.pagos.demo.password:}") String password) {
        this.em = em;
        this.passwords = passwords;
        this.compromisos = compromisos;
        this.pagos = pagos;
        this.password = password;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void preparar() {
        if (!password.matches("(?=.{12,64}$)(?=.*[A-Z])(?=.*[a-z])(?=.*[0-9])[^\\s]+")) {
            throw new IllegalStateException("La demo de pagos requiere PAYMENTS_DEMO_PASSWORD de 12 a 64 caracteres, con mayúscula, minúscula y número.");
        }
        if (em.createQuery("select count(a) from AccesoUsuario a where a.username in :usuarios", Long.class)
                .setParameter("usuarios", java.util.List.of("demo.pagos.1", "demo.pagos.2"))
                .getSingleResult() == 2) {
            LOGGER.info("La demo de pagos ya está preparada; se conservan cuentas, claves y pagos.");
            return;
        }
        var rol = em.createQuery("select r from Rol r where r.nombre='APODERADO' and r.activo=true", Rol.class)
                .getSingleResult();
        var anio = anio();
        var seccion = seccion(anio);
        familia(1, "Carla", "Ramos", rol, seccion, 2);
        familia(2, "Jorge", "Torres", rol, seccion, 1);
        LOGGER.info("Demo de pagos preparada: demo.pagos.1 y demo.pagos.2, con estudiantes distintos. Las claves no se registran en logs.");
    }

    private AnioAcademico anio() {
        var activos = em.createQuery("select a from AnioAcademico a where a.activo=true and a.estado=:estado order by a.anio desc", AnioAcademico.class)
                .setParameter("estado", EstadoAnioAcademico.ACTIVO).setMaxResults(1).getResultList();
        if (!activos.isEmpty()) return activos.getFirst();
        int numero = LocalDate.now(LIMA).getYear();
        while (em.createQuery("select count(a) from AnioAcademico a where a.anio=:anio", Long.class)
                .setParameter("anio", numero).getSingleResult() > 0) numero++;
        var anio = new AnioAcademico();
        anio.setAnio(numero);
        anio.setFechaInicio(LocalDate.of(numero, 1, 1));
        anio.setFechaFin(LocalDate.of(numero, 12, 31));
        anio.setEstado(EstadoAnioAcademico.ACTIVO);
        anio.setClaveActiva(1);
        em.persist(anio);
        return anio;
    }

    private Seccion seccion(AnioAcademico anio) {
        var existentes = em.createQuery("select s from Seccion s where s.anioAcademico=:anio and s.grado=1 and s.nombre='DEMO-PAGOS'", Seccion.class)
                .setParameter("anio", anio.getAnio()).getResultList();
        if (!existentes.isEmpty()) return existentes.getFirst();
        var grados = em.createQuery("select g from GradoAcademico g where g.anioAcademico.id=:id and g.numero=1", GradoAcademico.class)
                .setParameter("id", anio.getId()).getResultList();
        GradoAcademico grado;
        if (grados.isEmpty()) {
            grado = new GradoAcademico();
            grado.setAnioAcademico(anio);
            grado.setNumero(1);
            grado.setCapacidadDefault(20);
            em.persist(grado);
        } else grado = grados.getFirst();
        var seccion = new Seccion();
        seccion.setAnioAcademico(anio.getAnio());
        seccion.setGrado(1);
        seccion.setNombre("DEMO-PAGOS");
        seccion.setGradoAcademico(grado);
        seccion.setCapacidadMaxima(20);
        em.persist(seccion);
        return seccion;
    }

    private void familia(int numero, String nombre, String apellido, Rol rol, Seccion seccion, int hijos) {
        String username = "demo.pagos." + numero;
        if (em.createQuery("select count(a) from AccesoUsuario a where a.username=:usuario", Long.class)
                .setParameter("usuario", username).getSingleResult() > 0) return;
        var persona = persona("9000000" + numero, nombre, apellido, LocalDate.of(1985, 5, 10));
        var apoderado = new Apoderado();
        apoderado.setPersona(persona);
        em.persist(apoderado);
        var acceso = new AccesoUsuario();
        acceso.setPersona(persona);
        acceso.setRol(rol);
        acceso.setUsername(username);
        acceso.setCorreo(username + "@example.test");
        acceso.setContrasenaHash(passwords.encode(password));
        acceso.setEstado(EstadoAcceso.ACTIVO);
        acceso.setCorreoVerificado(true);
        acceso.setRequiereCambioContrasena(false);
        em.persist(acceso);
        for (int hijo = 1; hijo <= hijos; hijo++) estudiante(numero, hijo, apellido, apoderado, seccion);
    }

    private Persona persona(String documento, String nombre, String apellido, LocalDate nacimiento) {
        String libre = documento;
        long candidato = Long.parseLong(documento);
        while (em.createQuery("select count(p) from Persona p where p.tipoDocumento=:tipo and p.numeroDocumento=:documento", Long.class)
                .setParameter("tipo", TipoDocumento.DNI).setParameter("documento", libre).getSingleResult() > 0) {
            candidato = candidato == 99999999L ? 90000000L : candidato + 1;
            libre = String.format(Locale.ROOT, "%08d", candidato);
            if (libre.equals(documento)) {
                throw new IllegalStateException("No hay documentos libres para los datos de demostración.");
            }
        }
        var persona = new Persona();
        persona.setTipoDocumento(TipoDocumento.DNI);
        persona.setNumeroDocumento(libre);
        persona.setNombres(nombre);
        persona.setApellidoPaterno(apellido);
        persona.setApellidoMaterno("Demostración");
        persona.setFechaNacimiento(nacimiento);
        em.persist(persona);
        return persona;
    }

    private void estudiante(int familia, int numero, String apellido, Apoderado apoderado, Seccion seccion) {
        var alumno = new Alumno();
        alumno.setPersona(persona("900001" + familia + numero, numero == 1 ? "Lucía" : "Mateo", apellido,
                LocalDate.of(LocalDate.now(LIMA).getYear() - 13, 3, 10)));
        alumno.setCodigoAlumno("DEMO-PAGOS-" + familia + "-" + numero);
        em.persist(alumno);
        var vinculo = new AlumnoApoderado();
        vinculo.setAlumno(alumno);
        vinculo.setApoderado(apoderado);
        vinculo.setParentesco(familia == 1 ? Parentesco.MADRE : Parentesco.PADRE);
        vinculo.setPrincipal(true);
        em.persist(vinculo);
        var solicitud = new SolicitudMatricula();
        solicitud.setAlumno(alumno);
        solicitud.setSeccion(seccion);
        solicitud.setAnioAcademico(seccion.getAnioAcademico());
        solicitud.setGrado(1);
        solicitud.setEstado(EstadoSolicitudMatricula.MATRICULA_FINALIZADA);
        em.persist(solicitud);
        var matricula = new Matricula();
        matricula.setCodigoMatricula("MAT-DEMO-PAGOS-" + familia + "-" + numero);
        matricula.setAlumno(alumno);
        matricula.setSeccion(seccion);
        matricula.setSolicitudMatricula(solicitud);
        matricula.setAnioAcademico(seccion.getAnioAcademico());
        matricula.setEstado(EstadoMatricula.ACTIVA);
        matricula.setFechaMatricula(Instant.now());
        em.persist(matricula);
        em.flush();
        for (int cuota = 1; cuota <= 3; cuota++) {
            long dias = cuota == 1 ? -45 : cuota == 2 ? -7 : 15;
            var compromiso = compromisos.emitir(new PagoDto.CompromisoRequest(matricula.getId(), null,
                    "Pensión de demostración", "Datos ficticios de la demo local de pagos.", new BigDecimal("350.00"),
                    "PEN", LocalDate.now(LIMA).plusDays(dias), cuota));
            if (cuota == 1) {
                var transaccion = pagos.registrarTransaccion(new PagoDto.TransaccionRequest(compromiso.id(),
                        apoderado.getId(), null, "DEMO-PAGADA-" + familia + "-" + numero,
                        compromiso.montoTotal(), MedioPago.TARJETA_SIMULADA, Instant.now(), "Pago ficticio previo de demostración."));
                pagos.emitirComprobante(new PagoDto.ComprobanteRequest(transaccion.id(),
                        TipoComprobante.CONSTANCIA_PAGO_DIGITAL, "DEMO", familia + "-" + numero, null));
            }
        }
    }
}
