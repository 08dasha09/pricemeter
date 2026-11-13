package org.viewer.pricemeter.analytics;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.viewer.pricemeter.catalog.domain.Offer;
import org.viewer.pricemeter.catalog.service.CatalogService;
import org.viewer.pricemeter.common.Period;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Вердикт щодо поточної ціни пропозиції за двома незалежними ознаками — історичною (перцентиль у розподілі цін
 * магазину за період) і ринковою (відхилення від медіани інших магазинів):
 *
 * <pre>
 * Історія \ Ринок | нижче ринку        | на рівні ринку  | вище ринку
 * &lt; P25           | нижча за звичайну  | нижча           | звичайна (в інших дешевше)
 * P25–P75         | нижча за звичайну  | звичайна        | завищена
 * &gt; P75           | звичайна           | завищена        | завищена
 * </pre>
 *
 * Одна з ознак недоступна → вердикт за іншою зі зниженою довірою; обидві — вердикту немає.
 */
@Service
@RequiredArgsConstructor
public class VerdictService {

    private static final Map<Period, String> PERIOD_NAMES = Map.of(
            Period.WEEK, "за тиждень",
            Period.MONTH, "за місяць",
            Period.QUARTER, "за квартал",
            Period.ALL, "за весь час");

    private final CatalogService catalog;
    private final PriceAnalyticsService analytics;
    private final ProductMarketService markets;

    public Verdict forOffer(Long offerId, Period period) {
        Offer offer = catalog.getOffer(offerId);
        HistoricalAssessment historical = analytics.assessHistory(offerId, period);
        MarketAssessment market = MarketAssessment.UNAVAILABLE;
        if (offer.getProduct() != null) {
            List<MarketOffer> offers = markets.market(offer.getProduct().getId()).offers();
            market = offers.stream()
                    .filter(o -> o.id() == offerId)
                    .findFirst()
                    .map(self -> assessMarket(self, offers))
                    .orElse(MarketAssessment.UNAVAILABLE);
        }
        return decide(historical, market);
    }

    /**
     * Вердикти всіх пропозицій товару: ринок рахується один раз. Підсумок товару — вердикт найнижчої ціни в наявності.
     * Існування товару перевіряє викликач.
     */
    public ProductVerdicts forProduct(Long productId, Period period) {
        ProductMarket market = markets.market(productId);
        Map<Long, Verdict> byOffer = new LinkedHashMap<>();
        for (MarketOffer offer : market.offers()) {
            byOffer.put(offer.id(), decide(analytics.assessHistory(offer.id(), period),
                    assessMarket(offer, market.offers())));
        }
        Long best = market.offers().stream()
                .filter(MarketOffer::purchasable)
                .findFirst()
                .map(MarketOffer::id)
                .orElse(null);
        return new ProductVerdicts(market, byOffer, best);
    }

    /** Медіана наявних пропозицій інших магазинів (інша пропозиція того ж магазину ринком не вважається). */
    static MarketAssessment assessMarket(MarketOffer self, List<MarketOffer> offers) {
        if (!self.purchasable()) {
            return MarketAssessment.UNAVAILABLE;
        }
        List<MarketOffer> others = offers.stream()
                .filter(o -> o.purchasable() && !o.shopCode().equals(self.shopCode()))
                .toList();
        if (others.isEmpty()) {
            return MarketAssessment.UNAVAILABLE;
        }
        BigDecimal median = PriceStats.median(others.stream().map(MarketOffer::price).toList());
        Integer deviation = PriceStats.deviationPercent(self.price(), median);
        int otherShops = (int) others.stream().map(MarketOffer::shopCode).distinct().count();
        return new MarketAssessment(median, otherShops, deviation, MarketPosition.of(deviation));
    }

    static Verdict decide(HistoricalAssessment historical, MarketAssessment market) {
        HistoricalLevel history = historical.level();
        if (history == HistoricalLevel.NOT_AVAILABLE) {
            return new Verdict(Verdict.Level.UNKNOWN, Verdict.Confidence.NONE, historical, market,
                    "Зараз немає в наявності");
        }
        Verdict.Level level;
        Verdict.Confidence confidence;
        if (history.known() && market.known()) {
            level = table(history, market.position());
            confidence = Verdict.Confidence.HIGH;
        } else if (history.known()) {
            level = switch (history) {
                case LOW -> Verdict.Level.BELOW_USUAL;
                case HIGH -> Verdict.Level.ABOVE_USUAL;
                default -> Verdict.Level.USUAL;
            };
            confidence = Verdict.Confidence.REDUCED;
        } else if (market.known()) {
            level = switch (market.position()) {
                case BELOW_MARKET -> Verdict.Level.BELOW_USUAL;
                case ABOVE_MARKET -> Verdict.Level.ABOVE_USUAL;
                case AT_MARKET -> Verdict.Level.USUAL;
            };
            confidence = Verdict.Confidence.REDUCED;
        } else {
            level = Verdict.Level.UNKNOWN;
            confidence = Verdict.Confidence.NONE;
        }
        return new Verdict(level, confidence, historical, market, explain(historical, market));
    }

    private static Verdict.Level table(HistoricalLevel history, MarketPosition market) {
        return switch (history) {
            case LOW -> market == MarketPosition.ABOVE_MARKET
                    ? Verdict.Level.USUAL_CHEAPER_ELSEWHERE : Verdict.Level.BELOW_USUAL;
            case NORMAL -> switch (market) {
                case BELOW_MARKET -> Verdict.Level.BELOW_USUAL;
                case AT_MARKET -> Verdict.Level.USUAL;
                case ABOVE_MARKET -> Verdict.Level.ABOVE_USUAL;
            };
            case HIGH -> market == MarketPosition.BELOW_MARKET ? Verdict.Level.USUAL : Verdict.Level.ABOVE_USUAL;
            default -> throw new IllegalArgumentException("Historical level is not known: " + history);
        };
    }

    private static final DateTimeFormatter DROP_DATE = DateTimeFormatter.ofPattern("dd.MM");

    /**
     * «На 12% дешевше за медіану інших магазинів (3), 18-й перцентиль за квартал, останнє зниження — 12.10, на 8%
     * (6 днів тому)».
     */
    static String explain(HistoricalAssessment historical, MarketAssessment market) {
        List<String> parts = new ArrayList<>();
        if (market.known()) {
            int deviation = market.deviationPercent();
            String shops = " (" + market.otherShops() + ")";
            parts.add(switch (market.position()) {
                case BELOW_MARKET -> "на " + -deviation + "% дешевше за медіану інших магазинів" + shops;
                case ABOVE_MARKET -> "на " + deviation + "% дорожче за медіану інших магазинів" + shops;
                case AT_MARKET -> "на рівні інших магазинів" + shops;
            });
        } else {
            parts.add("інших магазинів з цим товаром у наявності немає");
        }
        String period = PERIOD_NAMES.getOrDefault(historical.period(), "");
        if (historical.level().known()) {
            parts.add(Math.round(historical.statistics().currentPercentile()) + "-й перцентиль " + period);
        } else {
            parts.add("історії замало: " + historical.statistics().days() + " з " + HistoricalLevel.MIN_DAYS
                    + " днів " + period);
        }
        PriceDrop drop = historical.lastDrop();
        if (drop != null) {
            parts.add("останнє зниження — " + DROP_DATE.format(drop.date()) + ", на " + drop.depthPercent() + "% ("
                    + daysAgo(drop.daysAgo()) + ")");
        }
        String text = String.join(", ", parts);
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    /** «сьогодні», «вчора», «2 дні тому», «5 днів тому», «21 день тому». */
    static String daysAgo(long days) {
        if (days <= 0) {
            return "сьогодні";
        }
        if (days == 1) {
            return "вчора";
        }
        long lastTwo = days % 100;
        long last = days % 10;
        String word = lastTwo >= 11 && lastTwo <= 14 ? "днів" : last == 1 ? "день" : last >= 2 && last <= 4 ? "дні" : "днів";
        return days + " " + word + " тому";
    }

}
