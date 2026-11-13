package org.viewer.pricemeter.pricehistory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.viewer.pricemeter.TestcontainersConfiguration;
import org.viewer.pricemeter.catalog.service.CatalogService;
import org.viewer.pricemeter.catalog.service.OfferUpsertCommand;
import org.viewer.pricemeter.common.Period;
import org.viewer.pricemeter.common.config.TimeConfig;
import org.viewer.pricemeter.pricehistory.domain.PriceSnapshot;
import org.viewer.pricemeter.pricehistory.service.DailyPrice;
import org.viewer.pricemeter.pricehistory.service.PriceHistoryService;
import org.viewer.pricemeter.pricehistory.service.PriceObservation;
import org.viewer.pricemeter.pricehistory.service.RecordOutcome;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, CatalogService.class, PriceHistoryService.class,
        PriceHistoryServiceTest.FixedClock.class})
class PriceHistoryServiceTest {

    /** 2026-10-01 11:00 за Києвом. */
    private static final Instant T0 = Instant.parse("2026-10-01T08:00:00Z");

    /** «Зараз» для тестів: 2026-10-10 15:00 за Києвом. */
    private static final Instant NOW = Instant.parse("2026-10-10T12:00:00Z");

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClock {
        @Bean
        Clock clock() {
            return Clock.fixed(NOW, TimeConfig.ZONE);
        }
    }

    @Autowired
    private CatalogService catalog;

    @Autowired
    private PriceHistoryService history;

    private Long offerId;

    @BeforeEach
    void createOffer() {
        offerId = catalog.upsertOffer(new OfferUpsertCommand(
                "ROZETKA", "WASHING_MACHINES", "P-1", "https://rozetka.com.ua/p-1/",
                "Samsung WW80T4040CE1UA", null, null, null, T0)).offer().getId();
    }

    @Test
    void firstObservationCreatesSnapshot() {
        RecordOutcome outcome = history.record(offerId, observation("15999", null, hours(0)));

        assertThat(outcome).isEqualTo(RecordOutcome.NEW_STATE);
        PriceSnapshot latest = history.getLatest(offerId).orElseThrow();
        assertThat(latest.getPrice()).isEqualByComparingTo("15999");
        assertThat(latest.getValidFrom()).isEqualTo(T0);
        assertThat(latest.getValidTo()).isEqualTo(T0);
    }

    @Test
    void unchangedPriceExtendsIntervalInsteadOfNewRow() {
        history.record(offerId, observation("15999", "18999", hours(0)));
        // той самий стан, але з іншим масштабом BigDecimal
        RecordOutcome outcome = history.record(offerId, observation("15999.00", "18999.0", hours(12)));

        assertThat(outcome).isEqualTo(RecordOutcome.UNCHANGED);
        List<PriceSnapshot> all = history.getAll(offerId);
        assertThat(all).hasSize(1);
        assertThat(all.getFirst().getValidFrom()).isEqualTo(T0);
        assertThat(all.getFirst().getValidTo()).isEqualTo(hours(12));
    }

    @Test
    void priceChangeCreatesNewInterval() {
        history.record(offerId, observation("15999", null, hours(0)));
        history.record(offerId, observation("15999", null, hours(12)));
        history.record(offerId, observation("14499", "15999", hours(24)));

        List<PriceSnapshot> all = history.getAll(offerId);
        assertThat(all).extracting(PriceSnapshot::getPrice)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("15999"), new BigDecimal("14499"));
        assertThat(all.get(0).getValidTo()).isEqualTo(hours(12));
        assertThat(all.get(1).getValidFrom()).isEqualTo(hours(24));
        assertThat(all.get(1).getOldPrice()).isEqualByComparingTo("15999");
    }

    @Test
    void oldPriceChangeAloneIsANewState() {
        history.record(offerId, observation("15999", null, hours(0)));

        assertThat(history.record(offerId, observation("15999", "19999", hours(12))))
                .isEqualTo(RecordOutcome.NEW_STATE);
    }

    @Test
    void availabilityChangeIsANewState() {
        history.record(offerId, observation("15999", null, hours(0)));

        RecordOutcome outcome = history.record(offerId, new PriceObservation(null, null, false, hours(12)));

        assertThat(outcome).isEqualTo(RecordOutcome.NEW_STATE);
        PriceSnapshot latest = history.getLatest(offerId).orElseThrow();
        assertThat(latest.isAvailable()).isFalse();
        assertThat(latest.getPrice()).isNull();
    }

    @Test
    void outOfOrderObservationIsIgnored() {
        history.record(offerId, observation("15999", null, hours(24)));

        assertThat(history.record(offerId, observation("12999", null, hours(0))))
                .isEqualTo(RecordOutcome.OUT_OF_ORDER);
        assertThat(history.getAll(offerId)).hasSize(1);
    }

    @Test
    void oldPriceNotAbovePriceIsDiscarded() {
        assertThat(observation("15999", "15999", T0).oldPrice()).isNull();
        assertThat(observation("15999", "14000", T0).oldPrice()).isNull();
        assertThat(observation("15999", "17999", T0).oldPrice()).isEqualByComparingTo("17999");
    }

    @Test
    void rejectsInvalidObservations() {
        assertThatThrownBy(() -> new PriceObservation(null, null, true, T0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> observation("0", null, T0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PriceObservation(BigDecimal.TEN, null, true, null))
                .isInstanceOf(NullPointerException.class);
    }

    /**
     * Історія для тестів рядів: 15999 з 1 по 3 жовтня, 14499 з 5-го, два зміни 6-го
     * (13000 вранці, 13500 увечері), останнє підтвердження 8-го.
     */
    private void recordTypicalHistory() {
        history.record(offerId, observation("15999", null, kyiv("2026-10-01T11:00")));
        history.record(offerId, observation("15999", null, kyiv("2026-10-03T11:00")));
        history.record(offerId, observation("14499", "15999", kyiv("2026-10-05T10:00")));
        history.record(offerId, observation("13000", "15999", kyiv("2026-10-06T09:00")));
        history.record(offerId, observation("13500", "15999", kyiv("2026-10-06T20:00")));
        history.record(offerId, observation("13500", "15999", kyiv("2026-10-08T20:00")));
    }

    @Test
    void dailySeriesTakesEndOfDayStateAndFillsGaps() {
        recordTypicalHistory();

        List<DailyPrice> days = history.getDailySeries(offerId, Period.ALL);

        assertThat(days).extracting(DailyPrice::date)
                .containsExactly(date(1), date(2), date(3), date(4), date(5), date(6), date(7), date(8));
        assertThat(days).extracting(DailyPrice::price)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(bd("15999"), bd("15999"), bd("15999"), bd("15999"),
                        bd("14499"), bd("13500"), bd("13500"), bd("13500"));
        assertThat(days.get(5).oldPrice()).isEqualByComparingTo("15999");
        // 2-ге — між двома спостереженнями того самого стану; 4-те не обходили, а 5-го ціна вже інша — невідомо;
        // 7-ме — 13 500 підтверджено 8-го
        assertThat(days).extracting(DailyPrice::confirmed)
                .containsExactly(true, true, true, false, true, true, true, true);
    }

    @Test
    void dailySeriesForPeriodStartsAtPeriodStartWithCarriedState() {
        recordTypicalHistory();

        // тиждень до 10.10 15:00 → початок 03.10
        List<DailyPrice> days = history.getDailySeries(offerId, Period.WEEK);

        assertThat(days.getFirst().date()).isEqualTo(date(3));
        assertThat(days.getFirst().price()).isEqualByComparingTo("15999");
        assertThat(days.getLast().date()).isEqualTo(date(8));
        assertThat(days).hasSize(6);
    }

    @Test
    void seriesForPeriodIncludesStateInEffectAtPeriodStart() {
        recordTypicalHistory();

        assertThat(history.getSeries(offerId, Period.WEEK)).extracting(PriceSnapshot::getPrice)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(bd("15999"), bd("14499"), bd("13000"), bd("13500"));
        assertThat(history.getSeries(offerId, Period.ALL)).hasSize(4);
    }

    @Test
    void dayBoundaryIsKyivTime() {
        // 22:30 UTC 1 жовтня — це вже 01:30 2 жовтня за Києвом
        history.record(offerId, observation("15999", null, Instant.parse("2026-10-01T22:30:00Z")));

        assertThat(history.getDailySeries(offerId, Period.ALL))
                .extracting(DailyPrice::date)
                .containsExactly(date(2));
    }

    @Test
    void dailySeriesIsEmptyWithoutHistory() {
        assertThat(history.getDailySeries(offerId, Period.MONTH)).isEmpty();
    }

    @Test
    void currentPricesReturnLatestStatePerOffer() {
        Long otherOfferId = catalog.upsertOffer(new OfferUpsertCommand(
                "COMFY", null, "C-1", "https://comfy.ua/c-1", "Samsung WW80T4040CE1UA",
                null, null, null, T0)).offer().getId();
        Long noHistoryOfferId = catalog.upsertOffer(new OfferUpsertCommand(
                "ALLO", null, "A-1", "https://allo.ua/a-1", "Samsung WW80T4040CE1UA",
                null, null, null, T0)).offer().getId();
        recordTypicalHistory();
        history.record(otherOfferId, observation("14999", null, kyiv("2026-10-02T12:00")));

        Map<Long, PriceSnapshot> current = history.getCurrentPrices(List.of(offerId, otherOfferId, noHistoryOfferId));

        assertThat(current).containsOnlyKeys(offerId, otherOfferId);
        assertThat(current.get(offerId).getPrice()).isEqualByComparingTo("13500");
        assertThat(current.get(otherOfferId).getPrice()).isEqualByComparingTo("14999");
        assertThat(history.getCurrentPrices(List.of())).isEmpty();
    }

    private static Instant kyiv(String localDateTime) {
        return LocalDateTime.parse(localDateTime).atZone(TimeConfig.ZONE).toInstant();
    }

    private static LocalDate date(int dayOfOctober) {
        return LocalDate.of(2026, 10, dayOfOctober);
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    @Test
    void findsOffersWithDiscountInLatestState() {
        Long ended = catalog.upsertOffer(new OfferUpsertCommand("ROZETKA", "WASHING_MACHINES", "P-2",
                "https://rozetka.com.ua/p-2/", "Bosch WAN28263UA", null, null, null, T0)).offer().getId();
        Long soldOut = catalog.upsertOffer(new OfferUpsertCommand("ROZETKA", "WASHING_MACHINES", "P-3",
                "https://rozetka.com.ua/p-3/", "LG F2Y2HS5WE", null, null, null, T0)).offer().getId();
        history.record(offerId, observation("15999", "18999", hours(0)));
        // знижка була, але закінчилась
        history.record(ended, observation("14999", "16999", hours(0)));
        history.record(ended, observation("16999", null, hours(24)));
        // зі «старою» ціною, але не в наявності
        history.record(soldOut, new PriceObservation(new BigDecimal("20999"), new BigDecimal("24999"), false, hours(0)));

        assertThat(history.getOfferIdsWithCurrentDiscount()).containsExactly(offerId);
    }

    private static PriceObservation observation(String price, String oldPrice, Instant at) {
        return new PriceObservation(new BigDecimal(price), oldPrice == null ? null : new BigDecimal(oldPrice), true, at);
    }

    private static Instant hours(long h) {
        return T0.plus(Duration.ofHours(h));
    }

}
