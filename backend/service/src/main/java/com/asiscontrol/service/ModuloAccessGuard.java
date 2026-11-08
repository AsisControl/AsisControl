package com.asiscontrol.service;

import com.asiscontrol.entity.Alumno;
import com.asiscontrol.entity.AsignacionCurso;
import com.asiscontrol.entity.Docente;
import com.asiscontrol.security.AuthenticatedUser;
import jakarta.persistence.EntityManager;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class ModuloAccessGuard {

    private final EntityManager entityManager;

    public ModuloAccessGuard(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public void verificarDocentePropietario(Docente docente) {
        if (tieneRol("ADMINISTRADOR")) {
            return;
        }
        if (!tieneRol("DOCENTE") || !docente.getPersona().getId().equals(personaIdActual())) {
            throw new AccessDeniedException("No puede operar en nombre de otro docente");
        }
    }

    public void verificarAsignacionDocente(AsignacionCurso asignacion) {
        if (tieneRol("ADMINISTRADOR")) {
            return;
        }
        if (!tieneRol("DOCENTE")
                || !asignacion.getDocente().getPersona().getId().equals(personaIdActual())) {
            throw new AccessDeniedException("La asignación no pertenece al docente autenticado");
        }
        // US-58: un docente retirado del curso conserva la consulta de su historial, pero ya no
        // puede registrar ni modificar nada en esa asignacion.
        if (!asignacion.isActivo()) {
            throw new AccessDeniedException("El docente ya no está asignado a este curso y sección");
        }
    }

    // asistencia alumnos: solo el docente asignado registra o corrige (R01); el administrador no
    public void verificarDocenteTitular(AsignacionCurso asignacion) {
        if (!tieneRol("DOCENTE")
                || !asignacion.getDocente().getPersona().getId().equals(personaIdActual())) {
            throw new AccessDeniedException("La asignación no pertenece al docente autenticado");
        }
    }

    public void verificarEntregaAlumno(Alumno alumno) {
        if (tieneRol("ADMINISTRADOR")) {
            return;
        }
        if (!tieneRol("ALUMNO") || !alumno.getPersona().getId().equals(personaIdActual())) {
            throw new AccessDeniedException("No puede registrar una entrega para otro alumno");
        }
    }

    public void verificarSolicitanteJustificacion(Alumno alumno) {
        if (tieneRol("ADMINISTRADOR")) {
            return;
        }
        Long personaId = personaIdActual();
        if (tieneRol("ALUMNO") && alumno.getPersona().getId().equals(personaId)) {
            return;
        }
        if (tieneRol("APODERADO")) {
            Long cantidad = entityManager.createQuery("""
                            SELECT COUNT(v) FROM AlumnoApoderado v
                            WHERE v.activo = true
                              AND v.alumno.id = :alumnoId
                              AND v.apoderado.persona.id = :personaId
                            """, Long.class)
                    .setParameter("alumnoId", alumno.getId())
                    .setParameter("personaId", personaId)
                    .getSingleResult();
            if (cantidad > 0) {
                return;
            }
        }
        throw new AccessDeniedException("No puede gestionar la justificación de este alumno");
    }

    public boolean puedeAccederAlumno(Long alumnoId) {
        if (alumnoId == null) {
            return false;
        }
        if (tieneAlgunRol("ADMINISTRADOR", "DIRECTOR", "SECRETARIA")) {
            return true;
        }
        Long personaId = personaIdActual();
        if (tieneRol("ALUMNO")) {
            return contar("""
                    SELECT COUNT(a) FROM Alumno a
                    WHERE a.id = :alumnoId
                      AND a.activo = true
                      AND a.persona.id = :personaId
                    """, alumnoId, personaId) > 0;
        }
        if (tieneRol("APODERADO")) {
            return contar("""
                    SELECT COUNT(v) FROM AlumnoApoderado v
                    WHERE v.alumno.id = :alumnoId
                      AND v.activo = true
                      AND v.apoderado.activo = true
                      AND v.apoderado.persona.id = :personaId
                    """, alumnoId, personaId) > 0;
        }
        if (tieneRol("DOCENTE")) {
            return contar("""
                    SELECT COUNT(m) FROM Matricula m, AsignacionCurso a
                    WHERE m.alumno.id = :alumnoId
                      AND m.activo = true
                      AND m.estado = com.asiscontrol.entity.enums.EstadoMatricula.ACTIVA
                      AND a.activo = true
                      AND a.seccion.id = m.seccion.id
                      AND a.docente.persona.id = :personaId
                    """, alumnoId, personaId) > 0;
        }
        return false;
    }

    public boolean puedeAccederDocente(Long docenteId) {
        if (docenteId == null) {
            return false;
        }
        if (tieneAlgunRol("ADMINISTRADOR", "DIRECTOR", "SECRETARIA")) {
            return true;
        }
        if (!tieneRol("DOCENTE")) {
            return false;
        }
        Long personaId = personaIdActual();
        return entityManager.createQuery("""
                        SELECT COUNT(d) FROM Docente d
                        WHERE d.id = :docenteId
                          AND d.activo = true
                          AND d.persona.id = :personaId
                        """, Long.class)
                .setParameter("docenteId", docenteId)
                .setParameter("personaId", personaId)
                .getSingleResult() > 0;
    }

    public boolean puedeAccederApoderado(Long apoderadoId) {
        if (apoderadoId == null) {
            return false;
        }
        if (tieneAlgunRol("ADMINISTRADOR", "DIRECTOR", "SECRETARIA")) {
            return true;
        }
        if (!tieneRol("APODERADO")) {
            return false;
        }
        Long personaId = personaIdActual();
        return entityManager.createQuery("""
                        SELECT COUNT(a) FROM Apoderado a
                        WHERE a.id = :apoderadoId
                          AND a.activo = true
                          AND a.persona.id = :personaId
                        """, Long.class)
                .setParameter("apoderadoId", apoderadoId)
                .setParameter("personaId", personaId)
                .getSingleResult() > 0;
    }

    public Long restriccionApoderadoPersonaId() {
        return tieneRol("APODERADO") ? personaIdActual() : null;
    }

    public Long restriccionAlumnoPersonaId() {
        return tieneRol("ALUMNO") ? personaIdActual() : null;
    }

    public Long restriccionDocentePersonaId() {
        return tieneRol("DOCENTE") ? personaIdActual() : null;
    }

    public boolean puedeAccederTarea(Long tareaId) {
        if (tareaId == null) {
            return false;
        }
        if (tieneAlgunRol("ADMINISTRADOR", "DIRECTOR")) {
            return true;
        }
        Long personaId = personaIdActual();
        if (tieneRol("DOCENTE")) {
            return entityManager.createQuery("""
                            SELECT COUNT(t) FROM Tarea t
                            WHERE t.id = :id AND t.activo = true
                              AND t.asignacionCurso.docente.persona.id = :personaId
                            """, Long.class)
                    .setParameter("id", tareaId)
                    .setParameter("personaId", personaId)
                    .getSingleResult() > 0;
        }
        if (tieneRol("ALUMNO")) {
            return entityManager.createQuery("""
                            SELECT COUNT(t) FROM Tarea t, Matricula m
                            WHERE t.id = :id AND t.activo = true
                              AND t.estadoPublicacion <> com.asiscontrol.entity.enums.EstadoPublicacionTarea.BORRADOR
                              AND m.activo = true
                              AND m.estado = com.asiscontrol.entity.enums.EstadoMatricula.ACTIVA
                              AND m.seccion.id = t.asignacionCurso.seccion.id
                              AND m.alumno.persona.id = :personaId
                            """, Long.class)
                    .setParameter("id", tareaId)
                    .setParameter("personaId", personaId)
                    .getSingleResult() > 0;
        }
        if (tieneRol("APODERADO")) {
            return entityManager.createQuery("""
                            SELECT COUNT(t) FROM Tarea t, Matricula m, AlumnoApoderado v
                            WHERE t.id = :id AND t.activo = true
                              AND t.estadoPublicacion <> com.asiscontrol.entity.enums.EstadoPublicacionTarea.BORRADOR
                              AND m.activo = true
                              AND m.estado = com.asiscontrol.entity.enums.EstadoMatricula.ACTIVA
                              AND m.seccion.id = t.asignacionCurso.seccion.id
                              AND v.activo = true
                              AND v.alumno.id = m.alumno.id
                              AND v.apoderado.persona.id = :personaId
                            """, Long.class)
                    .setParameter("id", tareaId)
                    .setParameter("personaId", personaId)
                    .getSingleResult() > 0;
        }
        return false;
    }

    /**
     * US-22: el padron de la seccion solo lo ven administracion, direccion y el docente de la tarea
     * (aunque ya este retirado, para consultar su historial); nunca alumnos ni apoderados.
     */
    public boolean puedeConsultarSeguimientoTarea(Long tareaId) {
        if (tareaId == null) {
            return false;
        }
        if (tieneAlgunRol("ADMINISTRADOR", "DIRECTOR")) {
            return true;
        }
        if (!tieneRol("DOCENTE")) {
            return false;
        }
        return entityManager.createQuery("""
                        SELECT COUNT(t) FROM Tarea t
                        WHERE t.id = :id AND t.activo = true
                          AND t.asignacionCurso.docente.persona.id = :personaId
                        """, Long.class)
                .setParameter("id", tareaId)
                .setParameter("personaId", personaIdActual())
                .getSingleResult() > 0;
    }

    public boolean puedeAccederAsistenciaAlumno(Long asistenciaId) {
        if (asistenciaId == null) {
            return false;
        }
        if (tieneAlgunRol("ADMINISTRADOR", "DIRECTOR", "SECRETARIA")) {
            return true;
        }
        Long personaId = personaIdActual();
        if (tieneRol("DOCENTE")) {
            return entityManager.createQuery("""
                            SELECT COUNT(a) FROM AsistenciaAlumno a
                            WHERE a.id = :id AND a.activo = true
                              AND a.sesionClase.estado = com.asiscontrol.entity.asistencia.EstadoSesionClase.TOMADA
                              AND a.asignacionCurso.docente.persona.id = :personaId
                            """, Long.class)
                    .setParameter("id", asistenciaId)
                    .setParameter("personaId", personaId)
                    .getSingleResult() > 0;
        }
        if (tieneRol("ALUMNO")) {
            return entityManager.createQuery("""
                            SELECT COUNT(a) FROM AsistenciaAlumno a
                            WHERE a.id = :id AND a.activo = true
                              AND a.sesionClase.estado = com.asiscontrol.entity.asistencia.EstadoSesionClase.TOMADA
                              AND a.alumno.persona.id = :personaId
                            """, Long.class)
                    .setParameter("id", asistenciaId)
                    .setParameter("personaId", personaId)
                    .getSingleResult() > 0;
        }
        if (tieneRol("APODERADO")) {
            return entityManager.createQuery("""
                            SELECT COUNT(a) FROM AsistenciaAlumno a, AlumnoApoderado v
                            WHERE a.id = :id AND a.activo = true
                              AND a.sesionClase.estado = com.asiscontrol.entity.asistencia.EstadoSesionClase.TOMADA
                              AND v.activo = true
                              AND v.alumno.id = a.alumno.id
                              AND v.apoderado.persona.id = :personaId
                            """, Long.class)
                    .setParameter("id", asistenciaId)
                    .setParameter("personaId", personaId)
                    .getSingleResult() > 0;
        }
        return false;
    }

    public boolean puedeAccederJustificacion(Long justificacionId) {
        if (justificacionId == null) {
            return false;
        }
        if (tieneAlgunRol("ADMINISTRADOR", "DIRECTOR", "SECRETARIA")) {
            return true;
        }
        Long personaId = personaIdActual();
        if (tieneRol("DOCENTE")) {
            return entityManager.createQuery("""
                            SELECT COUNT(j) FROM JustificacionAsistencia j
                            WHERE j.id = :id AND j.activo = true
                              AND j.asistenciaAlumno.asignacionCurso.docente.persona.id = :personaId
                            """, Long.class)
                    .setParameter("id", justificacionId)
                    .setParameter("personaId", personaId)
                    .getSingleResult() > 0;
        }
        if (tieneRol("ALUMNO")) {
            return entityManager.createQuery("""
                            SELECT COUNT(j) FROM JustificacionAsistencia j
                            WHERE j.id = :id AND j.activo = true
                              AND j.asistenciaAlumno.alumno.persona.id = :personaId
                            """, Long.class)
                    .setParameter("id", justificacionId)
                    .setParameter("personaId", personaId)
                    .getSingleResult() > 0;
        }
        if (tieneRol("APODERADO")) {
            return entityManager.createQuery("""
                            SELECT COUNT(j) FROM JustificacionAsistencia j, AlumnoApoderado v
                            WHERE j.id = :id AND j.activo = true
                              AND v.activo = true
                              AND v.alumno.id = j.asistenciaAlumno.alumno.id
                              AND v.apoderado.persona.id = :personaId
                            """, Long.class)
                    .setParameter("id", justificacionId)
                    .setParameter("personaId", personaId)
                    .getSingleResult() > 0;
        }
        return false;
    }

    public boolean puedeAccederEntrega(Long entregaId) {
        if (entregaId == null) {
            return false;
        }
        if (tieneAlgunRol("ADMINISTRADOR", "DIRECTOR")) {
            return true;
        }
        Long personaId = personaIdActual();
        if (tieneRol("DOCENTE")) {
            return entityManager.createQuery("""
                            SELECT COUNT(e) FROM EntregaTarea e
                            WHERE e.id = :id AND e.activo = true
                              AND e.tarea.asignacionCurso.docente.persona.id = :personaId
                            """, Long.class)
                    .setParameter("id", entregaId)
                    .setParameter("personaId", personaId)
                    .getSingleResult() > 0;
        }
        if (tieneRol("ALUMNO")) {
            return entityManager.createQuery("""
                            SELECT COUNT(e) FROM EntregaTarea e
                            WHERE e.id = :id AND e.activo = true
                              AND e.alumno.persona.id = :personaId
                            """, Long.class)
                    .setParameter("id", entregaId)
                    .setParameter("personaId", personaId)
                    .getSingleResult() > 0;
        }
        if (tieneRol("APODERADO")) {
            return entityManager.createQuery("""
                            SELECT COUNT(e) FROM EntregaTarea e, AlumnoApoderado v
                            WHERE e.id = :id AND e.activo = true
                              AND v.activo = true
                              AND v.alumno.id = e.alumno.id
                              AND v.apoderado.persona.id = :personaId
                            """, Long.class)
                    .setParameter("id", entregaId)
                    .setParameter("personaId", personaId)
                    .getSingleResult() > 0;
        }
        return false;
    }

    /** US-18: la planilla expone el padron: solo administracion, direccion y el docente del curso. */
    public boolean puedeGestionarEvaluacion(Long evaluacionId) {
        if (evaluacionId == null) {
            return false;
        }
        if (tieneAlgunRol("ADMINISTRADOR", "DIRECTOR")) {
            return true;
        }
        if (!tieneRol("DOCENTE")) {
            return false;
        }
        return entityManager.createQuery("""
                        SELECT COUNT(e) FROM Evaluacion e
                        WHERE e.id = :id AND e.activo = true
                          AND e.asignacionCurso.docente.persona.id = :personaId
                        """, Long.class)
                .setParameter("id", evaluacionId)
                .setParameter("personaId", personaIdActual())
                .getSingleResult() > 0;
    }

    public boolean puedeAccederEvaluacion(Long evaluacionId) {
        if (evaluacionId == null) {
            return false;
        }
        if (tieneAlgunRol("ADMINISTRADOR", "DIRECTOR")) {
            return true;
        }
        Long personaId = personaIdActual();
        if (tieneRol("DOCENTE")) {
            return entityManager.createQuery("""
                            SELECT COUNT(e) FROM Evaluacion e
                            WHERE e.id = :id AND e.activo = true
                              AND e.asignacionCurso.docente.persona.id = :personaId
                            """, Long.class)
                    .setParameter("id", evaluacionId)
                    .setParameter("personaId", personaId)
                    .getSingleResult() > 0;
        }
        if (tieneRol("ALUMNO")) {
            return entityManager.createQuery("""
                            SELECT COUNT(e) FROM Evaluacion e, Matricula m
                            WHERE e.id = :id AND e.activo = true
                              AND e.estado IN (
                                  com.asiscontrol.entity.enums.EstadoEvaluacion.PUBLICADA,
                                  com.asiscontrol.entity.enums.EstadoEvaluacion.CERRADA
                              )
                              AND m.activo = true
                              AND m.estado = com.asiscontrol.entity.enums.EstadoMatricula.ACTIVA
                              AND m.seccion.id = e.asignacionCurso.seccion.id
                              AND m.alumno.persona.id = :personaId
                            """, Long.class)
                    .setParameter("id", evaluacionId)
                    .setParameter("personaId", personaId)
                    .getSingleResult() > 0;
        }
        if (tieneRol("APODERADO")) {
            return entityManager.createQuery("""
                            SELECT COUNT(e) FROM Evaluacion e, Matricula m, AlumnoApoderado v
                            WHERE e.id = :id AND e.activo = true
                              AND e.estado IN (
                                  com.asiscontrol.entity.enums.EstadoEvaluacion.PUBLICADA,
                                  com.asiscontrol.entity.enums.EstadoEvaluacion.CERRADA
                              )
                              AND m.activo = true
                              AND m.estado = com.asiscontrol.entity.enums.EstadoMatricula.ACTIVA
                              AND m.seccion.id = e.asignacionCurso.seccion.id
                              AND v.activo = true
                              AND v.alumno.id = m.alumno.id
                              AND v.apoderado.persona.id = :personaId
                            """, Long.class)
                    .setParameter("id", evaluacionId)
                    .setParameter("personaId", personaId)
                    .getSingleResult() > 0;
        }
        return false;
    }

    public boolean puedeAccederCalificacion(Long calificacionId) {
        if (calificacionId == null) {
            return false;
        }
        if (tieneAlgunRol("ADMINISTRADOR", "DIRECTOR")) {
            return true;
        }
        if (!tieneRol("DOCENTE")) {
            return false;
        }
        return entityManager.createQuery("""
                        SELECT COUNT(c) FROM Calificacion c
                        WHERE c.id = :id AND c.activo = true
                          AND c.evaluacion.asignacionCurso.docente.persona.id = :personaId
                        """, Long.class)
                .setParameter("id", calificacionId)
                .setParameter("personaId", personaIdActual())
                .getSingleResult() > 0;
    }

    public boolean puedeAccederArchivo(Long archivoId) {
        if (archivoId == null) {
            return false;
        }
        if (tieneAlgunRol("ADMINISTRADOR", "DIRECTOR", "SECRETARIA")) {
            return true;
        }
        Long personaId = personaIdActual();
        if (esPropietarioArchivo(archivoId, personaId)) {
            return true;
        }
        if (tieneRol("DOCENTE")) {
            Long visibles = entityManager.createQuery("""
                            SELECT COUNT(DISTINCT a) FROM ArchivoAdjunto a
                            WHERE a.id = :id AND a.activo = true AND (
                                EXISTS (SELECT t.id FROM Tarea t JOIN t.archivos f
                                        WHERE f.id = a.id
                                          AND t.asignacionCurso.docente.persona.id = :personaId)
                                OR EXISTS (SELECT e.id FROM EntregaTarea e JOIN e.archivos f
                                           WHERE f.id = a.id
                                             AND e.tarea.asignacionCurso.docente.persona.id = :personaId)
                                OR EXISTS (SELECT j.id FROM JustificacionAsistencia j JOIN j.archivos f
                                           WHERE f.id = a.id
                                             AND j.asistenciaAlumno.asignacionCurso.docente.persona.id = :personaId)
                            )
                            """, Long.class)
                    .setParameter("id", archivoId)
                    .setParameter("personaId", personaId)
                    .getSingleResult();
            return visibles > 0;
        }
        if (tieneRol("ALUMNO")) {
            Long visibles = entityManager.createQuery("""
                            SELECT COUNT(DISTINCT a) FROM ArchivoAdjunto a
                            WHERE a.id = :id AND a.activo = true AND (
                                EXISTS (SELECT e.id FROM EntregaTarea e JOIN e.archivos f
                                        WHERE f.id = a.id AND e.alumno.persona.id = :personaId)
                                OR EXISTS (SELECT j.id FROM JustificacionAsistencia j JOIN j.archivos f
                                           WHERE f.id = a.id
                                             AND j.asistenciaAlumno.alumno.persona.id = :personaId)
                                OR EXISTS (SELECT t.id FROM Tarea t JOIN t.archivos f, Matricula m
                                           WHERE f.id = a.id
                                             AND t.estadoPublicacion <>
                                                 com.asiscontrol.entity.enums.EstadoPublicacionTarea.BORRADOR
                                             AND m.activo = true
                                             AND m.estado = com.asiscontrol.entity.enums.EstadoMatricula.ACTIVA
                                             AND m.seccion.id = t.asignacionCurso.seccion.id
                                             AND m.alumno.persona.id = :personaId)
                            )
                            """, Long.class)
                    .setParameter("id", archivoId)
                    .setParameter("personaId", personaId)
                    .getSingleResult();
            return visibles > 0;
        }
        if (tieneRol("APODERADO")) {
            Long visibles = entityManager.createQuery("""
                            SELECT COUNT(DISTINCT a) FROM ArchivoAdjunto a
                            WHERE a.id = :id AND a.activo = true AND (
                                EXISTS (SELECT e.id FROM EntregaTarea e JOIN e.archivos f, AlumnoApoderado v
                                        WHERE f.id = a.id AND v.activo = true
                                          AND v.alumno.id = e.alumno.id
                                          AND v.apoderado.persona.id = :personaId)
                                OR EXISTS (SELECT j.id FROM JustificacionAsistencia j JOIN j.archivos f,
                                                  AlumnoApoderado v
                                           WHERE f.id = a.id AND v.activo = true
                                             AND v.alumno.id = j.asistenciaAlumno.alumno.id
                                             AND v.apoderado.persona.id = :personaId)
                                OR EXISTS (SELECT c.id FROM Comprobante c, AlumnoApoderado v
                                           WHERE c.archivo.id = a.id AND v.activo = true
                                             AND v.alumno.id = c.transaccionPago.compromisoPago.matricula.alumno.id
                                             AND v.apoderado.persona.id = :personaId)
                            )
                            """, Long.class)
                    .setParameter("id", archivoId)
                    .setParameter("personaId", personaId)
                    .getSingleResult();
            return visibles > 0;
        }
        if (tieneRol("TESORERIA")) {
            return entityManager.createQuery("""
                            SELECT COUNT(c) FROM Comprobante c
                            WHERE c.activo = true AND c.archivo.id = :id
                            """, Long.class)
                    .setParameter("id", archivoId)
                    .getSingleResult() > 0;
        }
        return false;
    }

    public boolean puedeAccederAlertaRiesgo(Long alertaId) {
        if (alertaId == null) {
            return false;
        }
        if (tieneAlgunRol("ADMINISTRADOR", "DIRECTOR")) {
            return true;
        }
        if (!tieneRol("DOCENTE")) {
            return false;
        }
        return entityManager.createQuery("""
                        SELECT COUNT(DISTINCT a) FROM AlertaRiesgo a, Matricula m, AsignacionCurso ac
                        WHERE a.id = :id AND a.activo = true
                          AND m.activo = true
                          AND m.estado = com.asiscontrol.entity.enums.EstadoMatricula.ACTIVA
                          AND m.alumno.id = a.alumno.id
                          AND ac.activo = true
                          AND ac.seccion.id = m.seccion.id
                          AND ac.docente.persona.id = :personaId
                        """, Long.class)
                .setParameter("id", alertaId)
                .setParameter("personaId", personaIdActual())
                .getSingleResult() > 0;
    }

    public boolean puedeEliminarArchivo(Long archivoId) {
        if (archivoId == null) {
            return false;
        }
        return tieneRol("ADMINISTRADOR")
                || esPropietarioArchivo(archivoId, personaIdActual());
    }

    private boolean esPropietarioArchivo(Long archivoId, Long personaId) {
        return entityManager.createQuery("""
                        SELECT COUNT(a) FROM ArchivoAdjunto a
                        WHERE a.id = :id AND a.activo = true
                          AND a.cargadoPor.id = :personaId
                        """, Long.class)
                .setParameter("id", archivoId)
                .setParameter("personaId", personaId)
                .getSingleResult() > 0;
    }

    public boolean puedeAccederMatricula(Long matriculaId) {
        if (matriculaId == null) {
            return false;
        }
        if (tieneAlgunRol("ADMINISTRADOR", "DIRECTOR", "SECRETARIA", "TESORERIA")) {
            return true;
        }
        Long personaId = personaIdActual();
        if (tieneRol("ALUMNO")) {
            return entityManager.createQuery("""
                            SELECT COUNT(m) FROM Matricula m
                            WHERE m.id = :id AND m.activo = true
                              AND m.alumno.persona.id = :personaId
                            """, Long.class)
                    .setParameter("id", matriculaId)
                    .setParameter("personaId", personaId)
                    .getSingleResult() > 0;
        }
        if (tieneRol("APODERADO")) {
            return entityManager.createQuery("""
                            SELECT COUNT(m) FROM Matricula m, AlumnoApoderado v
                            WHERE m.id = :id AND m.activo = true
                              AND v.activo = true
                              AND v.apoderado.activo = true AND v.apoderado.persona.activo = true
                              AND v.alumno.activo = true AND v.alumno.persona.activo = true
                              AND v.alumno.id = m.alumno.id
                              AND v.apoderado.persona.id = :personaId
                            """, Long.class)
                    .setParameter("id", matriculaId)
                    .setParameter("personaId", personaId)
                    .getSingleResult() > 0;
        }
        return false;
    }

    public boolean puedeAccederSolicitudMatricula(Long solicitudId) {
        if (solicitudId == null) {
            return false;
        }
        if (tieneAlgunRol("ADMINISTRADOR", "DIRECTOR", "SECRETARIA")) {
            return true;
        }
        Long personaId = personaIdActual();
        if (tieneRol("ALUMNO")) {
            return entityManager.createQuery("""
                            SELECT COUNT(s) FROM SolicitudMatricula s
                            WHERE s.id = :id AND s.activo = true
                              AND s.alumno.persona.id = :personaId
                            """, Long.class)
                    .setParameter("id", solicitudId)
                    .setParameter("personaId", personaId)
                    .getSingleResult() > 0;
        }
        if (tieneRol("APODERADO")) {
            return entityManager.createQuery("""
                            SELECT COUNT(s) FROM SolicitudMatricula s, AlumnoApoderado v
                            WHERE s.id = :id AND s.activo = true
                              AND v.activo = true AND v.apoderado.activo = true AND v.apoderado.persona.activo = true AND v.alumno.activo = true AND v.alumno.persona.activo = true
                              AND v.alumno.id = s.alumno.id
                              AND v.apoderado.persona.id = :personaId
                            """, Long.class)
                    .setParameter("id", solicitudId)
                    .setParameter("personaId", personaId)
                    .getSingleResult() > 0;
        }
        return false;
    }

    public boolean puedeAccederCompromisoPago(Long compromisoId) {
        if (compromisoId == null) {
            return false;
        }
        if (tieneAlgunRol("ADMINISTRADOR", "DIRECTOR", "SECRETARIA", "TESORERIA")) {
            return true;
        }
        if (!tieneRol("APODERADO")) {
            return false;
        }
        return entityManager.createQuery("""
                        SELECT COUNT(c) FROM CompromisoPago c, AlumnoApoderado v
                        WHERE c.id = :id
                          AND c.activo = true
                          AND v.activo = true
                          AND v.alumno.id = c.matricula.alumno.id
                          AND v.apoderado.persona.id = :personaId
                          AND v.apoderado.activo = true AND v.apoderado.persona.activo = true
                          AND v.alumno.activo = true AND v.alumno.persona.activo = true
                        """, Long.class)
                .setParameter("id", compromisoId)
                .setParameter("personaId", personaIdActual())
                .getSingleResult() > 0;
    }

    public boolean puedeAccederTransaccionPago(Long transaccionId) {
        if (transaccionId == null) {
            return false;
        }
        if (tieneAlgunRol("ADMINISTRADOR", "DIRECTOR", "SECRETARIA", "TESORERIA")) {
            return true;
        }
        if (!tieneRol("APODERADO")) {
            return false;
        }
        return entityManager.createQuery("""
                        SELECT COUNT(t) FROM TransaccionPago t, AlumnoApoderado v
                        WHERE t.id = :id
                          AND t.activo = true
                          AND v.activo = true
                          AND v.alumno.id = t.compromisoPago.matricula.alumno.id
                          AND v.apoderado.persona.id = :personaId
                          AND v.apoderado.activo = true AND v.apoderado.persona.activo = true
                          AND v.alumno.activo = true AND v.alumno.persona.activo = true
                        """, Long.class)
                .setParameter("id", transaccionId)
                .setParameter("personaId", personaIdActual())
                .getSingleResult() > 0;
    }

    public boolean puedeAccederComprobante(Long comprobanteId) {
        if (comprobanteId == null) {
            return false;
        }
        if (tieneAlgunRol("ADMINISTRADOR", "DIRECTOR", "SECRETARIA", "TESORERIA")) {
            return true;
        }
        if (!tieneRol("APODERADO")) {
            return false;
        }
        return entityManager.createQuery("""
                        SELECT COUNT(c) FROM Comprobante c, AlumnoApoderado v
                        WHERE c.id = :id
                          AND c.activo = true
                          AND v.activo = true
                          AND v.alumno.id = c.transaccionPago.compromisoPago.matricula.alumno.id
                          AND v.apoderado.persona.id = :personaId
                          AND v.apoderado.activo = true AND v.apoderado.persona.activo = true
                          AND v.alumno.activo = true AND v.alumno.persona.activo = true
                        """, Long.class)
                .setParameter("id", comprobanteId)
                .setParameter("personaId", personaIdActual())
                .getSingleResult() > 0;
    }

    public boolean puedeRegistrarPago(Long apoderadoId, Long compromisoId) {
        if (!tieneRol("APODERADO") || apoderadoId == null || compromisoId == null) {
            return false;
        }
        return entityManager.createQuery("""
                        SELECT COUNT(v) FROM AlumnoApoderado v, CompromisoPago c
                        WHERE v.activo = true
                          AND v.apoderado.id = :apoderadoId
                          AND v.apoderado.persona.id = :personaId
                          AND c.id = :compromisoId
                          AND c.activo = true
                          AND c.matricula.alumno.id = v.alumno.id
                        """, Long.class)
                .setParameter("apoderadoId", apoderadoId)
                .setParameter("personaId", personaIdActual())
                .setParameter("compromisoId", compromisoId)
                .getSingleResult() > 0;
    }

    private long contar(String jpql, Long alumnoId, Long personaId) {
        return entityManager.createQuery(jpql, Long.class)
                .setParameter("alumnoId", alumnoId)
                .setParameter("personaId", personaId)
                .getSingleResult();
    }

    private boolean tieneAlgunRol(String... roles) {
        for (String rol : roles) {
            if (tieneRol(rol)) {
                return true;
            }
        }
        return false;
    }

    private boolean tieneRol(String rol) {
        Authentication authentication = autenticacionActual();
        String autoridad = "ROLE_" + rol;
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(autoridad::equals);
    }

    public Long personaIdActual() {
        Object principal = autenticacionActual().getPrincipal();
        if (principal instanceof Jwt jwt) {
            Object claim = jwt.getClaim("personaId");
            if (claim instanceof Number numero) {
                return numero.longValue();
            }
        }
        if (principal instanceof AuthenticatedUser usuario) {
            return usuario.getUsuario().getPersona().getId();
        }
        throw new AccessDeniedException("La identidad autenticada no contiene una persona válida");
    }

    private Authentication autenticacionActual() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Se requiere una sesión autenticada");
        }
        return authentication;
    }
}
