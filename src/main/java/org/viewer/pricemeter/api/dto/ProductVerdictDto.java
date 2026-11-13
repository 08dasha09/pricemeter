package org.viewer.pricemeter.api.dto;

import org.viewer.pricemeter.analytics.ProductVerdicts;
import org.viewer.pricemeter.common.Period;

import java.util.List;

/**
 * Вердикти товару.
 *
 * @param best   вердикт найнижчої ціни в наявності — підсумок для товару; {@code null}, якщо ніде немає
 * @param offers вердикти всіх пропозицій, у порядку від найдешевшої наявної
 */
public record ProductVerdictDto(Long productId, Period period, VerdictDto best, List<VerdictDto> offers) {

    public static ProductVerdictDto of(Long productId, Period period, ProductVerdicts verdicts) {
        return new ProductVerdictDto(productId, period,
                verdicts.best().map(v -> VerdictDto.of(verdicts.bestOfferId(), v)).orElse(null),
                verdicts.byOffer().entrySet().stream().map(e -> VerdictDto.of(e.getKey(), e.getValue())).toList());
    }

}
