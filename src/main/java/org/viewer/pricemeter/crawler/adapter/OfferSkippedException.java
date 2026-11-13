package org.viewer.pricemeter.crawler.adapter;

import java.net.URI;

/**
 * Сторінку розібрано, але пропозицію свідомо пропущено (напр. товар з іншої категорії).
 * Це не помилка обходу — у звіті рахується окремо.
 */
public class OfferSkippedException extends RuntimeException {

    public OfferSkippedException(URI uri, String reason) {
        super(reason + ": " + uri);
    }

}
