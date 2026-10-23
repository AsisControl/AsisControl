package com.asiscontrol.repository;

import com.asiscontrol.entity.TokenRenovacion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface TokenRenovacionRepository extends JpaRepository<TokenRenovacion, Long> {
    Optional<TokenRenovacion> findByTokenHash(String tokenHash);

    @org.springframework.data.jpa.repository.Query(
            "select t.sesion.id from TokenRenovacion t where t.tokenHash = :hash")
    Optional<Long> findSesionIdByHash(@org.springframework.data.repository.query.Param("hash") String hash);
}
