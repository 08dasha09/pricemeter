package org.viewer.pricemeter.crawler.adapter;

import java.net.URI;

/**
 * Сторінку отримано, але дані товару на ній не знайдено (змінилась верстка або це не сторінка товару).
 */
public class OfferParseException extends RuntimeException {

    public OfferParseException(URI uri, String message) {
        super(message + ": " + uri);
    }

}
