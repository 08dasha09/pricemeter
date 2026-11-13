package org.viewer.pricemeter.matching;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Пороги рішень зіставлення ({@code matching.*}).
 *
 * @param autoThreshold   оцінка, з якої пропозиції об'єднуються автоматично ({@code T_high})
 * @param reviewThreshold оцінка, з якої можливий збіг іде на ручну перевірку ({@code T_low}); нижче — різні товари
 */
@ConfigurationProperties("matching")
public record MatchingProperties(
        @DefaultValue("0.9") double autoThreshold,
        @DefaultValue("0.65") double reviewThreshold
) {

    public MatchingProperties {
        if (reviewThreshold <= 0 || reviewThreshold > autoThreshold || autoThreshold > 1) {
            throw new IllegalArgumentException(
                    "matching thresholds must satisfy 0 < review-threshold <= auto-threshold <= 1");
        }
    }

}
