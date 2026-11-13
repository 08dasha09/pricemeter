package org.viewer.pricemeter.web;

import org.viewer.pricemeter.common.Period;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Перемикач періоду графіків.
 */
public final class PeriodOptions {

    /** Період → підпис кнопки, у порядку показу. */
    public static final Map<Period, String> LABELS;

    /** Період, з яким графік відкривається. */
    public static final Period DEFAULT = Period.MONTH;

    static {
        Map<Period, String> labels = new LinkedHashMap<>();
        labels.put(Period.WEEK, "Тиждень");
        labels.put(Period.MONTH, "Місяць");
        labels.put(Period.QUARTER, "Квартал");
        labels.put(Period.ALL, "Весь час");
        LABELS = Collections.unmodifiableMap(labels);
    }

    private PeriodOptions() {
    }

}
