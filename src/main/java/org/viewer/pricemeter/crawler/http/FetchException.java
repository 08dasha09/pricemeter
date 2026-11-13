package org.viewer.pricemeter.crawler.http;

import java.net.URI;
import java.util.OptionalInt;

/**
 * Не вдалося отримати сторінку.
 */
public class FetchException extends RuntimeException {

    private final URI uri;
    private final Integer statusCode;

    public FetchException(URI uri, int statusCode) {
        super("HTTP " + statusCode + " for " + uri);
        this.uri = uri;
        this.statusCode = statusCode;
    }

    public FetchException(URI uri, String message, Throwable cause) {
        super(message + ": " + uri, cause);
        this.uri = uri;
        this.statusCode = null;
    }

    protected FetchException(URI uri, String message) {
        super(message + ": " + uri);
        this.uri = uri;
        this.statusCode = null;
    }

    public URI getUri() {
        return uri;
    }

    /** HTTP-статус, якщо сервер відповів. */
    public OptionalInt getStatusCode() {
        return statusCode == null ? OptionalInt.empty() : OptionalInt.of(statusCode);
    }

    /** Сторінки більше немає (404 / 410) — пропозицію можна вважати зниклою. */
    public boolean isNotFound() {
        return statusCode != null && (statusCode == 404 || statusCode == 410);
    }

}
