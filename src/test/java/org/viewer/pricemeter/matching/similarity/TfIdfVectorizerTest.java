package org.viewer.pricemeter.matching.similarity;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class TfIdfVectorizerTest {

    private final TfIdfVectorizer vectorizer = TfIdfVectorizer.fit(List.of(
            "rowenta ro2913ea",
            "rowenta swift power cyclonic ro2913ea",
            "rowenta ro4b25ea",
            "rowenta ro4b75ea"));

    @Test
    void identicalTitlesHaveCosineOne() {
        assertThat(cosine("rowenta ro2913ea", "rowenta ro2913ea")).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void titlesWithoutCommonGramsHaveCosineZero() {
        assertThat(cosine("rowenta ro2913ea", "lg f2y2hs5we")).isZero();
    }

    @Test
    void sameCodeWeighsMoreThanSameBrand() {
        // n-грами бренду є в усіх назвах і мають малу вагу, n-грами коду — рідкісні
        assertThat(cosine("rowenta ro2913ea", "rowenta swift power cyclonic ro2913ea"))
                .isGreaterThan(cosine("rowenta ro2913ea", "rowenta ro4b25ea"));
    }

    @Test
    void splitCodeStaysSimilar() {
        assertThat(cosine("whirlpool wrsb 7259 wb ua", "whirlpool wrsb7259wbua")).isGreaterThan(0.5);
    }

    @Test
    void blankTitleGivesEmptyVector() {
        assertThat(vectorizer.transform(" ")).isEqualTo(SparseVector.EMPTY);
        assertThat(cosine("", "rowenta ro2913ea")).isZero();
    }

    private double cosine(String a, String b) {
        return vectorizer.transform(a).cosine(vectorizer.transform(b));
    }

}
