package com.ahn.settlement.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SettlementCalculatorTest {

    @Test
    void 필수시나리오_creator1_2025_03_정산() {
        // sale-1(50000) + sale-2(50000) + sale-3(80000) + sale-4(80000) = 260000
        // cancel-1(80000) + cancel-2(30000) = 110000
        SettlementResult result = SettlementCalculator.calculate(260_000L, 110_000L);

        assertThat(result.totalSalesAmount()).isEqualTo(260_000L);
        assertThat(result.totalRefundAmount()).isEqualTo(110_000L);
        assertThat(result.netSalesAmount()).isEqualTo(150_000L);
        assertThat(result.platformFee()).isEqualTo(30_000L);
        assertThat(result.payoutAmount()).isEqualTo(120_000L);
    }

    @Test
    void 판매없고_환불없으면_전부_0() {
        SettlementResult result = SettlementCalculator.calculate(0L, 0L);

        assertThat(result.netSalesAmount()).isZero();
        assertThat(result.platformFee()).isZero();
        assertThat(result.payoutAmount()).isZero();
    }

    @Test
    void 수수료율_20퍼센트_정확히_적용() {
        SettlementResult result = SettlementCalculator.calculate(100_000L, 0L);

        assertThat(result.platformFee()).isEqualTo(20_000L);
        assertThat(result.payoutAmount()).isEqualTo(80_000L);
    }

    @Test
    void 부분환불_순매출에_잔여금액_남음() {
        // sale-4(80000) 부분 환불 cancel-2(30000) → 순매출 50000
        SettlementResult result = SettlementCalculator.calculate(80_000L, 30_000L);

        assertThat(result.netSalesAmount()).isEqualTo(50_000L);
        assertThat(result.platformFee()).isEqualTo(10_000L);
        assertThat(result.payoutAmount()).isEqualTo(40_000L);
    }
}
