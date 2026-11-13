package com.uxbiodash.backend.repository;

import com.uxbiodash.backend.domain.Proyecto;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProyectoRepository extends JpaRepository<Proyecto, Long> {
    List<Proyecto> findAllByOrderByCreadoEnDesc();
}
