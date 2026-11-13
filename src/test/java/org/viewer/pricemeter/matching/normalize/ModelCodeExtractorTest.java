package org.viewer.pricemeter.matching.normalize;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Назви — реальні, з БД (Moyo, Цитрус, Епіцентр, Eldorado).
 */
class ModelCodeExtractorTest {

    private final BrandExtractor brands = new BrandExtractor();
    private final ModelCodeExtractor extractor = new ModelCodeExtractor(brands);

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            // код одним токеном
            "Пральна машина LG F2Y2NS3WE                                    | LG               | F2Y2NS3WE      | F2Y2NS3WE",
            "Холодильник LG GC-B459SLCL                                     | LG               | GCB459SLCL     | GCB459SLCL",
            "Пральна машина Dreame S8                                       | Dreame           | S8             | S8",
            "Винна шафа Philco PW8F                                         | Philco           | PW8F           | PW8F",
            "Посудомийна машина вбудована EDLER EBD-6030PDHS                | EDLER            | EBD6030PDHS    | EBD6030PDHS",
            // регіональний суфікс
            "Пральна машина SAMSUNG WW90DG6G94LKUA                          | SAMSUNG          | WW90DG6G94LKUA | WW90DG6G94LK",
            "Мікрохвильова піч SAMSUNG MS20A3010AL/UA SOLO                  | Samsung          | MS20A3010ALUA  | MS20A3010AL",
            "Пральна машина Samsung WW60A3120BE/LE                          | Samsung          | WW60A3120BELE  | WW60A3120BE",
            "Посудомийна машина вбудована SAMSUNG DW50R4050BB/WT            | SAMSUNG          | DW50R4050BBWT  | DW50R4050BB",
            "Пральна машина Midea MFE06W60/W-UA                             | Midea            | MFE06W60WUA    | MFE06W60W",
            "Пилосос без мішка Samsung VC07M31C0HG/UK Bagless               | Samsung          | VC07M31C0HGUK  | VC07M31C0HG",
            // код, розділений пробілами
            "Пральна машина WHIRLPOOL WRSB 7259 WB UA                       | WHIRLPOOL        | WRSB7259WBUA   | WRSB7259WB",
            "Пральна машина WHIRLPOOL FFB 10469 BV UA                       | WHIRLPOOL        | FFB10469BVUA   | FFB10469BV",
            "Посудомийна машина вбудована HOTPOINT-ARISTON HI 5010 C        | HOTPOINT-ARISTON | HI5010C        | HI5010C",
            "Мікрохвильова піч PRIME TECHNICS PMW 20764 KB                  | PRIME TECHNICS   | PMW20764KB     | PMW20764KB",
            // серія, описові слова й колір поруч із кодом
            "Пилосос без мішка Philips 7000 series FC9745/09                | Philips          | FC974509       | FC974509",
            "Акумуляторний пилосос Philips Series 5000 XC5141/01            | Philips          | XC514101       | XC514101",
            "Акумуляторний пилосос Rowenta X-Force Flex 15.60 Aqua RH99G1WO | Rowenta          | RH99G1WO       | RH99G1WO",
            "Пилосос Rowenta Compact Power XXL Animal Kit RO4B50EA beige    | Rowenta          | RO4B50EA       | RO4B50EA",
            "Пилосос ROWENTA GREEN FORCE CYCLONIC EFFITECH+ RO7C66EA        | Rowenta          | RO7C66EA       | RO7C66EA",
            "Пилосос BOSCH BGBS2BA1P BAGGED                                 | BOSCH            | BGBS2BA1P      | BGBS2BA1P",
            "Двухкамерный холодильник Gorenje RK4182PS4                     | GORENJE          | RK4182PS4      | RK4182PS4",
    })
    void extractsCodeFromTitle(String title, String brandRaw, String full, String base) {
        String brand = brands.canonical(brandRaw);

        ModelCode code = extractor.extract(title, null, brand).orElseThrow();

        assertThat(code.full()).isEqualTo(full);
        assertThat(code.base()).isEqualTo(base);
        assertThat(code.source()).isEqualTo(ModelCode.Source.TITLE);
    }

    @Test
    void differentlyWrittenCodesOfOneModelAreEqual() {
        String spaced = extractor.extract("Пральна машина Samsung WW80T4040 CE1/UA", null, "SAMSUNG")
                .orElseThrow().full();
        String joined = extractor.extract("Samsung WW80T4040CE1UA", null, "SAMSUNG").orElseThrow().full();
        String eldorado = extractor.extract("Пральна машина WHIRLPOOL WRSB 7259 WB UA", null, "WHIRLPOOL")
                .orElseThrow().full();
        String other = extractor.extract("Пральна машина Whirlpool WRSB7259WBUA", null, "WHIRLPOOL")
                .orElseThrow().full();

        assertThat(spaced).isEqualTo(joined).isEqualTo("WW80T4040CE1UA");
        assertThat(eldorado).isEqualTo(other).isEqualTo("WRSB7259WBUA");
    }

    @Test
    void replacesCyrillicLookalikesInCode() {
        // «Т», «С», «Е» — кириличні
        ModelCode code = extractor.extract("Пральна машина Samsung WW80Т4040СЕ1UA", null, "SAMSUNG").orElseThrow();

        assertThat(code.full()).isEqualTo("WW80T4040CE1UA");
    }

    @Test
    void prefersMpnAndDropsShopSuffix() {
        ModelCode code = extractor.extract("Посудомийна машина ARDESTO DWMF-G605W_Sale", "DWMF-G605W_Sale", "ARDESTO")
                .orElseThrow();

        assertThat(code).isEqualTo(new ModelCode("DWMFG605W", "DWMFG605W", ModelCode.Source.MPN));
    }

    @Test
    void stripsRegionFromMpn() {
        ModelCode code = extractor.extract("Прально-сушильна машина Samsung WD11DB7B85GWUA", "WD11DB7B85GWUA",
                "SAMSUNG").orElseThrow();

        assertThat(code.base()).isEqualTo("WD11DB7B85GW");
    }

    @Test
    void brandIsNotTakenAsCodeFragment() {
        // без вирізання бренду «LG» приєднався б до коду
        ModelCode code = extractor.extract("Пральна машина LG 5010", null, "LG").orElseThrow();

        assertThat(code.full()).isEqualTo("5010");
    }

    @Test
    void returnsEmptyWithoutCode() {
        assertThat(extractor.extract("Пральна машина Samsung", null, "SAMSUNG")).isEmpty();
        assertThat(extractor.extract(null, " ", null)).isEmpty();
    }

}
