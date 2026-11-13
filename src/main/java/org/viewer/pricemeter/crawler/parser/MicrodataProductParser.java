package org.viewer.pricemeter.crawler.parser;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Вилучає дані товару з мікророзмітки Schema.org ({@code itemscope} / {@code itemprop}).
 */
@Component
public class MicrodataProductParser {

    private static final Set<String> URL_BY_HREF = Set.of("a", "area", "link");
    private static final Set<String> URL_BY_SRC = Set.of("audio", "embed", "iframe", "img", "source", "track", "video");
    private static final Set<String> VALUE_ATTR = Set.of("data", "meter");

    /**
     * @param html    HTML сторінки товару
     * @param pageUri адреса сторінки — для розв'язання відносних посилань
     * @return дані головного товару сторінки або порожньо, якщо мікророзмітки Product немає
     */
    public Optional<ParsedProduct> parse(String html, URI pageUri) {
        return parse(Jsoup.parse(html, pageUri.toString()), pageUri);
    }

    /**
     * Варіант для вже розібраного документа — адаптер може додатково читати з нього власні поля.
     */
    public Optional<ParsedProduct> parse(Document document, URI pageUri) {
        List<Item> products = document.select("[itemscope][itemtype]").stream()
                .filter(e -> hasType(e.attr("itemtype"), "product"))
                .map(MicrodataProductParser::readItem)
                .toList();
        // Головний товар має пропозицію з ціною; блоки «схожі товари» — зазвичай ні.
        return products.stream()
                .filter(p -> selectOffer(p) != null && priceOf(selectOffer(p)) != null)
                .findFirst()
                .or(() -> products.stream().findFirst())
                .map(product -> toParsedProduct(product, pageUri));
    }

    private static ParsedProduct toParsedProduct(Item product, URI pageUri) {
        Item offer = selectOffer(product);

        String gtin = firstText(product, SchemaOrg.GTIN_FIELDS);
        if (gtin == null && offer != null) {
            gtin = firstText(offer, SchemaOrg.GTIN_FIELDS);
        }
        String url = offer != null ? offer.text("url") : null;
        if (url == null) {
            url = product.text("url");
        }
        String brand = nameOf(product.first("brand"));
        if (brand == null) {
            brand = nameOf(product.first("manufacturer"));
        }

        return new ParsedProduct(
                product.text("name"),
                brand,
                gtin,
                product.text("sku"),
                product.text("mpn"),
                offer != null ? priceOf(offer) : null,
                offer != null ? oldPriceOf(offer) : null,
                offer != null ? offer.text("priceCurrency") : null,
                offer != null ? SchemaOrg.availability(offer.text("availability")) : Availability.UNKNOWN,
                resolve(pageUri, url),
                specsOf(product));
    }

    /**
     * Обирає пропозицію: першу в наявності з ціною, інакше першу з ціною, інакше першу.
     */
    private static Item selectOffer(Item product) {
        List<Item> offers = new ArrayList<>();
        for (Object value : product.all("offers")) {
            if (value instanceof Item offer) {
                offers.add(offer);
                // AggregateOffer може містити вкладені пропозиції окремих продавців
                offer.all("offers").stream().filter(Item.class::isInstance).map(Item.class::cast).forEach(offers::add);
            }
        }
        return offers.stream()
                .filter(o -> priceOf(o) != null && SchemaOrg.availability(o.text("availability")) == Availability.IN_STOCK)
                .findFirst()
                .or(() -> offers.stream().filter(o -> priceOf(o) != null).findFirst())
                .or(() -> offers.stream().findFirst())
                .orElse(null);
    }

    private static BigDecimal priceOf(Item offer) {
        BigDecimal price = PriceText.parse(offer.text("price"));
        if (price == null) {
            price = PriceText.parse(offer.text("lowPrice"));
        }
        if (price == null) {
            for (Item spec : offer.items("priceSpecification")) {
                if (!isOldPriceSpec(spec)) {
                    price = PriceText.parse(spec.text("price"));
                    if (price != null) {
                        break;
                    }
                }
            }
        }
        return price;
    }

    private static BigDecimal oldPriceOf(Item offer) {
        for (Item spec : offer.items("priceSpecification")) {
            if (isOldPriceSpec(spec)) {
                BigDecimal value = PriceText.parse(spec.text("price"));
                if (value != null) {
                    return value;
                }
            }
        }
        return null;
    }

    private static boolean isOldPriceSpec(Item spec) {
        String type = SchemaOrg.term(spec.text("priceType"));
        return type != null && SchemaOrg.OLD_PRICE_TYPES.contains(type);
    }

    private static Map<String, String> specsOf(Item product) {
        Map<String, String> specs = new LinkedHashMap<>();
        for (Item property : product.items("additionalProperty")) {
            String name = property.text("name");
            String value = property.text("value");
            if (name != null && value != null) {
                specs.putIfAbsent(name, value);
            }
        }
        return specs;
    }

    private static String nameOf(Object value) {
        if (value instanceof Item item) {
            return item.text("name");
        }
        return value instanceof String s ? s : null;
    }

    private static String firstText(Item item, List<String> names) {
        for (String name : names) {
            String value = item.text(name);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    /** {@code itemtype} може містити кілька URL через пробіл. */
    private static boolean hasType(String itemtype, String type) {
        for (String t : itemtype.strip().split("\\s+")) {
            if (type.equals(SchemaOrg.term(t))) {
                return true;
            }
        }
        return false;
    }

    private static String resolve(URI base, String url) {
        if (url == null) {
            return null;
        }
        try {
            return base.resolve(url).toString();
        } catch (IllegalArgumentException e) {
            return url;
        }
    }

    // ------------------------------------------------- читання мікророзмітки

    private static Item readItem(Element scope) {
        Map<String, List<Object>> properties = new LinkedHashMap<>();
        collectProperties(scope, properties);
        return new Item(properties);
    }

    /**
     * Збирає властивості елемента: нащадків з {@code itemprop}, не заходячи у вкладені {@code itemscope}
     * (їхні властивості належать вкладеному об'єкту).
     */
    private static void collectProperties(Element parent, Map<String, List<Object>> properties) {
        for (Element child : parent.children()) {
            if (child.hasAttr("itemprop")) {
                Object value = child.hasAttr("itemscope") ? readItem(child) : propertyValue(child);
                if (value != null) {
                    for (String name : child.attr("itemprop").strip().split("\\s+")) {
                        properties.computeIfAbsent(name, k -> new ArrayList<>()).add(value);
                    }
                }
            }
            if (!child.hasAttr("itemscope")) {
                collectProperties(child, properties);
            }
        }
    }

    /** Значення властивості за правилами HTML microdata. */
    private static String propertyValue(Element e) {
        String tag = e.normalName();
        String raw;
        if (e.hasAttr("content")) {
            raw = e.attr("content");
        } else if (URL_BY_HREF.contains(tag)) {
            raw = e.absUrl("href").isEmpty() ? e.attr("href") : e.absUrl("href");
        } else if (URL_BY_SRC.contains(tag)) {
            raw = e.absUrl("src").isEmpty() ? e.attr("src") : e.absUrl("src");
        } else if (VALUE_ATTR.contains(tag)) {
            raw = e.attr("value");
        } else if (tag.equals("time") && e.hasAttr("datetime")) {
            raw = e.attr("datetime");
        } else {
            raw = e.text();
        }
        String value = raw.replaceAll("\\s+", " ").strip();
        return value.isEmpty() ? null : value;
    }

    /** Об'єкт мікророзмітки: властивість → значення (рядок або вкладений {@link Item}). */
    private record Item(Map<String, List<Object>> properties) {

        List<Object> all(String name) {
            return properties.getOrDefault(name, List.of());
        }

        Object first(String name) {
            List<Object> values = all(name);
            return values.isEmpty() ? null : values.getFirst();
        }

        String text(String name) {
            for (Object value : all(name)) {
                if (value instanceof String s) {
                    return s;
                }
            }
            return null;
        }

        List<Item> items(String name) {
            return all(name).stream().filter(Item.class::isInstance).map(Item.class::cast).toList();
        }

    }

}
