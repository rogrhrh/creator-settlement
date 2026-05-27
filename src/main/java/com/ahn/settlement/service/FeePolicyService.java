package com.ahn.settlement.service;

import com.ahn.settlement.entity.FeePolicyHistory;
import com.ahn.settlement.exception.DuplicateResourceException;
import com.ahn.settlement.repository.FeePolicyHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FeePolicyService {

    private final FeePolicyHistoryRepository feePolicyHistoryRepository;

    public long getRateFor(LocalDate date) {
        return feePolicyHistoryRepository
            .findTopByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(date)
            .map(FeePolicyHistory::getRatePercent)
            .orElseThrow(() -> new IllegalStateException("적용 가능한 수수료율이 없습니다: " + date));
    }

    public List<FeePolicyHistory> getHistory() {
        return feePolicyHistoryRepository.findAllByOrderByEffectiveFromDesc();
    }

    @Transactional
    public FeePolicyHistory add(Long ratePercent, LocalDate effectiveFrom) {
        if (feePolicyHistoryRepository.existsByEffectiveFrom(effectiveFrom)) {
            throw new DuplicateResourceException("이미 등록된 수수료율 날짜입니다: " + effectiveFrom);
        }
        return feePolicyHistoryRepository.save(
            new FeePolicyHistory(UUID.randomUUID().toString(), ratePercent, effectiveFrom));
    }
}
