package org.viewer.pricemeter.crawler.parser;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Потоковий розбір товарних фідів (YML та Google Merchant). Фід не завантажується в пам'ять
 * цілком: кожна пропозиція одразу передається в {@code sink}.
 */
@Slf4j
@Component
public class XmlFeedParser {

    private static final Pattern CURRENCY = Pattern.compile("\\b([A-Z]{3})\\b");

    private static final XMLInputFactory FACTORY = createFactory();

    /**
     * @param in   вміст фіду (потік не закривається)
     * @param sink отримувач пропозицій
     * @return кількість переданих і пропущених пропозицій
     * @throws FeedFormatException якщо формат невідомий або XML зламаний
     */
    public FeedStats parse(InputStream in, Consumer<FeedOffer> sink) {
        XMLStreamReader reader = null;
        try {
            reader = FACTORY.createXMLStreamReader(in);
            while (reader.hasNext() && reader.next() != XMLStreamConstants.START_ELEMENT) {
                // пропускаємо пролог до кореневого елемента
            }
            if (!reader.isStartElement()) {
                throw new FeedFormatException("Feed is empty");
            }
            String root = reader.getLocalName();
            return switch (root) {
                case "yml_catalog" -> parseYml(reader, sink);
                case "rss", "feed" -> parseGoogleMerchant(reader, sink);
                default -> throw new FeedFormatException("Unsupported feed root element: <" + root + ">");
            };
        } catch (XMLStreamException e) {
            throw new FeedFormatException("Malformed feed XML: " + e.getMessage(), e);
        } finally {
            closeQuietly(reader);
        }
    }

    // ---------------------------------------------------------------- YML

    private FeedStats parseYml(XMLStreamReader r, Consumer<FeedOffer> sink) throws XMLStreamException {
        Map<String, String> categories = new HashMap<>();
        int offers = 0;
        int skipped = 0;
        while (r.hasNext()) {
            if (r.next() != XMLStreamConstants.START_ELEMENT) {
                continue;
            }
            switch (r.getLocalName()) {
                case "category" -> {
                    String id = r.getAttributeValue(null, "id");
                    String name = readText(r);
                    if (id != null && name != null) {
                        categories.put(id.strip(), name);
                    }
                }
                case "offer" -> {
                    FeedOffer offer = readYmlOffer(r, categories);
                    if (offer != null) {
                        sink.accept(offer);
                        offers++;
                    } else {
                        skipped++;
                    }
                }
                default -> { /* shop, offers, categories тощо — заходимо всередину */ }
            }
        }
        return new FeedStats(FeedFormat.YML, offers, skipped);
    }

    private FeedOffer readYmlOffer(XMLStreamReader r, Map<String, String> categories) throws XMLStreamException {
        String id = blankToNull(r.getAttributeValue(null, "id"));
        String availableAttr = blankToNull(r.getAttributeValue(null, "available"));
        Map<String, String> fields = new HashMap<>();
        Map<String, String> params = new LinkedHashMap<>();

        readChildren(r, (name, reader) -> {
            if (name.equals("param")) {
                String paramName = blankToNull(reader.getAttributeValue(null, "name"));
                String value = readText(reader);
                if (paramName != null && value != null) {
                    params.putIfAbsent(paramName, value);
                }
            } else {
                String value = readText(reader);
                if (value != null) {
                    fields.putIfAbsent(name, value);
                }
            }
        });

        String name = fields.get("name");
        if (name == null) {
            // формат vendor.model: назва складається з typePrefix + vendor + model
            name = blankToNull(Stream.of(fields.get("typePrefix"), fields.get("vendor"), fields.get("model"))
                    .filter(s -> s != null)
                    .collect(Collectors.joining(" ")));
        }
        String url = fields.get("url");
        if (id == null || name == null || url == null) {
            log.debug("Skipping YML offer without id/name/url: id={}", id);
            return null;
        }

        String categoryId = fields.get("categoryId");
        return new FeedOffer(
                id, name, url,
                PriceText.parse(fields.get("price")),
                PriceText.parse(fields.get("oldprice")),
                fields.get("currencyId"),
                ymlAvailability(availableAttr != null ? availableAttr : fields.get("available"), fields.get("stock_quantity")),
                categoryId,
                categoryId != null ? categories.get(categoryId) : null,
                fields.get("vendor"),
                fields.getOrDefault("vendorCode", fields.get("model")),
                fields.get("barcode"),
                params);
    }

    private static Availability ymlAvailability(String available, String stockQuantity) {
        if (available != null) {
            return switch (available.toLowerCase(Locale.ROOT)) {
                case "true", "1", "yes" -> Availability.IN_STOCK;
                case "false", "0", "no" -> Availability.OUT_OF_STOCK;
                default -> Availability.UNKNOWN;
            };
        }
        if (stockQuantity != null) {
            try {
                return Integer.parseInt(stockQuantity) > 0 ? Availability.IN_STOCK : Availability.OUT_OF_STOCK;
            } catch (NumberFormatException e) {
                return Availability.UNKNOWN;
            }
        }
        return Availability.UNKNOWN;
    }

    // ---------------------------------------------------- Google Merchant

    private FeedStats parseGoogleMerchant(XMLStreamReader r, Consumer<FeedOffer> sink) throws XMLStreamException {
        int offers = 0;
        int skipped = 0;
        while (r.hasNext()) {
            if (r.next() != XMLStreamConstants.START_ELEMENT) {
                continue;
            }
            String name = r.getLocalName();
            if (name.equals("item") || name.equals("entry")) {
                FeedOffer offer = readGoogleItem(r);
                if (offer != null) {
                    sink.accept(offer);
                    offers++;
                } else {
                    skipped++;
                }
            }
        }
        return new FeedStats(FeedFormat.GOOGLE_MERCHANT, offers, skipped);
    }

    private FeedOffer readGoogleItem(XMLStreamReader r) throws XMLStreamException {
        Map<String, String> fields = new HashMap<>();
        Map<String, String> params = new LinkedHashMap<>();

        // префікс g: не обов'язковий, тому працюємо з локальними іменами
        readChildren(r, (name, reader) -> {
            if (name.equals("product_detail")) {
                Map<String, String> detail = new HashMap<>();
                readChildren(reader, (n, rr) -> {
                    String v = readText(rr);
                    if (v != null) {
                        detail.putIfAbsent(n, v);
                    }
                });
                if (detail.get("attribute_name") != null && detail.get("attribute_value") != null) {
                    params.putIfAbsent(detail.get("attribute_name"), detail.get("attribute_value"));
                }
            } else {
                String value = readText(reader);
                if (value != null) {
                    fields.putIfAbsent(name, value);
                }
            }
        });

        String id = fields.get("id");
        String name = fields.get("title");
        String url = fields.get("link");
        if (id == null || name == null || url == null) {
            log.debug("Skipping Google Merchant item without id/title/link: id={}", id);
            return null;
        }

        // sale_price — акційна ціна, тоді price — ціна без знижки
        String regular = fields.get("price");
        String sale = fields.get("sale_price");
        BigDecimal price = PriceText.parse(sale != null ? sale : regular);
        BigDecimal oldPrice = sale != null ? PriceText.parse(regular) : null;

        return new FeedOffer(
                id, name, url, price, oldPrice,
                currencyOf(sale != null ? sale : regular),
                googleAvailability(fields.get("availability")),
                null,
                fields.getOrDefault("product_type", fields.get("google_product_category")),
                fields.get("brand"),
                fields.get("mpn"),
                fields.get("gtin"),
                params);
    }

    private static Availability googleAvailability(String value) {
        if (value == null) {
            return Availability.UNKNOWN;
        }
        return switch (value.toLowerCase(Locale.ROOT).replace('_', ' ')) {
            case "in stock", "preorder", "backorder" -> Availability.IN_STOCK;
            case "out of stock" -> Availability.OUT_OF_STOCK;
            default -> Availability.UNKNOWN;
        };
    }

    private static String currencyOf(String price) {
        if (price == null) {
            return null;
        }
        Matcher m = CURRENCY.matcher(price);
        return m.find() ? m.group(1) : null;
    }

    // ------------------------------------------------------------ helpers

    @FunctionalInterface
    private interface ChildHandler {
        /** Викликається на START_ELEMENT дочірнього елемента; має повністю прочитати його. */
        void handle(String localName, XMLStreamReader reader) throws XMLStreamException;
    }

    /**
     * Обходить прямі дочірні елементи поточного елемента до його закриття.
     */
    private static void readChildren(XMLStreamReader r, ChildHandler handler) throws XMLStreamException {
        while (r.hasNext()) {
            int event = r.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                handler.handle(r.getLocalName(), r);
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                return;
            }
        }
    }

    /**
     * Читає весь текст елемента разом із вкладеними елементами і переходить на його закриття.
     * На відміну від {@code getElementText()} не падає на вкладених тегах.
     */
    private static String readText(XMLStreamReader r) throws XMLStreamException {
        StringBuilder text = new StringBuilder();
        int depth = 1;
        while (depth > 0 && r.hasNext()) {
            switch (r.next()) {
                case XMLStreamConstants.START_ELEMENT -> depth++;
                case XMLStreamConstants.END_ELEMENT -> depth--;
                case XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA, XMLStreamConstants.SPACE ->
                        text.append(r.getText());
                default -> { }
            }
        }
        return blankToNull(text.toString().replaceAll("\\s+", " "));
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String stripped = value.strip();
        return stripped.isEmpty() ? null : stripped;
    }

    private static XMLInputFactory createFactory() {
        XMLInputFactory factory = XMLInputFactory.newDefaultFactory();
        // фід — зовнішні дані: жодних DTD і зовнішніх сутностей (захист від XXE)
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true);
        factory.setProperty(XMLInputFactory.IS_COALESCING, true);
        return factory;
    }

    private static void closeQuietly(XMLStreamReader reader) {
        if (reader != null) {
            try {
                reader.close();
            } catch (XMLStreamException ignored) {
                // нічого не робимо: потік закриває той, хто його відкрив
            }
        }
    }

}
