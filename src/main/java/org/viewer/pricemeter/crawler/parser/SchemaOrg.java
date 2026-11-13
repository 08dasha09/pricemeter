package org.viewer.pricemeter.crawler.parser;

import java.util.List;
import java.util.Locale;

/**
 * Спільні правила словника Schema.org для парсерів JSON-LD та мікророзмітки.
 */
public final class SchemaOrg {

    /** Типи {@code priceSpecification}, що означають ціну без знижки. */
    public static final List<String> OLD_PRICE_TYPES = List.of("strikethroughprice", "listprice");

    /** Поля зі штрихкодом у порядку пріоритету. */
    public static final List<String> GTIN_FIELDS = List.of("gtin13", "gtin", "gtin12", "gtin14", "gtin8");

    private SchemaOrg() {
    }

    /**
     * Коротка назва терміна в нижньому регістрі: {@code "https://schema.org/InStock"} → {@code "instock"},
     * {@code "schema:Product"} → {@code "product"}.
     */
    public static String term(String value) {
        if (value == null) {
            return null;
        }
        String stripped = value.strip();
        int cut = Math.max(stripped.lastIndexOf('/'), stripped.lastIndexOf(':'));
        String term = stripped.substring(cut + 1).toLowerCase(Locale.ROOT);
        return term.isEmpty() ? null : term;
    }

    public static Availability availability(String value) {
        String term = term(value);
        if (term == null) {
            return Availability.UNKNOWN;
        }
        return switch (term) {
            // BackOrder / PreOrder — товар можна замовити, ціна дійсна
            case "instock", "limitedavailability", "onlineonly", "instoreonly", "preorder", "presale", "backorder" ->
                    Availability.IN_STOCK;
            case "outofstock", "soldout", "discontinued" -> Availability.OUT_OF_STOCK;
            default -> Availability.UNKNOWN;
        };
    }

}
