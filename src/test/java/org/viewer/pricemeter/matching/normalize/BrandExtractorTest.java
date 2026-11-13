package org.viewer.pricemeter.matching.normalize;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class BrandExtractorTest {

    private final BrandExtractor extractor = new BrandExtractor();

    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
            "Samsung          | SAMSUNG",
            "SAMSUNG          | SAMSUNG",
            "HOTPOINT-ARISTON | HOTPOINT ARISTON",
            "Hotpoint         | HOTPOINT ARISTON",
            "Kärcher          | KARCHER",
            "PRIME TECHNICS   | PRIME TECHNICS",
            "De'Longhi        | DE LONGHI",
            // невідомий бренд теж приводиться до канонічної форми
            "Some Brand       | SOME BRAND",
    })
    void canonicalizesShopBrand(String brandRaw, String expected) {
        assertThat(extractor.extract(brandRaw, "будь-яка назва")).contains(expected);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Пральна машина WHIRLPOOL WRSB 7259 WB UA                | WHIRLPOOL",
            "Мікрохвильова піч Prime Technics PMW 20764 KB           | PRIME TECHNICS",
            "Посудомийна машина вбудована HOTPOINT-ARISTON HI 5010 C | HOTPOINT ARISTON",
            "Пральна машина LG F2Y2HS5WE                             | LG",
            "Пилосос господарський Kärcher WD 2 PLUS                 | KARCHER",
    })
    void findsBrandInTitleWhenShopHasNone(String title, String expected) {
        assertThat(extractor.extract(null, title)).contains(expected);
        assertThat(extractor.extract("  ", title)).contains(expected);
    }

    @Test
    void takesFirstBrandInTitle() {
        assertThat(extractor.extract(null, "Пилосос Bosch BGS05X240 (аналог Siemens)")).contains("BOSCH");
    }

    @Test
    void matchesOnlyWholeWords() {
        assertThat(extractor.extract(null, "Холодильник BLG-200")).isEmpty();
        assertThat(extractor.extract(null, null)).isEmpty();
    }

}
