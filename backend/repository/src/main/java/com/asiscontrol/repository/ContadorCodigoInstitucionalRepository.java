package com.asiscontrol.repository;

import com.asiscontrol.entity.ContadorCodigoInstitucional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContadorCodigoInstitucionalRepository
        extends JpaRepository<ContadorCodigoInstitucional, ContadorCodigoInstitucional.Clave> {

    // MySQL y H2 en MODE=MySQL: crea la fila del año sin fallar si otra transacción ya la creó.
    @Modifying
    @Query(
            value =
                    """
                    INSERT INTO ContadorCodigoInstitucional (prefijo, anio, ultimo_valor)
                    VALUES (:prefijo, :anio, 0)
                    ON DUPLICATE KEY UPDATE prefijo = prefijo
                    """,
            nativeQuery = true)
    void inicializar(@Param("prefijo") String prefijo, @Param("anio") int anio);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ContadorCodigoInstitucional c where c.prefijo = :prefijo and c.anio = :anio")
    ContadorCodigoInstitucional bloquear(@Param("prefijo") String prefijo, @Param("anio") int anio);
}
