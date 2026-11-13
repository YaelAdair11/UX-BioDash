package com.uxbiodash.backend.repository;

import com.uxbiodash.backend.domain.Estimulo;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EstimuloRepository extends JpaRepository<Estimulo, Long> {
    List<Estimulo> findByProyectoIdOrderByIdAsc(Long proyectoId);

    /** Estimulo principal del proyecto: el ultimo subido. */
    Optional<Estimulo> findFirstByProyectoIdOrderByIdDesc(Long proyectoId);
}
