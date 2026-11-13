package org.viewer.pricemeter.pricehistory.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.viewer.pricemeter.common.Period;
import org.viewer.pricemeter.common.config.TimeConfig;
import org.viewer.pricemeter.pricehistory.domain.PriceSnapshot;
import org.viewer.pricemeter.pricehistory.repository.PriceSnapshotRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PriceHistoryService {

    private final PriceSnapshotRepository snapshots;
    private final Clock clock;

    /**
     * Записує спостереження ціни. Якщо стан не змінився, подовжує поточний інтервал
     * замість створення нового рядка.
     */
    @Transactional
    public RecordOutcome record(Long offerId, PriceObservation observation) {
        Optional<PriceSnapshot> latest = snapshots.findTopByOfferIdOrderByValidFromDesc(offerId);

        if (latest.isPresent()) {
            PriceSnapshot current = latest.get();
            if (observation.observedAt().isBefore(current.getValidFrom())) {
                log.warn("Ignoring out-of-order price observation for offer {}: {} is before {}",
                        offerId, observation.observedAt(), current.getValidFrom());
                return RecordOutcome.OUT_OF_ORDER;
            }
            if (current.hasSameState(observation.price(), observation.oldPrice(), observation.available())) {
                current.confirmAt(observation.observedAt());
                return RecordOutcome.UNCHANGED;
            }
        }

        snapshots.save(new PriceSnapshot(offerId, observation.price(), observation.oldPrice(),
                observation.available(), observation.observedAt()));
        return RecordOutcome.NEW_STATE;
    }

    public Optional<PriceSnapshot> getLatest(Long offerId) {
        return snapshots.findTopByOfferIdOrderByValidFromDesc(offerId);
    }

    public List<PriceSnapshot> getAll(Long offerId) {
        return snapshots.findAllByOfferIdOrderByValidFromAsc(offerId);
    }

    /**
     * Інтервали цін за період, упорядковані за часом. Перший елемент може починатися раніше
     * за період — це стан, що діяв на його початку.
     */
    public List<PriceSnapshot> getSeries(Long offerId, Period period) {
        Optional<Instant> since = period.startFrom(clock.instant());
        if (since.isEmpty()) {
            return getAll(offerId);
        }
        List<PriceSnapshot> result = new ArrayList<>();
        snapshots.findTopByOfferIdAndValidFromLessThanOrderByValidFromDesc(offerId, since.get())
                .ifPresent(result::add);
        result.addAll(snapshots.findAllByOfferIdAndValidFromGreaterThanEqualOrderByValidFromAsc(offerId, since.get()));
        return result;
    }

    /**
     * Одна точка на календарний день — стан ціни на кінець дня. Дні між спостереженнями заповнюються останнім відомим
     * станом: якщо той самий стан бачили й пізніше — день підтверджений, якщо наступне спостереження показало вже інший
     * стан — ні ({@link DailyPrice#confirmed()}). Дні після останнього підтвердження не включаються.
     */
    public List<DailyPrice> getDailySeries(Long offerId, Period period) {
        List<PriceSnapshot> series = getSeries(offerId, period);
        if (series.isEmpty()) {
            return List.of();
        }

        LocalDate firstObserved = toDate(series.getFirst().getValidFrom());
        LocalDate start = period.startFrom(clock.instant())
                .map(PriceHistoryService::toDate)
                .filter(periodStart -> periodStart.isAfter(firstObserved))
                .orElse(firstObserved);
        LocalDate lastConfirmed = toDate(series.getLast().getValidTo());
        LocalDate today = LocalDate.now(clock);
        LocalDate end = lastConfirmed.isBefore(today) ? lastConfirmed : today;

        List<DailyPrice> days = new ArrayList<>();
        int i = 0;
        for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
            Instant endOfDay = day.plusDays(1).atStartOfDay(TimeConfig.ZONE).toInstant();
            while (i + 1 < series.size() && series.get(i + 1).getValidFrom().isBefore(endOfDay)) {
                i++;
            }
            PriceSnapshot state = series.get(i);
            if (state.getValidFrom().isBefore(endOfDay)) {
                boolean confirmed = !day.isAfter(toDate(state.getValidTo()));
                days.add(new DailyPrice(day, state.getPrice(), state.getOldPrice(), state.isAvailable(), confirmed));
            }
        }
        return days;
    }

    /** Пропозиції, які зараз у наявності із заявленою старою ціною (знижкою). */
    public List<Long> getOfferIdsWithCurrentDiscount() {
        return snapshots.findOfferIdsWithCurrentDiscount();
    }

    /**
     * Останній відомий стан ціни для кожної з пропозицій; пропозиції без історії пропускаються.
     */
    public Map<Long, PriceSnapshot> getCurrentPrices(Collection<Long> offerIds) {
        if (offerIds.isEmpty()) {
            return Map.of();
        }
        return snapshots.findLatestForOffers(offerIds).stream()
                .collect(Collectors.toMap(PriceSnapshot::getOfferId, Function.identity()));
    }

    private static LocalDate toDate(Instant instant) {
        return LocalDate.ofInstant(instant, TimeConfig.ZONE);
    }

}
