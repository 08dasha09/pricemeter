package org.viewer.pricemeter.matching.cluster;

import org.junit.jupiter.api.Test;
import org.viewer.pricemeter.catalog.domain.MatchStatus;
import org.viewer.pricemeter.matching.MatchingProperties;
import org.viewer.pricemeter.matching.normalize.BrandExtractor;
import org.viewer.pricemeter.matching.normalize.ModelCodeExtractor;
import org.viewer.pricemeter.matching.normalize.TitleNormalizer;
import org.viewer.pricemeter.matching.similarity.MatchCandidate;
import org.viewer.pricemeter.matching.similarity.MatchCandidateFactory;
import org.viewer.pricemeter.matching.similarity.OfferText;
import org.viewer.pricemeter.matching.similarity.SimilarityScorer;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class OfferClustererTest {

    private static final String WASHERS = "WASHING_MACHINES";

    private final BrandExtractor brands = new BrandExtractor();
    private final MatchCandidateFactory factory = new MatchCandidateFactory(
            new TitleNormalizer(), brands, new ModelCodeExtractor(brands));
    private final OfferClusterer clusterer = new OfferClusterer(
            new SimilarityScorer(), new MatchingProperties(0.9, 0.65));

    private final List<OfferText> offers = new ArrayList<>();
    private final List<MatchStatus> statuses = new ArrayList<>();
    private final List<Long> productIds = new ArrayList<>();

    @Test
    void mergesSameModelFromDifferentShops() {
        long citrus = offer("CITRUS", "Пральна машина LG F2Y2HS5WE", "LG", null);
        long moyo = offer("MOYO", "Пральна машина LG F2Y2HS5WE", "LG", "F2Y2HS5WE");
        long epicentr = offer("EPICENTR", "Пральна машина LG F2Y2HS5WE", "LG", null);
        long other = offer("EPICENTR", "Пральна машина LG F2Y2HS6WE", "LG", null);

        List<OfferCluster> clusters = cluster();

        assertThat(groups(clusters)).containsExactlyInAnyOrder(Set.of(citrus, moyo, epicentr), Set.of(other));
        OfferCluster.Member member = member(clusters, moyo);
        assertThat(member.status()).isEqualTo(MatchStatus.AUTO);
        assertThat(member.confidence()).isGreaterThanOrEqualTo(0.95);
        // окремий товар без можливих збігів
        assertThat(member(clusters, other).status()).isEqualTo(MatchStatus.AUTO);
        assertThat(member(clusters, other).confidence()).isNull();
    }

    @Test
    void neverPutsTwoOffersOfOneShopTogether() {
        // Цитрус: та сама модель за українською й російською адресою
        long citrusUa = offer("CITRUS", "Пральна машина LG F2Y2NS3WE", "LG", null);
        long citrusRu = offer("CITRUS", "Стиральная машина LG F2Y2NS3WE", "LG", null);
        long epicentr = offer("EPICENTR", "Пральна машина LG F2Y2NS3WE", "LG", null);

        List<OfferCluster> clusters = cluster();

        assertThat(groups(clusters)).containsExactlyInAnyOrder(Set.of(citrusUa, epicentr), Set.of(citrusRu));
        // сильний збіг, заборонений обмеженням магазину, — на ручну перевірку
        assertThat(member(clusters, citrusRu).status()).isEqualTo(MatchStatus.PENDING);
        assertThat(member(clusters, citrusRu).confidence()).isGreaterThanOrEqualTo(0.9);
    }

    @Test
    void reviewRangeGoesToPendingWithoutMerging() {
        long full = offer("ELDORADO", "Пральна машина Samsung WW80T4040CE1UA", "SAMSUNG", null);
        long prefix = offer("EPICENTR", "Пральна машина Samsung WW80T4040", "Samsung", null);

        List<OfferCluster> clusters = cluster();

        assertThat(groups(clusters)).containsExactlyInAnyOrder(Set.of(full), Set.of(prefix));
        assertThat(member(clusters, full).status()).isEqualTo(MatchStatus.PENDING);
        assertThat(member(clusters, prefix).status()).isEqualTo(MatchStatus.PENDING);
        assertThat(member(clusters, prefix).confidence()).isBetween(0.65, 0.9);
    }

    @Test
    void colourVariantsAreSeparateWithoutReview() {
        long silver = offer("ELDORADO", "Посудомийна машина GORENJE GS520E15S", "GORENJE", null);
        long white = offer("EPICENTR", "Посудомийна машина Gorenje GS520E15W", "Gorenje", null);

        List<OfferCluster> clusters = cluster();

        assertThat(groups(clusters)).containsExactlyInAnyOrder(Set.of(silver), Set.of(white));
        assertThat(member(clusters, silver).status()).isEqualTo(MatchStatus.AUTO);
    }

    @Test
    void offerWithoutBrandIsComparedWithBrandedOnes() {
        long branded = offer("MOYO", "Холодильник Gorenje RF414EPS4", "Gorenje", "RF414EPS4");
        long unbranded = offer("CITRUS", "Холодильник Gorenje RF414EPS4", null, null);

        assertThat(groups(cluster())).containsExactly(Set.of(branded, unbranded));
    }

    @Test
    void keepsManualGroupsAndRejectedOffers() {
        // вручну об'єднано попри різні коди
        long manualA = offer("MOYO", "Пральна машина LG F2X5PS5W", "LG", "F2X5PS5W", MatchStatus.MANUAL, 10L);
        long manualB = offer("CITRUS", "Пральна машина LG F2X5PS5W/UA", "LG", null, MatchStatus.MANUAL, 10L);
        // вручну виділено в окремий товар — нова пропозиція може приєднатись лише до одного з них
        long manualC = offer("ELDORADO", "Пральна машина LG F2Y2HS5WE", "LG", null, MatchStatus.MANUAL, 20L);
        long manualD = offer("MOYO", "Пральна машина LG F2Y2HS5WE", "LG", "F2Y2HS5WE", MatchStatus.MANUAL, 30L);
        long fresh = offer("EPICENTR", "Пральна машина LG F2Y2HS5WE", "LG", null);
        long rejected = offer("EPICENTR", "Пральна машина LG F2X5PS5W", "LG", null, MatchStatus.REJECTED, 40L);

        List<OfferCluster> clusters = cluster();

        assertThat(groups(clusters)).containsExactlyInAnyOrder(
                Set.of(manualA, manualB), Set.of(manualC, fresh), Set.of(manualD), Set.of(rejected));
        assertThat(member(clusters, manualA).status()).isEqualTo(MatchStatus.MANUAL);
        assertThat(member(clusters, fresh).status()).isEqualTo(MatchStatus.AUTO);
        assertThat(member(clusters, rejected).status()).isEqualTo(MatchStatus.REJECTED);
    }

    private List<OfferCluster> cluster() {
        List<MatchCandidate> candidates = factory.build(offers);
        List<ClusterInput> inputs = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            inputs.add(new ClusterInput(candidates.get(i), statuses.get(i), productIds.get(i)));
        }
        return clusterer.cluster(inputs);
    }

    private long offer(String shop, String title, String brandRaw, String mpn) {
        return offer(shop, title, brandRaw, mpn, MatchStatus.PENDING, null);
    }

    private long offer(String shop, String title, String brandRaw, String mpn, MatchStatus status, Long productId) {
        long id = offers.size() + 1;
        offers.add(new OfferText(id, shop, WASHERS, title, brandRaw, mpn, null));
        statuses.add(status);
        productIds.add(productId);
        return id;
    }

    private static List<Set<Long>> groups(List<OfferCluster> clusters) {
        return clusters.stream()
                .map(c -> c.members().stream().map(m -> m.input().candidate().offerId()).collect(Collectors.toSet()))
                .toList();
    }

    private static OfferCluster.Member member(List<OfferCluster> clusters, long offerId) {
        return clusters.stream()
                .flatMap(c -> c.members().stream())
                .filter(m -> m.input().candidate().offerId() == offerId)
                .findFirst()
                .orElseThrow();
    }

}
