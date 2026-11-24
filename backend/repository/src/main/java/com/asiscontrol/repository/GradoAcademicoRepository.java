package com.asiscontrol.repository;

import com.asiscontrol.entity.GradoAcademico;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.*;

public interface GradoAcademicoRepository extends JpaRepository<GradoAcademico, Long> {
    List<GradoAcademico> findByAnioAcademicoAnioOrderByNumero(int anio);

    Optional<GradoAcademico> findByAnioAcademicoAnioAndNumero(int anio, int numero);

    @Query("select count(g) > 0 from GradoAcademico g join g.cursosPlan cursoPlan where key(cursoPlan).id = :cursoId")
    boolean existsByCursosPlanId(@Param("cursoId") Long cursoId);

    @Query("""
            select distinct key(cursoPlan).id as cursoId, g.numero as grado
            from GradoAcademico g join g.cursosPlan cursoPlan
            where key(cursoPlan).id in :ids
            """)
    List<GradoDeCurso> gradosDeCursos(@Param("ids") Set<Long> ids);

    interface GradoDeCurso {
        Long getCursoId();
        int getGrado();
    }
}
