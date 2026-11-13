package com.uxbiodash.backend.repository;

import com.uxbiodash.backend.domain.Sacada;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SacadaRepository extends JpaRepository<Sacada, Long> {

    @Query("select s from Sacada s join fetch s.fijacionOrigen join fetch s.fijacionDestino "
            + "where s.sesion.id = :sesionId order by s.fijacionOrigen.ordenSecuencia")
    List<Sacada> buscarPorSesion(@Param("sesionId") Long sesionId);

    @Query("select s from Sacada s join fetch s.fijacionOrigen join fetch s.fijacionDestino "
            + "where s.sesion.id in :sesionIds")
    List<Sacada> buscarPorSesiones(@Param("sesionIds") Collection<Long> sesionIds);
}
