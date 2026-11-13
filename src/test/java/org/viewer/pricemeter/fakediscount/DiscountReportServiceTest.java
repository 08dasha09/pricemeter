package org.viewer.pricemeter.fakediscount;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.viewer.pricemeter.TestcontainersConfiguration;
import org.viewer.pricemeter.analytics.ProductMarketService;
import org.viewer.pricemeter.catalog.domain.Offer;
import org.viewer.pricemeter.catalog.domain.Product;
import org.viewer.pricemeter.catalog.repository.OfferRepository;
import org.viewer.pricemeter.catalog.repository.ProductRepository;
import org.viewer.pricemeter.catalog.service.CatalogService;
import org.viewer.pricemeter.catalog.service.OfferUpsertCommand;
import org.viewer.pricemeter.common.config.TimeConfig;
import org.viewer.pricemeter.fakediscount.rule.InflatedOldPriceRule;
import org.viewer.pricemeter.fakediscount.rule.PromoNotBelowMarketRule;
import org.viewer.pricemeter.fakediscount.rule.SamePriceElsewhereRule;
import org.viewer.pricemeter.pricehistory.service.PriceHistoryService;
import org.viewer.pricemeter.pricehistory.service.PriceObservation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, TimeConfig.class, CatalogService.class, PriceHistoryService.class,
        ProductMarketService.class, FakeDiscountDetector.class, PromoNotBelowMarketRule.class,
        InflatedOldPriceRule.class, SamePriceElsewhereRule.class, DiscountReportService.class})
class DiscountReportServiceTest {

    private static final Instant T0 = Instant.parse("2026-10-11T01:00:00Z");

    @Autowired
    private DiscountReportService report;

    @Autowired
    private CatalogService catalog;

    @Autowired
    private PriceHistoryService history;

    @Autowired
    private ProductRepository products;

    @Autowired
    private OfferRepository offers;

    @Test
    void findsDiscountsAndCountsThemPerShop() {
        Product lg = products.save(new Product(catalog.getCategory("WASHING_MACHINES"), "LG F2Y2HS5WE"));
        // Moyo: «−23%», але в Цитрусі та Епіцентрі ті самі 20 000 без акції — фіктивна
        Offer moyo = offer("MOYO", "M-1", lg, "20000", "26000");
        offer("CITRUS", "C-1", lg, "20000", null);
        offer("EPICENTR", "E-1", lg, "20100", null);
        // Цитрус: інший товар, справді дешевше за Moyo
        Product gorenje = products.save(new Product(catalog.getCategory("REFRIGERATORS"), "Gorenje RK4182PS4"));
        Offer citrusPromo = offer("CITRUS", "C-2", gorenje, "15000", "17000");
        offer("MOYO", "M-2", gorenje, "18000", null);
        // ще не зіставлена — порівняти нема з чим
        Offer lonely = offer("ELDORADO", "D-1", null, "9999", "12999");

        List<DiscountFinding> findings = report.findings();

        assertThat(findings).extracting(f -> f.offer().id())
                .containsExactly(moyo.getId(), citrusPromo.getId(), lonely.getId());
        assertThat(findings).extracting(f -> f.assessment().level()).containsExactly(
                DiscountAssessment.Level.FAKE, DiscountAssessment.Level.GENUINE, DiscountAssessment.Level.UNKNOWN);
        assertThat(findings.getFirst().productId()).isEqualTo(lg.getId());
        assertThat(findings.getFirst().categoryCode()).isEqualTo("WASHING_MACHINES");
        assertThat(findings.getLast().productId()).isNull();

        List<ShopDiscountStats> stats = report.shopStats(findings);
        assertThat(stats).extracting(ShopDiscountStats::shopCode).containsExactly("MOYO", "CITRUS", "ELDORADO");
        assertThat(stats.getFirst().untrustworthyPercent()).isEqualTo(100);
        assertThat(stats.get(1).untrustworthyPercent()).isZero();
        assertThat(stats.getLast().untrustworthyPercent()).isNull();
        assertThat(stats.getLast().declared()).isEqualTo(1);
    }

    private Offer offer(String shop, String externalId, Product product, String price, String oldPrice) {
        Offer offer = catalog.upsertOffer(new OfferUpsertCommand(shop, product != null
                ? product.getCategory().getCode() : "WASHING_MACHINES", externalId,
                "https://shop/" + externalId, "Товар " + externalId, null, null, null, T0)).offer();
        offer.setProduct(product);
        offers.flush();
        history.record(offer.getId(), new PriceObservation(new BigDecimal(price),
                oldPrice != null ? new BigDecimal(oldPrice) : null, true, T0));
        return offer;
    }

}
