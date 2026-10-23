package com.asiscontrol.repository;

import com.asiscontrol.entity.RegistroAuditoria;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface RegistroAuditoriaRepository
        extends JpaRepository<RegistroAuditoria, Long>, JpaSpecificationExecutor<RegistroAuditoria> {
    java.util.List<RegistroAuditoria> findByEntidadAndRegistroIdOrderByFechaHoraAsc(
            String entidad, String registroId);

    Page<RegistroAuditoria> findAllByOrderByFechaHoraDesc(Pageable pageable);

    /** Modulos que ya tienen registros, para las opciones del filtro. */
    @Query("SELECT DISTINCT r.modulo FROM RegistroAuditoria r ORDER BY r.modulo")
    java.util.List<String> findModulos();

    /** Acciones que ya tienen registros, para las opciones del filtro. */
    @Query("SELECT DISTINCT r.accion FROM RegistroAuditoria r ORDER BY r.accion")
    java.util.List<String> findAcciones();
}
