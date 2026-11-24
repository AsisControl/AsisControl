package com.asiscontrol.repository;

import com.asiscontrol.entity.Curso;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;
import java.util.Set;

public interface CursoRepository extends JpaRepository<Curso, Long> {
    List<Curso> findAllByGradoAcademicoIdAndActivoTrue(Long gradoId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Curso c where c.id in :ids order by c.id")
    List<Curso> bloquearTodos(@Param("ids") Set<Long> ids);

    Optional<Curso> findByIdAndActivoTrue(Long id);

    Optional<Curso> findByCodigoIgnoreCaseAndActivoTrue(String codigo);

    Optional<Curso> findByCodigoIgnoreCase(String codigo);

    boolean existsByGradoAcademicoAnioAcademicoAnio(int anio);

    @Query("""
            SELECT c FROM Curso c
            LEFT JOIN c.gradoAcademico g
            LEFT JOIN g.anioAcademico a
            WHERE c.activo = true
              AND (:busqueda IS NULL
                   OR LOWER(c.codigo) LIKE LOWER(CONCAT('%', :busqueda, '%'))
                   OR LOWER(c.nombre) LIKE LOWER(CONCAT('%', :busqueda, '%')))
              AND (
                  ((:anio IS NULL OR a.anio = :anio) AND (:grado IS NULL OR g.numero = :grado))
                  OR (:incluirPlanes = true AND EXISTS (
                      SELECT plan.id FROM GradoAcademico plan
                      JOIN plan.anioAcademico anioPlan
                      JOIN plan.cursosPlan cursoPlan
                      WHERE KEY(cursoPlan) = c AND plan.activo = true AND anioPlan.activo = true
                        AND (:anio IS NULL OR anioPlan.anio = :anio)
                        AND (:grado IS NULL OR plan.numero = :grado)
                  ))
              )
            """)
    Page<Curso> buscar(
            @Param("busqueda") String busqueda,
            @Param("anio") Integer anio,
            @Param("grado") Integer grado,
            @Param("incluirPlanes") boolean incluirPlanes,
            Pageable pageable);

    @Query("select anio.anio from AnioAcademico anio where anio.activo = true order by anio.anio desc")
    List<Integer> aniosConsultables();
}
