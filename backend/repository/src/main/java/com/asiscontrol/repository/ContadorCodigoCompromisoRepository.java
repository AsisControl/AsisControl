package com.asiscontrol.repository;

import com.asiscontrol.entity.ContadorCodigoCompromiso;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ContadorCodigoCompromisoRepository
        extends JpaRepository<ContadorCodigoCompromiso, Integer> {
    // Inicialización idempotente compatible con MySQL y H2 MODE=MySQL.
    @Modifying
    @Query(value = """
            INSERT INTO ContadorCodigoCompromiso (id_contador, ultimo_valor)
            VALUES (1, 0)
            ON DUPLICATE KEY UPDATE id_contador = id_contador
            """, nativeQuery = true)
    void initializeCounter();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ContadorCodigoCompromiso c where c.id = 1")
    ContadorCodigoCompromiso lockCounter();
}
