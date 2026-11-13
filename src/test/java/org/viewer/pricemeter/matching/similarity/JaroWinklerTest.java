package org.viewer.pricemeter.matching.similarity;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class JaroWinklerTest {

    @ParameterizedTest
    @CsvSource({
            // класичні приклади з літератури
            "MARTHA,    MARHTA,    0.961",
            "DIXON,     DICKSONX,  0.813",
            "DWAYNE,    DUANE,     0.840",
            "F2Y2HS5WE, F2Y2HS5WE, 1.0",
            "ABC,       XYZ,       0.0",
            "'',        ABC,       0.0",
    })
    void computesSimilarity(String a, String b, double expected) {
        assertThat(JaroWinkler.similarity(a, b)).isCloseTo(expected, within(0.001));
        assertThat(JaroWinkler.similarity(b, a)).isCloseTo(expected, within(0.001));
    }

}
