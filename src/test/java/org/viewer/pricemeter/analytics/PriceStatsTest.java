package org.viewer.pricemeter.analytics;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PriceStatsTest {

    @Test
    void medianOfOddCountIsMiddleValue() {
        assertThat(PriceStats.median(prices("17999", "15999", "16499"))).isEqualByComparingTo("16499");
    }

    @Test
    void medianOfEvenCountIsMeanOfMiddleValues() {
        assertThat(PriceStats.median(prices("15999", "16998", "18000", "14000"))).isEqualByComparingTo("16498.50");
    }

    @Test
    void medianSkipsMissingPrices() {
        assertThat(PriceStats.median(Arrays.asList(new BigDecimal("100"), null))).isEqualByComparingTo("100");
        assertThat(PriceStats.median(List.of())).isNull();
    }

    @Test
    void deviationIsRoundedPercentOfReference() {
        assertThat(PriceStats.deviationPercent(new BigDecimal("9500"), new BigDecimal("10000"))).isEqualTo(-5);
        assertThat(PriceStats.deviationPercent(new BigDecimal("10999"), new BigDecimal("10000"))).isEqualTo(10);
        assertThat(PriceStats.deviationPercent(new BigDecimal("10049"), new BigDecimal("10000"))).isZero();
        assertThat(PriceStats.deviationPercent(new BigDecimal("10000"), null)).isNull();
    }

    private static List<BigDecimal> prices(String... values) {
        return Arrays.stream(values).map(BigDecimal::new).toList();
    }

}
