package com.uxbiodash.backend.repository;

import com.uxbiodash.backend.domain.SesionEvaluacion;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SesionEvaluacionRepository extends JpaRepository<SesionEvaluacion, Long> {

    List<SesionEvaluacion> findByEstimuloIdOrderByCodigoSujetoAscIdAsc(Long estimuloId);

    List<SesionEvaluacion> findByProyectoIdOrderByCodigoSujetoAscIdAsc(Long proyectoId);

    /** Borra sesiones previas de los mismos sujetos (las tablas hijas se borran en cascada en la BD). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from SesionEvaluacion s where s.estimulo.id = :estimuloId and s.codigoSujeto in :codigos")
    int borrarPorSujetos(@Param("estimuloId") Long estimuloId, @Param("codigos") Collection<String> codigos);

    @Query("select count(distinct s.codigoSujeto) from SesionEvaluacion s where s.proyecto.id = :proyectoId")
    long contarSujetos(@Param("proyectoId") Long proyectoId);

    @Query("select avg(s.duracionTotalMs) from SesionEvaluacion s where s.proyecto.id = :proyectoId")
    Double duracionMediaProyecto(@Param("proyectoId") Long proyectoId);
}
