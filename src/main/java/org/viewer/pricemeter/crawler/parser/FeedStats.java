package org.viewer.pricemeter.crawler.parser;

/**
 * Підсумок розбору фіду.
 *
 * @param format  формат фіду
 * @param offers  кількість переданих далі пропозицій
 * @param skipped кількість пропущених некоректних пропозицій
 */
public record FeedStats(FeedFormat format, int offers, int skipped) {
}
