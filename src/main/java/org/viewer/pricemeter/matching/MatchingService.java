package org.viewer.pricemeter.matching;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.viewer.pricemeter.catalog.domain.Category;
import org.viewer.pricemeter.catalog.domain.MatchStatus;
import org.viewer.pricemeter.catalog.service.CatalogService;
import org.viewer.pricemeter.catalog.service.MatchableOffer;
import org.viewer.pricemeter.catalog.service.ProductAssignment;
import org.viewer.pricemeter.matching.cluster.ClusterInput;
import org.viewer.pricemeter.matching.cluster.OfferCluster;
import org.viewer.pricemeter.matching.cluster.OfferClusterer;
import org.viewer.pricemeter.matching.normalize.ModelCode;
import org.viewer.pricemeter.matching.similarity.MatchCandidate;
import org.viewer.pricemeter.matching.similarity.MatchCandidateFactory;
import org.viewer.pricemeter.matching.similarity.MatchScore;
import org.viewer.pricemeter.matching.similarity.SimilarityScorer;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

/**
 * Зіставляє пропозиції категорії в товари: повний перерахунок з урахуванням ручних рішень.
 *
 * <p>Ідентифікатори товарів зберігаються між запусками: кластер отримує товар, до якого вже належить
 * більшість його пропозицій (або вручну закріплений товар); новий товар створюється лише для нового кластера.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchingService {

    /** Літери, яких немає в українській абетці, — ознака російської назви (Цитрус). */
    private static final Pattern RUSSIAN_LETTERS = Pattern.compile("[ыэъёЫЭЪЁ]");

    private final CatalogService catalog;
    private final MatchCandidateFactory candidateFactory;
    private final OfferClusterer clusterer;
    private final SimilarityScorer scorer;
    private final MatchingProperties properties;
    private final ReentrantLock lock = new ReentrantLock();

    public MatchingReport matchCategory(String categoryCode) {
        if (!lock.tryLock()) {
            throw new MatchingAlreadyRunningException();
        }
        try {
            return match(catalog.getCategory(categoryCode.strip().toUpperCase(Locale.ROOT)).getCode());
        } finally {
            lock.unlock();
        }
    }

    public List<MatchingReport> matchAll() {
        if (!lock.tryLock()) {
            throw new MatchingAlreadyRunningException();
        }
        try {
            return catalog.getCategories().stream().map(Category::getCode).sorted().map(this::match).toList();
        } finally {
            lock.unlock();
        }
    }

    /** Зіставляє вказані категорії (напр. після обходу); невідомі коди пропускаються. */
    public List<MatchingReport> matchCategories(Collection<String> categoryCodes) {
        if (!lock.tryLock()) {
            throw new MatchingAlreadyRunningException();
        }
        try {
            Set<String> known = new HashSet<>();
            catalog.getCategories().forEach(c -> known.add(c.getCode()));
            return categoryCodes.stream()
                    .map(code -> code.strip().toUpperCase(Locale.ROOT))
                    .filter(known::contains)
                    .distinct()
                    .sorted()
                    .map(this::match)
                    .toList();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Ручне рішення: пропозиція належить товару. Пропозиції товару теж закріплюються вручну, інакше наступне
     * зіставлення могло б знову розділити їх (оцінка, через яку пропозиція чекала перевірки, нижча за поріг).
     *
     * @return звіт повторного зіставлення категорії
     */
    public MatchingReport assignToProduct(long offerId, long productId) {
        return matchCategory(catalog.assignOfferManually(offerId, productId));
    }

    /**
     * Ручне рішення: пропозиція — окремий товар, автоматично її ні з чим не об'єднуємо.
     *
     * @return звіт повторного зіставлення категорії
     */
    public MatchingReport keepSeparate(long offerId) {
        return matchCategory(catalog.rejectOfferMatch(offerId));
    }

    /**
     * Скасувати ручне рішення («той самий товар» чи «окремий»): пропозиція знову зіставляється автоматично.
     *
     * @return звіт повторного зіставлення категорії
     */
    public MatchingReport resetDecision(long offerId) {
        return matchCategory(catalog.resetMatchDecision(offerId));
    }

    /**
     * Пропозиції, що чекають перевірки ({@code PENDING}), з товарами-кандидатами — оцінки рахуються на льоту.
     *
     * @param maxSuggestions скільки кандидатів показувати для кожної пропозиції
     */
    public List<PendingReview> pendingReviews(int maxSuggestions) {
        List<PendingReview> reviews = new ArrayList<>();
        for (String categoryCode : catalog.getCategories().stream().map(Category::getCode).sorted().toList()) {
            List<MatchableOffer> offers = catalog.getMatchableOffers(categoryCode);
            if (offers.stream().noneMatch(MatchingService::awaitsReview)) {
                continue;
            }
            List<MatchCandidate> candidates = buildCandidates(offers);
            Map<Long, List<PendingReview.Member>> productOffers = new HashMap<>();
            for (MatchableOffer o : offers) {
                if (o.productId() != null) {
                    productOffers.computeIfAbsent(o.productId(), id -> new ArrayList<>())
                            .add(new PendingReview.Member(o.id(), o.shopCode(), o.title()));
                }
            }
            for (int i = 0; i < offers.size(); i++) {
                MatchableOffer offer = offers.get(i);
                if (!awaitsReview(offer)) {
                    continue;
                }
                ModelCode code = candidates.get(i).modelCode();
                reviews.add(new PendingReview(offer.id(), offer.shopCode(), categoryCode, offer.title(),
                        code != null ? code.full() : null,
                        suggestions(i, offers, candidates, productOffers, maxSuggestions)));
            }
        }
        return reviews;
    }

    /** Нова пропозиція теж PENDING, але без товару — вона ще не проходила зіставлення. */
    private static boolean awaitsReview(MatchableOffer offer) {
        return offer.matchStatus() == MatchStatus.PENDING && offer.productId() != null;
    }

    private List<PendingReview.Suggestion> suggestions(int index, List<MatchableOffer> offers,
                                                       List<MatchCandidate> candidates,
                                                       Map<Long, List<PendingReview.Member>> productOffers,
                                                       int limit) {
        Long ownProduct = offers.get(index).productId();
        Map<Long, MatchScore> best = new HashMap<>();
        for (int j = 0; j < offers.size(); j++) {
            Long productId = offers.get(j).productId();
            if (j == index || productId == null || productId.equals(ownProduct)) {
                continue;
            }
            MatchScore score = scorer.score(candidates.get(index), candidates.get(j));
            if (score.value() >= properties.reviewThreshold()) {
                best.merge(productId, score, (a, b) -> a.value() >= b.value() ? a : b);
            }
        }
        return best.entrySet().stream()
                .sorted(Comparator.comparingDouble((Map.Entry<Long, MatchScore> e) -> e.getValue().value())
                        .reversed()
                        .thenComparing(Map.Entry::getKey))
                .limit(limit)
                .map(e -> new PendingReview.Suggestion(e.getKey(), e.getValue().value(), e.getValue().reason(),
                        productOffers.getOrDefault(e.getKey(), List.of())))
                .toList();
    }

    private List<MatchCandidate> buildCandidates(List<MatchableOffer> offers) {
        return candidateFactory.build(OfferTexts.of(offers));
    }

    private MatchingReport match(String categoryCode) {
        long started = System.nanoTime();
        List<MatchableOffer> offers = catalog.getMatchableOffers(categoryCode);
        List<MatchCandidate> candidates = buildCandidates(offers);
        List<ClusterInput> inputs = new ArrayList<>(offers.size());
        for (int i = 0; i < offers.size(); i++) {
            inputs.add(new ClusterInput(candidates.get(i), offers.get(i).matchStatus(), offers.get(i).productId()));
        }
        Map<Long, MatchableOffer> offersById = new HashMap<>();
        offers.forEach(o -> offersById.put(o.id(), o));

        List<OfferCluster> clusters = clusterer.cluster(inputs);
        catalog.applyMatching(categoryCode, assignProducts(clusters, offersById));

        MatchingReport report = report(categoryCode, offers.size(), clusters);
        log.info("Зіставлення {}: {} за {} мс", categoryCode, report, (System.nanoTime() - started) / 1_000_000);
        return report;
    }

    /**
     * Першими обирають товар кластери з вручну закріпленими пропозиціями, далі — більші: при розпаді кластера товар
     * лишається закріпленій групі або більшій частині.
     */
    private static List<ProductAssignment> assignProducts(List<OfferCluster> clusters,
                                                          Map<Long, MatchableOffer> offersById) {
        List<OfferCluster> ordered = clusters.stream()
                .sorted(Comparator.comparing((OfferCluster c) -> !hasManual(c))
                        .thenComparing(Comparator.comparingInt((OfferCluster c) -> c.members().size()).reversed())
                        .thenComparingLong(c -> firstOfferId(c)))
                .toList();
        Set<Long> usedProducts = new HashSet<>();
        List<ProductAssignment> assignments = new ArrayList<>(ordered.size());
        for (OfferCluster cluster : ordered) {
            Long productId = chooseProduct(cluster);
            if (productId != null && !usedProducts.add(productId)) {
                productId = null;
            }
            OfferCluster.Member representative = representative(cluster, offersById);
            MatchCandidate candidate = representative.input().candidate();
            ModelCode code = candidate.modelCode();
            assignments.add(new ProductAssignment(
                    productId,
                    offersById.get(candidate.offerId()).title(),
                    candidate.brand(),
                    code != null ? code.full() : null,
                    cluster.members().stream()
                            .map(m -> new ProductAssignment.OfferMatch(m.input().candidate().offerId(), m.status(),
                                    m.confidence() != null
                                            ? BigDecimal.valueOf(m.confidence()).setScale(3, RoundingMode.HALF_UP)
                                            : null))
                            .toList()));
        }
        return assignments;
    }

    private static boolean hasManual(OfferCluster cluster) {
        return cluster.members().stream().anyMatch(m -> m.status() == MatchStatus.MANUAL);
    }

    /** Вручну закріплений товар, інакше — товар більшості пропозицій (за рівності — менший id). */
    private static Long chooseProduct(OfferCluster cluster) {
        for (OfferCluster.Member member : cluster.members()) {
            if (member.status() == MatchStatus.MANUAL && member.input().productId() != null) {
                return member.input().productId();
            }
        }
        Map<Long, Integer> votes = new HashMap<>();
        cluster.members().stream()
                .map(m -> m.input().productId())
                .filter(Objects::nonNull)
                .forEach(id -> votes.merge(id, 1, Integer::sum));
        return votes.entrySet().stream()
                .max(Map.Entry.<Long, Integer>comparingByValue()
                        .thenComparing(Map.Entry.comparingByKey(Comparator.reverseOrder())))
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    /**
     * Пропозиція, чия назва й код стають назвою й кодом товару: спершу з кодом від магазину ({@code mpn}),
     * далі — українська назва, далі — коротша.
     */
    private static OfferCluster.Member representative(OfferCluster cluster, Map<Long, MatchableOffer> offersById) {
        return cluster.members().stream()
                .min(Comparator
                        .comparing((OfferCluster.Member m) -> {
                            ModelCode code = m.input().candidate().modelCode();
                            return code == null || code.source() != ModelCode.Source.MPN;
                        })
                        .thenComparing(m -> RUSSIAN_LETTERS.matcher(title(m, offersById)).find())
                        .thenComparingInt(m -> title(m, offersById).length())
                        .thenComparingLong(m -> m.input().candidate().offerId()))
                .orElseThrow();
    }

    private static String title(OfferCluster.Member member, Map<Long, MatchableOffer> offersById) {
        return offersById.get(member.input().candidate().offerId()).title();
    }

    private static long firstOfferId(OfferCluster cluster) {
        return cluster.members().stream().mapToLong(m -> m.input().candidate().offerId()).min().orElse(0);
    }

    private static MatchingReport report(String categoryCode, int offers, List<OfferCluster> clusters) {
        int multiShop = 0;
        int autoMatched = 0;
        int pending = 0;
        for (OfferCluster cluster : clusters) {
            long shops = cluster.members().stream().map(m -> m.input().candidate().shopCode()).distinct().count();
            if (shops > 1) {
                multiShop++;
            }
            for (OfferCluster.Member member : cluster.members()) {
                if (member.status() == MatchStatus.AUTO && cluster.members().size() > 1) {
                    autoMatched++;
                } else if (member.status() == MatchStatus.PENDING) {
                    pending++;
                }
            }
        }
        return new MatchingReport(categoryCode, offers, clusters.size(), multiShop, autoMatched, pending);
    }

}
