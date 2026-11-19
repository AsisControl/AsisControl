package com.asiscontrol.service;

import com.asiscontrol.dto.PagoDto;
import com.asiscontrol.dto.PagosFamiliaDto;
import com.asiscontrol.entity.Alumno;
import com.asiscontrol.entity.Apoderado;
import com.asiscontrol.entity.Comprobante;
import com.asiscontrol.entity.enums.EstadoCompromisoPago;
import com.asiscontrol.repository.ApoderadoRepository;
import com.asiscontrol.repository.CompromisoPagoRepository;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/** Todas las consultas se limitan a asociaciones vigentes de la identidad autenticada. */
@Service
@Transactional(readOnly = true)
public class ConsultaObligacionesService {
    private final ModuloAccessGuard guard;
    private final ApoderadoRepository apoderados;
    private final CompromisoPagoRepository compromisos;
    private final CompromisoPagoService compromisoService;
    private final PagoService pagos;
    private final EntityManager em;

    public ConsultaObligacionesService(ModuloAccessGuard guard, ApoderadoRepository apoderados,
            CompromisoPagoRepository compromisos, CompromisoPagoService compromisoService,
            PagoService pagos, EntityManager em) {
        this.guard = guard;
        this.apoderados = apoderados;
        this.compromisos = compromisos;
        this.compromisoService = compromisoService;
        this.pagos = pagos;
        this.em = em;
    }

    public Apoderado apoderadoActual() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals("ROLE_APODERADO"))) {
            throw denegado();
        }
        var perfil = apoderados.findByPersonaIdAndActivoTrue(guard.personaIdActual()).orElseThrow(this::denegado);
        if (!perfil.getPersona().isActivo()) throw denegado();
        return perfil;
    }

    public List<PagosFamiliaDto.EstudianteResponse> estudiantes() {
        var apoderado = apoderadoActual();
        return em.createQuery("""
                SELECT DISTINCT a FROM AlumnoApoderado v JOIN v.alumno a JOIN a.persona p
                WHERE v.apoderado.id=:apoderado AND v.activo=true AND a.activo=true AND p.activo=true
                ORDER BY a.id
                """, Alumno.class).setParameter("apoderado", apoderado.getId()).getResultList().stream()
                .map(a -> new PagosFamiliaDto.EstudianteResponse(a.getId(), a.getCodigoAlumno(), a.getPersona().nombreCompleto()))
                .toList();
    }

    public Page<PagoDto.CompromisoResponse> listar(Long idAlumno, EstadoCompromisoPago estado, Pageable pageable) {
        var apoderado = apoderadoActual();
        if (idAlumno != null) verificarAlumno(idAlumno, apoderado);
        return compromisos.buscarDeApoderado(apoderado.getId(), idAlumno,
                        estado == null ? null : estado.name(), LocalDate.now(ZoneId.of("America/Lima")), pageable)
                .map(c -> compromisoService.obtener(c.getId()));
    }

    public PagosFamiliaDto.ObligacionDetalle detalle(Long id) {
        var apoderado = apoderadoActual();
        verificarCompromiso(id, apoderado);
        var transacciones = pagos.listarTransacciones(id, apoderado.getPersona().getId(), Pageable.unpaged()).getContent();
        var comprobantes = em.createQuery("""
                SELECT c FROM Comprobante c JOIN c.transaccionPago t
                WHERE c.activo=true AND t.activo=true AND t.compromisoPago.id=:id
                  AND t.estado=com.asiscontrol.entity.enums.EstadoTransaccionPago.APROBADA
                ORDER BY c.fechaEmision DESC
                """, Comprobante.class).setParameter("id", id).getResultList().stream()
                .map(c -> pagos.obtenerComprobante(c.getId())).toList();
        return new PagosFamiliaDto.ObligacionDetalle(compromisoService.obtener(id), transacciones, comprobantes);
    }

    public void verificarAlumno(Long alumnoId, Apoderado apoderado) {
        if (!asociado(alumnoId, apoderado)) throw denegado();
    }

    public boolean asociado(Long alumnoId, Apoderado apoderado) {
        return apoderado.isActivo() && apoderado.getPersona().isActivo() && em.createQuery("""
                SELECT COUNT(v) FROM AlumnoApoderado v
                JOIN v.alumno a JOIN a.persona p
                WHERE v.apoderado.id=:apoderado AND v.activo=true AND a.id=:alumno
                  AND a.activo=true AND p.activo=true
                """, Long.class).setParameter("apoderado", apoderado.getId())
                .setParameter("alumno", alumnoId).getSingleResult() > 0;
    }

    public void verificarCompromiso(Long compromisoId, Apoderado apoderado) {
        Long encontrados = em.createQuery("""
                SELECT COUNT(c) FROM CompromisoPago c JOIN c.matricula m JOIN m.alumno a
                JOIN a.persona p JOIN AlumnoApoderado v ON v.alumno=a
                WHERE c.id=:compromiso AND c.activo=true AND v.apoderado.id=:apoderado
                  AND v.activo=true AND a.activo=true AND p.activo=true
                """, Long.class).setParameter("compromiso", compromisoId)
                .setParameter("apoderado", apoderado.getId()).getSingleResult();
        if (encontrados == 0) throw denegado();
    }

    private AccessDeniedException denegado() {
        return new AccessDeniedException("No tienes acceso a las obligaciones solicitadas.");
    }
}
