package org.viewer.pricemeter.crawler.parser;

import java.math.BigDecimal;

/**
 * Розбір ціни з тексту: {@code "15 999 ₴"}, {@code "15999.00"}, {@code "15 999,50 грн"}, {@code "15,999.50"}.
 */
public final class PriceText {

    private PriceText() {
    }

    /**
     * @return додатна ціна або {@code null}, якщо текст не містить коректної ціни
     */
    public static BigDecimal parse(String text) {
        if (text == null) {
            return null;
        }
        String cleaned = text.replaceAll("[^0-9.,]", "");
        if (cleaned.chars().noneMatch(Character::isDigit)) {
            return null;
        }

        // Останній роздільник, за яким 1–2 цифри, — десятковий; решта — роздільники тисяч.
        int lastSep = Math.max(cleaned.lastIndexOf('.'), cleaned.lastIndexOf(','));
        String integerPart = cleaned;
        String fraction = "";
        if (lastSep >= 0) {
            String tail = cleaned.substring(lastSep + 1);
            if (!tail.isEmpty() && tail.length() <= 2) {
                integerPart = cleaned.substring(0, lastSep);
                fraction = tail;
            }
        }
        String digits = integerPart.replaceAll("[.,]", "");
        if (digits.isEmpty()) {
            return null;
        }

        BigDecimal value = new BigDecimal(fraction.isEmpty() ? digits : digits + "." + fraction);
        return value.signum() > 0 ? value : null;
    }

}
