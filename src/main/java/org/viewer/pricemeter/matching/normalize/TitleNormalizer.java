package org.viewer.pricemeter.matching.normalize;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Нормалізує назву пропозиції для порівняння текстів: нижній регістр, латиниця замість кириличних двійників
 * у кодах, роздільники ({@code / - _ ,}) → пробіл, без типу товару, кольорів і службових слів.
 * {@code "Пральна машина Samsung WW80T4040 CE1/UA" → "samsung ww80t4040 ce1 ua"}.
 *
 * <p>Тип товару вилучається, бо категорію порівнюємо окремо, а різні магазини називають її по-різному
 * («Двухкамерный холодильник» проти «Холодильник»).
 */
@Component
public class TitleNormalizer {

    /** Тип товару та його ознаки, українською й російською; багатослівні — з пробілом замість дефіса. */
    private static final List<Pattern> NOISE_PHRASES = phrasePatterns(
            "пральна машина", "прально сушильна машина", "сушильна машина",
            "стиральная машина", "стирально сушильная машина", "сушильная машина",
            "посудомийна машина", "посудомоечная машина",
            "холодильник", "морозильна камера", "морозильная камера",
            "двокамерний", "двухкамерный", "однокамерний", "однокамерный",
            "мікрохвильова піч", "микроволновая печь", "мікрохвильовка", "микроволновка",
            "робот пилосос", "робот пылесос", "пилосос", "пылесос",
            "акумуляторний", "аккумуляторный", "вертикальний", "вертикальный", "циклонний", "циклонный",
            "господарський", "хозяйственный", "миючий", "моющий",
            "для сухого прибирання", "для сухой уборки", "без мішка", "без мешка", "з мішком", "с мешком",
            "вбудована", "вбудовувана", "вбудований", "вбудовуваний",
            "встраиваемая", "встраиваемый", "окремостояча", "отдельностоящая");

    private static final Set<String> NOISE_WORDS = Set.of(
            "білий", "біла", "чорний", "чорна", "сріблястий", "срібляста", "сірий", "сіра", "бежевий", "бежева",
            "белый", "белая", "черный", "черная", "серебристый", "серый", "бежевый",
            "white", "black", "silver", "grey", "gray", "beige", "inox",
            // службова позначка Moyo в артикулі: «DWMF-G605W_Sale»
            "sale");

    private static final Pattern SEPARATORS = Pattern.compile("[\\s/\\\\_,;:()\\[\\]«»\"'|+*!-]+");
    /** Крапка, що не стоїть між цифрами ({@code 15.60} лишається). */
    private static final Pattern STRAY_DOT = Pattern.compile("(?<!\\d)\\.|\\.(?!\\d)");

    public String normalize(String title) {
        if (title == null || title.isBlank()) {
            return "";
        }
        String text = MatchingText.foldLatinDiacritics(Normalizer.normalize(title, Normalizer.Form.NFKC));
        text = STRAY_DOT.matcher(text).replaceAll(" ");

        // Двійників замінюємо до нижнього регістру: словник — для великих літер кодів («Т», не «т»).
        List<String> tokens = new ArrayList<>();
        for (String part : SEPARATORS.split(text)) {
            if (!part.isEmpty()) {
                tokens.add(MatchingText.latinizeLookalikes(part).toLowerCase(Locale.ROOT).replace('ё', 'е'));
            }
        }

        String joined = String.join(" ", tokens);
        for (Pattern phrase : NOISE_PHRASES) {
            joined = phrase.matcher(joined).replaceAll("");
        }
        return Arrays.stream(joined.split(" "))
                .filter(token -> !token.isEmpty() && !NOISE_WORDS.contains(token))
                .reduce((a, b) -> a + " " + b)
                .orElse("");
    }

    /** Фраза цілими словами; довші — першими, щоб «прально сушильна машина» не розібрало «сушильна машина». */
    private static List<Pattern> phrasePatterns(String... phrases) {
        return Arrays.stream(phrases)
                .sorted(Comparator.comparingInt(String::length).reversed())
                .map(phrase -> Pattern.compile("(?<![^ ])" + Pattern.quote(phrase) + "(?![^ ])"))
                .toList();
    }

}
