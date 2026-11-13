package org.viewer.pricemeter.crawler.http;

import java.net.URI;

/**
 * Адресу заборонено для обходу правилами robots.txt.
 */
public class RobotsDisallowedException extends FetchException {

    public RobotsDisallowedException(URI uri) {
        super(uri, "Disallowed by robots.txt");
    }

}
