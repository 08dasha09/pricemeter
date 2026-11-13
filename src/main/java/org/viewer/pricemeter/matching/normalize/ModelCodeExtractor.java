package org.viewer.pricemeter.matching.normalize;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Вилучає код моделі: з {@code mpn}, якщо магазин його вказав, інакше — з назви.
 *
 * <p>У назві шукається «якір» — найдовший токен з латинських літер і цифр ({@code WW80T4040},
 * {@code FC9745/09}); якщо такого немає — число з ≥ 3 цифр ({@code 7259} у {@code "WRSB 7259 WB UA"}).
 * До якоря приєднуються сусідні фрагменти коду, розділені пробілами: короткі латинські токени й токени
 * з цифрами. Канонізація склеює все в один рядок: {@code "WW80T4040 CE1/UA" → WW80T4040CE1UA}.
 */
@Component
public class ModelCodeExtractor {

    private static final int MAX_LEFT_FRAGMENTS = 2;
    private static final int MAX_RIGHT_FRAGMENTS = 3;
    /** Найдовший буквений фрагмент коду: {@code WRSB}, {@code HI}, {@code UA}. */
    private static final int MAX_SHORT_FRAGMENT = 4;
    private static final int MIN_CODE_LENGTH = 2;
    private static final int MIN_NUMERIC_ANCHOR_DIGITS = 3;

    /** Короткі латинські слова, що не бувають частиною коду. */
    private static final Set<String> STOP_WORDS = Set.of(
            "SOLO", "GRIL", "KIT", "SET", "NEW", "SALE", "LED", "WIFI", "INOX", "WHITE", "BLACK");

    private static final Pattern TOKEN_SEPARATORS = Pattern.compile("[\\s,;()\\[\\]«»\"']+");
    private static final Pattern EDGE_PUNCTUATION = Pattern.compile("^[.:/\\-_]+|[.:/\\-_]+$");
    private static final Pattern CODE_CHARS = Pattern.compile("[A-Za-z0-9./\\-+_]+");
    private static final Pattern NUMERIC_CHARS = Pattern.compile("[0-9./\\-]+");
    private static final Pattern LETTERS = Pattern.compile("[A-Za-z]+");
    private static final Pattern NON_ALNUM = Pattern.compile("[^A-Z0-9]");
    /** Службовий суфікс магазину в {@code mpn}: Moyo пише {@code DWMF-G605W_Sale}. */
    private static final Pattern MPN_SHOP_SUFFIX = Pattern.compile("_[A-Za-z]+$");
    /** Регіональний суфікс: {@code UA}, {@code /UK}; {@code LE}, {@code WT} — лише після роздільника (Samsung). */
    private static final Pattern REGION_SUFFIX = Pattern.compile("(?:[\\s/_-]*(?:UA|UK|EU|RU)|[\\s/_-]+(?:LE|WT))$");

    private final BrandExtractor brandExtractor;

    public ModelCodeExtractor(BrandExtractor brandExtractor) {
        this.brandExtractor = brandExtractor;
    }

    /**
     * @param title назва пропозиції
     * @param mpn   код моделі від магазину (може бути порожнім)
     * @param brand канонічний бренд ({@link BrandExtractor}) — вирізається з назви перед пошуком; може бути порожнім
     */
    public Optional<ModelCode> extract(String title, String mpn, String brand) {
        if (mpn != null && !mpn.isBlank()) {
            Optional<ModelCode> fromMpn = toModelCode(MPN_SHOP_SUFFIX.matcher(mpn.strip()).replaceAll(""),
                    ModelCode.Source.MPN);
            if (fromMpn.isPresent()) {
                return fromMpn;
            }
        }
        if (title == null || title.isBlank()) {
            return Optional.empty();
        }
        String text = MatchingText.foldLatinDiacritics(title);
        if (brand != null && !brand.isBlank()) {
            text = brandExtractor.titlePattern(brand).matcher(text).replaceAll(" ");
        }
        return findInTokens(tokenize(text)).flatMap(raw -> toModelCode(raw, ModelCode.Source.TITLE));
    }

    private static List<Token> tokenize(String text) {
        List<Token> tokens = new ArrayList<>();
        for (String part : TOKEN_SEPARATORS.split(text)) {
            String value = MatchingText.latinizeLookalikes(EDGE_PUNCTUATION.matcher(part).replaceAll(""));
            if (!value.isEmpty()) {
                tokens.add(Token.of(value));
            }
        }
        return tokens;
    }

    private static Optional<String> findInTokens(List<Token> tokens) {
        int anchor = -1;
        for (int i = 0; i < tokens.size(); i++) {
            Token t = tokens.get(i);
            if (t.kind == Kind.MIXED && t.alnum >= MIN_CODE_LENGTH
                    && (anchor < 0 || t.alnum > tokens.get(anchor).alnum)) {
                anchor = i;
            }
        }
        boolean numericAnchor = false;
        if (anchor < 0) {
            for (int i = 0; i < tokens.size() && anchor < 0; i++) {
                Token t = tokens.get(i);
                if (t.kind == Kind.NUMERIC && t.alnum >= MIN_NUMERIC_ANCHOR_DIGITS) {
                    anchor = i;
                    numericAnchor = true;
                }
            }
        }
        if (anchor < 0) {
            return Optional.empty();
        }

        int from = anchor;
        if (numericAnchor) {
            while (from > 0 && anchor - from < MAX_LEFT_FRAGMENTS && tokens.get(from - 1).kind == Kind.SHORT) {
                from--;
            }
        }
        int to = anchor;
        while (to + 1 < tokens.size() && to - anchor < MAX_RIGHT_FRAGMENTS
                && isRightFragment(tokens.get(to + 1), numericAnchor)) {
            to++;
        }

        List<String> parts = new ArrayList<>();
        for (int i = from; i <= to; i++) {
            parts.add(tokens.get(i).value);
        }
        return Optional.of(String.join(" ", parts));
    }

    /** Після буквено-цифрового якоря окремі числа не приєднуємо: це, найчастіше, потужність чи об'єм. */
    private static boolean isRightFragment(Token token, boolean numericAnchor) {
        return token.kind == Kind.MIXED || token.kind == Kind.SHORT
                || (numericAnchor && token.kind == Kind.NUMERIC);
    }

    private static Optional<ModelCode> toModelCode(String raw, ModelCode.Source source) {
        String upper = MatchingText.latinizeLookalikes(raw).toUpperCase(Locale.ROOT);
        String full = canonical(upper);
        if (full.length() < MIN_CODE_LENGTH) {
            return Optional.empty();
        }
        String base = canonical(REGION_SUFFIX.matcher(upper).replaceAll(""));
        return Optional.of(new ModelCode(full, base.length() >= MIN_CODE_LENGTH ? base : full, source));
    }

    private static String canonical(String upper) {
        return NON_ALNUM.matcher(upper).replaceAll("");
    }

    private enum Kind {
        /** Латинські літери й цифри разом: {@code WW80T4040}, {@code CE1/UA}. */
        MIXED,
        /** Лише цифри й роздільники: {@code 7259}, {@code 1.628-009.0}. */
        NUMERIC,
        /** Коротке латинське слово, можливо фрагмент коду: {@code WRSB}, {@code UA}. */
        SHORT,
        OTHER
    }

    private record Token(String value, Kind kind, int alnum) {

        static Token of(String value) {
            int letters = 0;
            int digits = 0;
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                if (MatchingText.isAsciiLetter(c)) {
                    letters++;
                } else if (MatchingText.isAsciiDigit(c)) {
                    digits++;
                }
            }
            Kind kind;
            if (digits > 0 && NUMERIC_CHARS.matcher(value).matches()) {
                kind = Kind.NUMERIC;
            } else if (digits > 0 && letters > 0 && CODE_CHARS.matcher(value).matches()) {
                kind = Kind.MIXED;
            } else if (value.length() <= MAX_SHORT_FRAGMENT && LETTERS.matcher(value).matches()
                    && !STOP_WORDS.contains(value.toUpperCase(Locale.ROOT))) {
                kind = Kind.SHORT;
            } else {
                kind = Kind.OTHER;
            }
            return new Token(value, kind, letters + digits);
        }

    }

}
