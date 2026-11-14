package com.asiscontrol.repository;
import com.asiscontrol.entity.TarifaPension;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface TarifaPensionRepository extends JpaRepository<TarifaPension,Long> {
    Optional<TarifaPension> findByConceptoIdAndAnioAcademicoAndGrado(Long conceptoId,int anio,int grado);
    List<TarifaPension> findAllByOrderByAnioAcademicoDescGradoAsc();
}
