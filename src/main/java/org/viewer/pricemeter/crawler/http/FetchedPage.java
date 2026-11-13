package org.viewer.pricemeter.crawler.http;

import java.net.URI;

/**
 * Успішно отримана сторінка.
 *
 * @param uri        кінцева адреса (після редиректів)
 * @param statusCode HTTP-статус (2xx)
 * @param body       тіло відповіді
 */
public record FetchedPage(URI uri, int statusCode, String body) {
}
