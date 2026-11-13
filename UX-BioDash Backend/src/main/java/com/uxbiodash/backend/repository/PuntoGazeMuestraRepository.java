package com.uxbiodash.backend.repository;

import com.uxbiodash.backend.domain.PuntoGazeMuestra;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PuntoGazeMuestraRepository extends JpaRepository<PuntoGazeMuestra, Long> {
    Page<PuntoGazeMuestra> findBySesionIdOrderByTiempoMsAsc(Long sesionId, Pageable pageable);

    long countBySesionId(Long sesionId);

    @org.springframework.data.jpa.repository.Query("select count(m) from PuntoGazeMuestra m")
    long contarTodas();
}
