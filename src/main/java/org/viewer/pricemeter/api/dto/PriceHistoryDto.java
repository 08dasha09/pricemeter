package org.viewer.pricemeter.api.dto;

import org.viewer.pricemeter.common.Period;

import java.util.List;

/**
 * Денний ряд цін пропозиції за період.
 */
public record PriceHistoryDto(Long offerId, Period period, List<PricePointDto> points) {
}
