package com.asiscontrol.service;

import com.asiscontrol.entity.*;

import com.asiscontrol.entity.planificacion.AnioAcademico;
import jakarta.persistence.EntityManager;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@Transactional(readOnly = true)
public class FamiliaService {
    private final EntityManager em;
    private final ModuloAccessGuard guard;

    public FamiliaService(EntityManager em, ModuloAccessGuard guard) {
        this.em = em;
        this.guard = guard;
    }

    public record Estudiante(Long id, String codigoAlumno, String nombreCompleto) {}

    public record Oferta(int anio, List<Integer> grados) {}

    public List<Estudiante> estudiantes() {
        return em
                .createQuery(
                        "select distinct v.alumno from AlumnoApoderado v where v.activo=true and"
                            + " v.apoderado.activo=true and v.apoderado.persona.activo=true and"
                            + " v.apoderado.persona.id=:id and v.alumno.activo=true and"
                            + " v.alumno.persona.activo=true",
                        Alumno.class)
                .setParameter("id", guard.personaIdActual())
                .getResultList()
                .stream()
                .map(
                        a ->
                                new Estudiante(
                                        a.getId(),
                                        a.getCodigoAlumno(),
                                        a.getPersona().nombreCompleto()))
                .toList();
    }

    public List<Oferta> oferta() {
        return em
                .createQuery(
                        "select a from AnioAcademico a where"
                            + " a.estado=com.asiscontrol.entity.enums.EstadoAnioAcademico.ACTIVO"
                            + " and a.admisionAbierta=true",
                        AnioAcademico.class)
                .getResultList()
                .stream()
                .map(
                        a ->
                                new Oferta(
                                        a.getAnio(),
                                        em.createQuery(
                                                        "select g.numero from GradoAcademico g"
                                                            + " where g.anioAcademico.id=:id and"
                                                            + " g.activo=true order by g.numero",
                                                        Integer.class)
                                                .setParameter("id", a.getId())
                                                .getResultList()))
                .toList();
    }
}
