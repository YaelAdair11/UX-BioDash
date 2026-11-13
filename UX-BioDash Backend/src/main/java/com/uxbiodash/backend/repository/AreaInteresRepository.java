package com.uxbiodash.backend.repository;

import com.uxbiodash.backend.domain.AreaInteres;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AreaInteresRepository extends JpaRepository<AreaInteres, Long> {
    List<AreaInteres> findByEstimuloIdOrderByIdAsc(Long estimuloId);
}
