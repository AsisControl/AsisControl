package com.asiscontrol.repository;

import com.asiscontrol.entity.Seccion;
import com.asiscontrol.entity.enums.EstadoAnioAcademico;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SeccionRepository extends JpaRepository<Seccion, Long> {

    Optional<Seccion> findByIdAndActivoTrue(Long id);

    java.util.List<Seccion> findByAnioAcademicoOrderByGradoAscNombreAsc(int anio);

    Optional<Seccion> findByAnioAcademicoAndGradoAndNombreIgnoreCaseAndActivoTrue(
            int anioAcademico, int grado, String nombre);

    Optional<Seccion> findByAnioAcademicoAndGradoAndNombreIgnoreCase(
            int anioAcademico, int grado, String nombre);

    Optional<Seccion> findByGradoAcademicoIdAndNombreIgnoreCase(Long gradoAcademicoId, String nombre);

    boolean existsByGradoAcademicoIdAndActivoTrue(Long gradoAcademicoId);

    boolean existsByAnioAcademico(int anioAcademico);

    /** Cualquier sección que tenga el aula, también inactiva o de un año cerrado. */
    java.util.List<Seccion> findByAulaIdOrderByAnioAcademicoAscGradoAscNombreAsc(Long aulaId);

    /** Secciones activas con el aula cuyo año académico no está cerrado. */
    @Query(
            """
            SELECT s FROM Seccion s
            WHERE s.aula.id = :aulaId AND s.activo = true
              AND NOT EXISTS (
                    SELECT a.id FROM AnioAcademico a
                    WHERE a.anio = s.anioAcademico AND a.estado = :cerrado)
            ORDER BY s.anioAcademico, s.grado, s.nombre
            """)
    java.util.List<Seccion> buscarActivasPorAulaFueraDeAnios(
            @Param("aulaId") Long aulaId, @Param("cerrado") EstadoAnioAcademico cerrado);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Seccion s WHERE s.id = :id")
    Optional<Seccion> bloquearPorId(@Param("id") Long id);

    Page<Seccion> findByActivoTrue(Pageable pageable);

    @Query(
            """
            SELECT s FROM Seccion s
            WHERE (:activo IS NULL OR s.activo = :activo)
              AND (:anioAcademico IS NULL OR s.anioAcademico = :anioAcademico)
              AND (:grado IS NULL OR s.grado = :grado)
            """)
    Page<Seccion> buscarConFiltros(
            @Param("activo") Boolean activo,
            @Param("anioAcademico") Integer anioAcademico,
            @Param("grado") Integer grado,
            Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Seccion s WHERE s.id = :id AND s.activo = true")
    Optional<Seccion> buscarActivaParaActualizar(@Param("id") Long id);
}
