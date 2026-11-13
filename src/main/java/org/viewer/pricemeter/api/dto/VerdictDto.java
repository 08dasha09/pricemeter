package org.viewer.pricemeter.api.dto;

import org.viewer.pricemeter.analytics.HistoricalLevel;
import org.viewer.pricemeter.analytics.MarketPosition;
import org.viewer.pricemeter.analytics.PriceDrop;
import org.viewer.pricemeter.analytics.Verdict;
import org.viewer.pricemeter.common.Period;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Вердикт щодо поточної ціни пропозиції з обома ознаками.
 *
 * @param percentile       перцентиль поточної ціни за період
 * @param historyDays      днів у наявності за період
 * @param marketPosition   {@code null} — ринкова ознака недоступна
 * @param otherMedian      медіана цін інших магазинів
 * @param lastDropDate     день останнього зниження ціни за період; {@code null} — не знижувалась
 * @param lastDropPercent  глибина останнього зниження, %
 */
public record VerdictDto(
        Long offerId,
        Period period,
        Verdict.Level level,
        Verdict.Confidence confidence,
        String explanation,
        HistoricalLevel historicalLevel,
        Double percentile,
        int historyDays,
        MarketPosition marketPosition,
        Integer deviationPercent,
        BigDecimal otherMedian,
        int otherShops,
        LocalDate lastDropDate,
        Integer lastDropPercent) {

    public static VerdictDto of(Long offerId, Verdict v) {
        PriceDrop drop = v.historical().lastDrop();
        return new VerdictDto(offerId, v.historical().period(), v.level(), v.confidence(), v.explanation(),
                v.historical().level(), v.historical().statistics().currentPercentile(),
                v.historical().statistics().days(), v.market().position(), v.market().deviationPercent(),
                v.market().otherMedian(), v.market().otherShops(),
                drop != null ? drop.date() : null, drop != null ? drop.depthPercent() : null);
    }

}
