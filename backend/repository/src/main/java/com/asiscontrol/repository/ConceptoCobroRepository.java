package com.asiscontrol.repository;
import com.asiscontrol.entity.ConceptoCobro;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface ConceptoCobroRepository extends JpaRepository<ConceptoCobro,Long> {
    Optional<ConceptoCobro> findByIdAndActivoTrue(Long id);
    Optional<ConceptoCobro> findByCodigoIgnoreCase(String codigo);
    List<ConceptoCobro> findAllByOrderByNombreAsc();
    Optional<ConceptoCobro> findByEsPensionTrueAndActivoTrue();
}
