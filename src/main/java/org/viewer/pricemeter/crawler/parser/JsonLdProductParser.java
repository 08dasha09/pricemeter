package org.viewer.pricemeter.crawler.parser;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.springframework.stereotype.Component;
import tools.jackson.core.json.JsonReadFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Вилучає дані товару з розмітки Schema.org/Product у форматі JSON-LD.
 */
@Slf4j
@Component
public class JsonLdProductParser {

    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
            .enable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS)
            .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS)
            .build();

    /**
     * @param html    HTML сторінки товару
     * @param pageUri адреса сторінки — для розв'язання відносних посилань
     * @return дані першого знайденого товару або порожньо, якщо розмітки Product немає
     */
    public Optional<ParsedProduct> parse(String html, URI pageUri) {
        return parse(Jsoup.parse(html, pageUri.toString()), pageUri);
    }

    /**
     * Варіант для вже розібраного документа — адаптер може додатково читати з нього власні поля.
     */
    public Optional<ParsedProduct> parse(Document document, URI pageUri) {
        List<JsonNode> products = new ArrayList<>();
        for (Element script : document.select("script[type~=(?i)application/ld\\+json]")) {
            try {
                collectProducts(JSON.readTree(script.data()), products);
            } catch (RuntimeException e) {
                log.debug("Skipping malformed JSON-LD block on {}: {}", pageUri, e.getMessage());
            }
        }
        // Головний товар сторінки зазвичай має offers; блоки «схожі товари» — часто ні.
        return products.stream()
                .filter(p -> !p.path("offers").isMissingNode())
                .findFirst()
                .or(() -> products.stream().findFirst())
                .map(product -> toParsedProduct(product, pageUri));
    }

    private static void collectProducts(JsonNode node, List<JsonNode> out) {
        if (node.isArray()) {
            node.values().forEach(child -> collectProducts(child, out));
        } else if (node.isObject()) {
            if (hasType(node, "product")) {
                out.add(node);
                return;
            }
            node.properties().forEach(entry -> collectProducts(entry.getValue(), out));
        }
    }

    private static ParsedProduct toParsedProduct(JsonNode product, URI pageUri) {
        JsonNode offer = selectOffer(product.path("offers"));

        String gtin = firstText(product, SchemaOrg.GTIN_FIELDS);
        if (gtin == null && offer != null) {
            gtin = firstText(offer, SchemaOrg.GTIN_FIELDS);
        }
        String url = offer != null ? text(offer.path("url")) : null;
        if (url == null) {
            url = text(product.path("url"));
        }

        return new ParsedProduct(
                text(product.path("name")),
                nameOf(product.path("brand")) != null ? nameOf(product.path("brand")) : nameOf(product.path("manufacturer")),
                gtin,
                text(product.path("sku")),
                text(product.path("mpn")),
                offer != null ? priceOf(offer) : null,
                offer != null ? oldPriceOf(offer) : null,
                offer != null ? text(offer.path("priceCurrency")) : null,
                offer != null ? availabilityOf(offer) : Availability.UNKNOWN,
                resolve(pageUri, url),
                specsOf(product.path("additionalProperty")));
    }

    /**
     * Обирає пропозицію: першу в наявності з ціною, інакше першу з ціною, інакше першу.
     */
    private static JsonNode selectOffer(JsonNode offersNode) {
        List<JsonNode> offers = new ArrayList<>();
        flattenOffers(offersNode, offers);
        return offers.stream().filter(o -> priceOf(o) != null && availabilityOf(o) == Availability.IN_STOCK).findFirst()
                .or(() -> offers.stream().filter(o -> priceOf(o) != null).findFirst())
                .or(() -> offers.stream().findFirst())
                .orElse(null);
    }

    private static void flattenOffers(JsonNode node, List<JsonNode> out) {
        if (node.isArray()) {
            node.values().forEach(child -> flattenOffers(child, out));
        } else if (node.isObject()) {
            out.add(node);
            // AggregateOffer може містити вкладені пропозиції окремих продавців
            if (hasType(node, "aggregateoffer")) {
                flattenOffers(node.path("offers"), out);
            }
        }
    }

    private static BigDecimal priceOf(JsonNode offer) {
        BigDecimal price = decimal(offer.path("price"));
        if (price == null) {
            price = decimal(offer.path("lowPrice"));
        }
        if (price == null) {
            JsonNode spec = offer.path("priceSpecification");
            for (JsonNode s : spec.isArray() ? spec.values() : List.of(spec)) {
                if (!isOldPriceSpec(s)) {
                    price = decimal(s.path("price"));
                    if (price != null) {
                        break;
                    }
                }
            }
        }
        return price;
    }

    private static BigDecimal oldPriceOf(JsonNode offer) {
        JsonNode spec = offer.path("priceSpecification");
        for (JsonNode s : spec.isArray() ? spec.values() : List.of(spec)) {
            if (isOldPriceSpec(s)) {
                BigDecimal value = decimal(s.path("price"));
                if (value != null) {
                    return value;
                }
            }
        }
        return null;
    }

    private static boolean isOldPriceSpec(JsonNode spec) {
        String type = SchemaOrg.term(text(spec.path("priceType")));
        return type != null && SchemaOrg.OLD_PRICE_TYPES.contains(type);
    }

    private static Availability availabilityOf(JsonNode offer) {
        return SchemaOrg.availability(text(offer.path("availability")));
    }

    private static Map<String, String> specsOf(JsonNode properties) {
        Map<String, String> specs = new LinkedHashMap<>();
        for (JsonNode p : properties.isArray() ? properties.values() : List.of(properties)) {
            String name = text(p.path("name"));
            String value = text(p.path("value"));
            if (name != null && value != null) {
                specs.putIfAbsent(name, value);
            }
        }
        return specs;
    }

    /** {@code @type} може бути рядком або масивом, з префіксом {@code schema:} або повним URL. */
    private static boolean hasType(JsonNode node, String type) {
        JsonNode t = node.path("@type");
        for (JsonNode value : t.isArray() ? t.values() : List.of(t)) {
            if (type.equals(SchemaOrg.term(text(value)))) {
                return true;
            }
        }
        return false;
    }

    private static String nameOf(JsonNode node) {
        return node.isObject() ? text(node.path("name")) : text(node);
    }

    private static String firstText(JsonNode node, List<String> fields) {
        for (String field : fields) {
            String value = text(node.path(field));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static BigDecimal decimal(JsonNode node) {
        if (node.isNumber()) {
            BigDecimal value = node.decimalValue();
            return value.signum() > 0 ? value : null;
        }
        return node.isString() ? PriceText.parse(node.asString()) : null;
    }

    /** Скалярне значення як рядок: HTML-сутності розкодовано, пробіли нормалізовано, порожнє → null. */
    private static String text(JsonNode node) {
        if (!node.isString() && !node.isNumber()) {
            return null;
        }
        String value = Parser.unescapeEntities(node.asString(), false).replaceAll("\\s+", " ").strip();
        return value.isEmpty() ? null : value;
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

}
