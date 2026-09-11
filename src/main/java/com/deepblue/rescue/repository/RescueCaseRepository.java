package com.deepblue.rescue.repository;

import com.deepblue.rescue.domain.RescueCase;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RescueCaseRepository extends JpaRepository<RescueCase, Long> {
}