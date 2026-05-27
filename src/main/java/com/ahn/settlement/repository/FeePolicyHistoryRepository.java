package com.ahn.settlement.repository;

import com.ahn.settlement.entity.FeePolicyHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FeePolicyHistoryRepository extends JpaRepository<FeePolicyHistory, String> {

    Optional<FeePolicyHistory> findTopByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(LocalDate date);

    List<FeePolicyHistory> findAllByOrderByEffectiveFromDesc();

    boolean existsByEffectiveFrom(LocalDate effectiveFrom);
}
