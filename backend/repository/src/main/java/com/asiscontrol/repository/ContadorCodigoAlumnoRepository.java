package com.asiscontrol.repository;

import com.asiscontrol.entity.ContadorCodigoAlumno;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ContadorCodigoAlumnoRepository
        extends JpaRepository<ContadorCodigoAlumno, Integer> {
    // MySQL y H2 en MODE=MySQL: inicialización idempotente dentro de la transacción.
    @Modifying
    @Query(
            value =
                    """
                    INSERT INTO ContadorCodigoAlumno (id_contador, ultimo_valor)
                    VALUES (1, 0)
                    ON DUPLICATE KEY UPDATE id_contador = id_contador
                    """,
            nativeQuery = true)
    void initializeCounter();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ContadorCodigoAlumno c where c.id = 1")
    ContadorCodigoAlumno lockCounter();
}
