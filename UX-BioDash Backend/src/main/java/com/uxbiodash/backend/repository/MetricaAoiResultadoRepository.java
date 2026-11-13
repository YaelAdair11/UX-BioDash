package com.uxbiodash.backend.repository;

import com.uxbiodash.backend.domain.MetricaAoiResultado;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MetricaAoiResultadoRepository extends JpaRepository<MetricaAoiResultado, Long> {
    @Query("select m from MetricaAoiResultado m join fetch m.aoi a join fetch m.sesion s "
            + "where a.estimulo.id = :estimuloId order by s.codigoSujeto, s.id, a.id")
    List<MetricaAoiResultado> buscarPorEstimulo(@Param("estimuloId") Long estimuloId);

    @Query("select m from MetricaAoiResultado m join fetch m.aoi a join fetch m.sesion s "
            + "where s.id = :sesionId order by a.id")
    List<MetricaAoiResultado> buscarPorSesion(@Param("sesionId") Long sesionId);

    @Modifying
    @Query("delete from MetricaAoiResultado m where m.sesion.id in :sesionIds")
    int borrarPorSesiones(@Param("sesionIds") Collection<Long> sesionIds);
}
