package org.viewer.pricemeter.matching.evaluation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScoreBandTest {

    @Test
    void lowerBoundIsInclusive() {
        assertThat(ScoreBand.of(0)).isEqualTo(ScoreBand.VERY_LOW);
        assertThat(ScoreBand.of(0.299)).isEqualTo(ScoreBand.VERY_LOW);
        assertThat(ScoreBand.of(0.30)).isEqualTo(ScoreBand.LOW);
        assertThat(ScoreBand.of(0.65)).isEqualTo(ScoreBand.REVIEW_LOW);
        assertThat(ScoreBand.of(0.899)).isEqualTo(ScoreBand.REVIEW_HIGH);
        assertThat(ScoreBand.of(0.90)).isEqualTo(ScoreBand.AUTO_LOW);
        assertThat(ScoreBand.of(0.95)).isEqualTo(ScoreBand.AUTO_HIGH);
        assertThat(ScoreBand.of(1.0)).isEqualTo(ScoreBand.AUTO_HIGH);
    }

}
