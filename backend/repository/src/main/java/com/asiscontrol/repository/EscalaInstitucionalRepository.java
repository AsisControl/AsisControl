package com.asiscontrol.repository;

import com.asiscontrol.entity.EscalaInstitucional;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EscalaInstitucionalRepository extends JpaRepository<EscalaInstitucional, Long> {
    List<EscalaInstitucional> findAllByOrderByNumeroVersionDesc();
    Optional<EscalaInstitucional> findFirstByOrderByNumeroVersionDesc();
    Optional<EscalaInstitucional> findByRanura(EscalaInstitucional.Ranura ranura);
}
