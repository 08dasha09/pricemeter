package org.viewer.pricemeter.crawler.parser;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class PriceTextTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "15999            | 15999",
            "15999.00         | 15999.00",
            "15 999 ₴         | 15999",
            "15 999 грн       | 15999",
            "15 999,50 грн | 15999.50",
            "15,999.50        | 15999.50",
            "15.999,5         | 15999.5",
            "1.299.000        | 1299000",
            "від 8 499 ₴      | 8499",
    })
    void parsesPrices(String text, String expected) {
        assertThat(PriceText.parse(text)).isEqualByComparingTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"немає в наявності", "0", "0,00", "₴"})
    void returnsNullWithoutPositivePrice(String text) {
        assertThat(PriceText.parse(text)).isNull();
    }

}
