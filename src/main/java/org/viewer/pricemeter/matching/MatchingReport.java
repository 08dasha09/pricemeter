package org.viewer.pricemeter.matching;

/**
 * Підсумок зіставлення категорії.
 *
 * @param offers          пропозицій у категорії
 * @param products        товарів після зіставлення
 * @param multiShop       товарів, що мають пропозиції кількох магазинів
 * @param autoMatched     пропозицій, автоматично об'єднаних з іншими
 * @param pending         пропозицій з можливим збігом, що чекають ручної перевірки
 */
public record MatchingReport(
        String categoryCode,
        int offers,
        int products,
        int multiShop,
        int autoMatched,
        int pending) {
}
