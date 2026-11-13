package org.viewer.pricemeter.crawler.adapter;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Збережені реальні сторінки магазинів з {@code src/test/resources/fixtures/<магазин>/}.
 */
final class AdapterFixtures {

    private AdapterFixtures() {
    }

    static Document document(String path, String baseUri) {
        return Jsoup.parse(read(path), baseUri);
    }

    static Document xmlDocument(String path, String baseUri) {
        return Jsoup.parse(read(path), baseUri, Parser.xmlParser());
    }

    private static String read(String path) {
        try (InputStream in = AdapterFixtures.class.getResourceAsStream("/fixtures/" + path)) {
            if (in == null) {
                throw new IllegalArgumentException("Fixture not found: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
