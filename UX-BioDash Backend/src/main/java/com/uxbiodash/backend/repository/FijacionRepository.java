package com.uxbiodash.backend.repository;

import com.uxbiodash.backend.domain.Fijacion;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FijacionRepository extends JpaRepository<Fijacion, Long> {
    List<Fijacion> findBySesionIdOrderByOrdenSecuenciaAsc(Long sesionId);

    List<Fijacion> findBySesionEstimuloIdOrderBySesionIdAscOrdenSecuenciaAsc(Long estimuloId);

    List<Fijacion> findBySesionIdInOrderBySesionIdAscOrdenSecuenciaAsc(Collection<Long> sesionIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Fijacion f set f.aoi = null where f.aoi.id = :aoiId")
    int desasignarAoi(@Param("aoiId") Long aoiId);
}
