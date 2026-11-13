package org.viewer.pricemeter.matching.normalize;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Визначає канонічний бренд пропозиції: з поля магазину ({@code brand_raw}), а якщо його немає — за словником
 * у назві. Канонічна форма — латиниця у верхньому регістрі без діакритики, слова через пробіл:
 * {@code "Hotpoint-Ariston" → "HOTPOINT ARISTON"}, {@code "Kärcher" → "KARCHER"}.
 */
@Component
public class BrandExtractor {

    /** Відомі бренди побутової техніки (канонічна форма). */
    private static final Set<String> KNOWN_BRANDS = Set.of(
            "AEG", "AMICA", "ARDESTO", "ATLANT", "BEKO", "BOSCH", "BRANDT", "CANDY", "CECOTEC", "CONCEPT",
            "DE LONGHI", "DREAME", "DYSON", "ECOVACS", "EDLER", "ELECTROLUX", "ELENBERG", "EXPERT", "FRANKE",
            "GORENJE", "GRUNDIG", "GRUNHELM", "HAIER", "HANSA", "HISENSE", "HOTPOINT ARISTON", "INDESIT",
            "INTERLINE", "IROBOT", "KARCHER", "KERNAU", "LG", "LIBERTON", "LIEBHERR", "MAGIO", "MIDEA", "MIELE",
            "MPM", "NEFF", "NORD", "PANASONIC", "PHILCO", "PHILIPS", "PRIME TECHNICS", "PYRAMIDA", "ROBOROCK",
            "ROWENTA", "SAMSUNG", "SHARP", "SIEMENS", "SMEG", "SNAIGE", "TCL", "TEFAL", "THOMAS", "TOSHIBA",
            "VENTOLUX", "VESTFROST", "WEILOR", "WHIRLPOOL", "XIAOMI", "ZANUSSI");

    /** Інші написання → канонічна форма. */
    private static final Map<String, String> ALIASES = Map.of(
            "HOTPOINT", "HOTPOINT ARISTON",
            "DELONGHI", "DE LONGHI",
            "БОШ", "BOSCH",
            "САМСУНГ", "SAMSUNG",
            "КЕРХЕР", "KARCHER");

    private static final Pattern NON_ALNUM = Pattern.compile("[^\\p{L}\\p{N}]+");

    /** Шаблони пошуку в назві: канонічна форма або синонім → канонічна форма; довші — першими. */
    private final List<Map.Entry<Pattern, String>> titlePatterns;

    public BrandExtractor() {
        Map<String, String> spellings = new HashMap<>();
        KNOWN_BRANDS.forEach(brand -> spellings.put(brand, brand));
        spellings.putAll(ALIASES);
        titlePatterns = spellings.entrySet().stream()
                .sorted(Map.Entry.<String, String>comparingByKey(
                        (a, b) -> Integer.compare(b.length(), a.length())).thenComparing(Map.Entry.comparingByKey()))
                .map(e -> Map.entry(wordsPattern(e.getKey()), e.getValue()))
                .toList();
    }

    /**
     * @param brandRaw бренд, як його вказав магазин (може бути порожнім)
     * @param title    назва пропозиції — для пошуку бренду за словником
     * @return канонічний бренд або порожньо, якщо його не вдалося визначити
     */
    public Optional<String> extract(String brandRaw, String title) {
        String fromShop = canonical(brandRaw);
        if (fromShop != null) {
            return Optional.of(fromShop);
        }
        return findInTitle(title);
    }

    /**
     * Канонічна форма довільного написання бренду; невідомий бренд теж приводиться до цієї форми.
     *
     * @return канонічний бренд або {@code null} для порожнього значення
     */
    public String canonical(String brand) {
        if (brand == null) {
            return null;
        }
        String key = NON_ALNUM.matcher(MatchingText.foldLatinDiacritics(brand).toUpperCase(Locale.ROOT))
                .replaceAll(" ").strip();
        if (key.isEmpty()) {
            return null;
        }
        return ALIASES.getOrDefault(key, key);
    }

    /**
     * Шаблон, що знаходить у назві будь-яке написання бренду: {@code HOTPOINT ARISTON} знайде і
     * {@code "Hotpoint-Ariston"}, і {@code "HOTPOINT"}. Потрібен, щоб вирізати бренд перед пошуком коду моделі.
     */
    public Pattern titlePattern(String canonicalBrand) {
        List<String> spellings = new ArrayList<>();
        spellings.add(canonicalBrand);
        ALIASES.forEach((alias, brand) -> {
            if (brand.equals(canonicalBrand)) {
                spellings.add(alias);
            }
        });
        spellings.sort((a, b) -> Integer.compare(b.length(), a.length()));
        return Pattern.compile(String.join("|", spellings.stream().map(BrandExtractor::wordsRegex).toList()),
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    private Optional<String> findInTitle(String title) {
        if (title == null || title.isBlank()) {
            return Optional.empty();
        }
        String folded = MatchingText.foldLatinDiacritics(title);
        String best = null;
        int bestStart = Integer.MAX_VALUE;
        for (Map.Entry<Pattern, String> entry : titlePatterns) {
            Matcher m = entry.getKey().matcher(folded);
            if (m.find() && m.start() < bestStart) {
                bestStart = m.start();
                best = entry.getValue();
            }
        }
        return Optional.ofNullable(best);
    }

    private static Pattern wordsPattern(String spelling) {
        return Pattern.compile(wordsRegex(spelling), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    /** Слова бренду цілими словами, між ними — будь-які пробіли, дефіси, підкреслення. */
    private static String wordsRegex(String spelling) {
        List<String> words = List.of(spelling.split(" "));
        return "(?<![\\p{L}\\p{N}])"
                + String.join("[\\s\\-_]*", words.stream().map(Pattern::quote).toList())
                + "(?![\\p{L}\\p{N}])";
    }

}
