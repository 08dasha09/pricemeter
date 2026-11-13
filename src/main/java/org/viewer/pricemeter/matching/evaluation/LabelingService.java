package org.viewer.pricemeter.matching.evaluation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.viewer.pricemeter.catalog.domain.Offer;
import org.viewer.pricemeter.catalog.service.CatalogService;
import org.viewer.pricemeter.matching.similarity.MatchScore;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Ручна розмітка пар пропозицій для оцінки зіставлення.
 *
 * <p>Кандидати на розмітку — пари, які порівнює зіставлення ({@link PairScanner}). Наступна пара береться з
 * діапазону оцінки, де розмічено найменше, — так вибірка покриває і явні випадки, і складні біля порогів
 * (стратифікована вибірка; {@link MatchingEvaluator} зважує її назад до генеральної сукупності).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LabelingService {

    private final CatalogService catalog;
    private final PairScanner pairs;
    private final MatchLabelRepository labels;
    private final Clock clock;

    /** Наступна нерозмічена пара або порожньо, якщо розмічати нічого. */
    public Optional<LabelingTask> nextTask() {
        Set<OfferPair> labeled = new HashSet<>();
        Map<ScoreBand, Integer> labeledPerBand = new EnumMap<>(ScoreBand.class);
        for (MatchLabel label : labels.findAll()) {
            labeled.add(label.pair());
            labeledPerBand.merge(ScoreBand.of(label.getScore().doubleValue()), 1, Integer::sum);
        }

        // по одній випадковій парі з кожного діапазону (reservoir sampling) — без зберігання всіх пар
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Map<ScoreBand, Reservoir> unlabeled = new EnumMap<>(ScoreBand.class);
        pairs.scan(p -> {
            if (labeled.contains(p.pair())) {
                return;
            }
            ScoreBand band = ScoreBand.of(p.score().value());
            Reservoir reservoir = unlabeled.computeIfAbsent(band, k -> new Reservoir());
            reservoir.seen++;
            if (random.nextInt(reservoir.seen) == 0) {
                reservoir.task = new LabelingTask(p.categoryCode(), LabelingTask.Side.of(p.a(), p.candidateA()),
                        LabelingTask.Side.of(p.b(), p.candidateB()), p.score(), band, 0);
            }
        });

        // найменше розмічений діапазон; за рівності — вищий (там рішення зіставлення)
        ScoreBand chosen = null;
        for (ScoreBand band : unlabeled.keySet()) {
            if (chosen == null || labeledPerBand.getOrDefault(band, 0) <= labeledPerBand.getOrDefault(chosen, 0)) {
                chosen = band;
            }
        }
        if (chosen == null) {
            return Optional.empty();
        }
        Reservoir reservoir = unlabeled.get(chosen);
        LabelingTask task = reservoir.task;
        return Optional.of(new LabelingTask(task.categoryCode(), task.a(), task.b(), task.score(), task.band(),
                reservoir.seen));
    }

    /**
     * Зберігає рішення людини; повторна розмітка пари замінює попереднє.
     *
     * @throws IllegalArgumentException пропозиції з різних категорій або одного магазину
     */
    @Transactional
    public MatchLabel label(long offerA, long offerB, boolean sameProduct) {
        OfferPair pair = OfferPair.of(offerA, offerB);
        Offer first = catalog.getOffer(pair.first());
        Offer second = catalog.getOffer(pair.second());
        String categoryCode = first.getCategory().getCode();
        if (!categoryCode.equals(second.getCategory().getCode())) {
            throw new IllegalArgumentException("Пропозиції з різних категорій");
        }
        if (first.getShop().getCode().equals(second.getShop().getCode())) {
            throw new IllegalArgumentException("Пропозиції одного магазину не розмічаються");
        }
        MatchScore score = pairs.score(categoryCode, pair);
        Instant now = Instant.now(clock);
        Optional<MatchLabel> existing = labels.findByOfferAIdAndOfferBId(pair.first(), pair.second());
        if (existing.isEmpty()) {
            return labels.save(new MatchLabel(pair, categoryCode, sameProduct, score, now));
        }
        existing.get().relabel(sameProduct, score, now);
        return existing.get();
    }

    /** Розмічено — за діапазонами оцінки на момент розмітки. */
    public LabelingProgress progress() {
        Map<ScoreBand, int[]> counts = new EnumMap<>(ScoreBand.class);
        int total = 0;
        int same = 0;
        for (MatchLabel label : labels.findAll()) {
            int[] band = counts.computeIfAbsent(ScoreBand.of(label.getScore().doubleValue()), k -> new int[2]);
            band[0]++;
            total++;
            if (label.isSameProduct()) {
                band[1]++;
                same++;
            }
        }
        Map<ScoreBand, LabelingProgress.Band> bands = new EnumMap<>(ScoreBand.class);
        for (ScoreBand band : ScoreBand.values()) {
            int[] c = counts.getOrDefault(band, new int[2]);
            bands.put(band, new LabelingProgress.Band(c[0], c[1]));
        }
        return new LabelingProgress(total, same, bands);
    }

    private static final class Reservoir {
        private int seen;
        private LabelingTask task;
    }

}
