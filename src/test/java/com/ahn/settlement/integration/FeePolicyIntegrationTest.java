package com.ahn.settlement.integration;

import com.ahn.settlement.entity.FeePolicyHistory;
import com.ahn.settlement.repository.FeePolicyHistoryRepository;
import com.ahn.settlement.service.FeePolicyService;
import com.ahn.settlement.exception.DuplicateResourceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Transactional
class FeePolicyIntegrationTest {

    @Autowired FeePolicyService feePolicyService;
    @Autowired FeePolicyHistoryRepository feePolicyHistoryRepository;

    @BeforeEach
    void setUp() {
        feePolicyHistoryRepository.deleteAllInBatch();
        feePolicyHistoryRepository.save(
            new FeePolicyHistory("fp-init", 20L, LocalDate.of(2020, 1, 1)));
    }

    @Test
    void getRateFor_시드_데이터_기준_20퍼센트_반환() {
        long rate = feePolicyService.getRateFor(LocalDate.of(2025, 3, 1));
        assertThat(rate).isEqualTo(20L);
    }

    @Test
    void getRateFor_변경_후_이전_날짜는_구_수수료율_반환() {
        feePolicyHistoryRepository.save(
            new FeePolicyHistory("fp-new", 10L, LocalDate.of(2025, 4, 1)));

        assertThat(feePolicyService.getRateFor(LocalDate.of(2025, 3, 31))).isEqualTo(20L);
        assertThat(feePolicyService.getRateFor(LocalDate.of(2025, 4, 1))).isEqualTo(10L);
        assertThat(feePolicyService.getRateFor(LocalDate.of(2025, 5, 1))).isEqualTo(10L);
    }

    @Test
    void add_중복_effectiveFrom_시_DuplicateResourceException() {
        assertThatThrownBy(() -> feePolicyService.add(15L, LocalDate.of(2020, 1, 1)))
            .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void getHistory_내림차순_반환() {
        feePolicyHistoryRepository.save(
            new FeePolicyHistory("fp-b", 15L, LocalDate.of(2025, 4, 1)));

        List<FeePolicyHistory> history = feePolicyService.getHistory();
        assertThat(history).hasSize(2);
        assertThat(history.get(0).getEffectiveFrom()).isEqualTo(LocalDate.of(2025, 4, 1));
        assertThat(history.get(1).getEffectiveFrom()).isEqualTo(LocalDate.of(2020, 1, 1));
    }
}
