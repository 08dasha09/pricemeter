package org.viewer.pricemeter.crawler.parser;

/**
 * Підтримувані формати товарних фідів.
 */
public enum FeedFormat {
    /** Yandex Market Language: {@code <yml_catalog><shop><offers><offer>}; формат прайсів Rozetka та CPA-мереж. */
    YML,
    /** Google Merchant Center: RSS {@code <item>} або Atom {@code <entry>} з полями {@code g:*}. */
    GOOGLE_MERCHANT
}
