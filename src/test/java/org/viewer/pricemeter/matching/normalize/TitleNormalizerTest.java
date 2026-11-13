package org.viewer.pricemeter.matching.normalize;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import static org.assertj.core.api.Assertions.assertThat;

class TitleNormalizerTest {

    private final TitleNormalizer normalizer = new TitleNormalizer();

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Пральна машина Samsung WW80T4040 CE1/UA                        | samsung ww80t4040 ce1 ua",
            "Прально-сушильна машина LG F2R5FG0W                            | lg f2r5fg0w",
            "Двухкамерный холодильник Gorenje RK4182PS4                     | gorenje rk4182ps4",
            "Посудомийна машина вбудована HOTPOINT-ARISTON HI 5010 C        | hotpoint ariston hi 5010 c",
            "Вбудовувана посудомийна машина Whirlpool WIC3C34PFES           | whirlpool wic3c34pfes",
            "Мікрохвильова піч SAMSUNG MS20A3010AL/UA SOLO                  | samsung ms20a3010al ua solo",
            "Пилосос для сухого прибирання без мішка Rowenta RO4B25EA       | rowenta ro4b25ea",
            "Пилосос Rowenta Compact Power XXL Animal Kit RO4B50EA beige    | rowenta compact power xxl animal kit ro4b50ea",
            "Акумуляторний пилосос Rowenta X-Force Flex 15.60 Aqua RH99G1WO | rowenta x force flex 15.60 aqua rh99g1wo",
            "Пилосос ROWENTA GREEN FORCE CYCLONIC EFFITECH+ RO7C66EA        | rowenta green force cyclonic effitech ro7c66ea",
            "Пилосос господарський Kärcher WD 2.                            | karcher wd 2",
            "Посудомийна машина ARDESTO DWMF-G605W_Sale                     | ardesto dwmf g605w",
            // суміжний товар у категорії лишається відмінним за назвою
            "Винна шафа Philco PW8F                                         | винна шафа philco pw8f",
            // «Т» — кирилична
            "Пральна машина Samsung WW80Т4040                               | samsung ww80t4040",
    })
    void normalizesTitle(String title, String expected) {
        assertThat(normalizer.normalize(title)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void returnsEmptyForBlankTitle(String title) {
        assertThat(normalizer.normalize(title)).isEmpty();
    }

}
