package org.viewer.pricemeter.crawler.parser;

/**
 * Фід неможливо розібрати: невідомий формат або зламаний XML.
 */
public class FeedFormatException extends RuntimeException {

    public FeedFormatException(String message) {
        super(message);
    }

    public FeedFormatException(String message, Throwable cause) {
        super(message, cause);
    }

}
