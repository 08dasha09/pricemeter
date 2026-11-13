package org.viewer.pricemeter.matching.normalize;

import java.text.Normalizer;
import java.util.Map;

/**
 * Посимвольні перетворення тексту, спільні для нормалізатора й екстракторів.
 */
final class MatchingText {

    /** Кириличні літери, які в кодах моделей плутають з латинськими. */
    private static final Map<Character, Character> LOOKALIKES = Map.ofEntries(
            Map.entry('А', 'A'), Map.entry('В', 'B'), Map.entry('С', 'C'), Map.entry('Е', 'E'),
            Map.entry('Н', 'H'), Map.entry('І', 'I'), Map.entry('К', 'K'), Map.entry('М', 'M'),
            Map.entry('О', 'O'), Map.entry('Р', 'P'), Map.entry('Т', 'T'), Map.entry('Х', 'X'),
            Map.entry('У', 'Y'),
            Map.entry('а', 'a'), Map.entry('с', 'c'), Map.entry('е', 'e'), Map.entry('і', 'i'),
            Map.entry('о', 'o'), Map.entry('р', 'p'), Map.entry('х', 'x'), Map.entry('у', 'y'));

    private MatchingText() {
    }

    /**
     * Прибирає діакритику з латинських літер ({@code Kärcher → Karcher}), не чіпаючи кирилицю
     * ({@code й}, {@code ї} під час розкладу теж втратили б знаки).
     */
    static String foldLatinDiacritics(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 'À' && c <= 'ɏ') {
                String decomposed = Normalizer.normalize(String.valueOf(c), Normalizer.Form.NFD);
                out.append(decomposed.replaceAll("\\p{M}", ""));
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    /**
     * Замінює кириличних «двійників» латиницею в токені, схожому на код (є цифра або латинська літера):
     * {@code WW80Т4040} з кириличною «Т» → {@code WW80T4040}. Звичайні слова лишаються без змін.
     */
    static String latinizeLookalikes(String token) {
        if (!looksLikeCode(token)) {
            return token;
        }
        StringBuilder out = new StringBuilder(token.length());
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            out.append(LOOKALIKES.getOrDefault(c, c));
        }
        return out.toString();
    }

    static boolean isAsciiLetter(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
    }

    static boolean isAsciiDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean looksLikeCode(String token) {
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (isAsciiLetter(c) || isAsciiDigit(c)) {
                return true;
            }
        }
        return false;
    }

}
