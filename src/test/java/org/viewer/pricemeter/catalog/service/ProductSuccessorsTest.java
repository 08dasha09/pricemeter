package org.viewer.pricemeter.catalog.service;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Наступник товару, що зникає після зіставлення: туди, куди перейшла більшість його пропозицій.
 */
class ProductSuccessorsTest {

    @Test
    void emptiedProductGoesToWhereMostOffersWent() {
        // товар 1: пропозиції 10, 11 → товар 5; 12 → товар 6. Товар 2 лишається (пропозиція 20 у ньому ж).
        Map<Long, Long> previous = Map.of(10L, 1L, 11L, 1L, 12L, 1L, 20L, 2L);
        Map<Long, Long> current = Map.of(10L, 5L, 11L, 5L, 12L, 6L, 20L, 2L);

        assertThat(CatalogService.successors(previous, current)).containsExactly(Map.entry(1L, 5L));
    }

    @Test
    void tieGoesToSmallerProductIdAndKeptProductsAreNotReplaced() {
        // товар 3 зберіг id (більшість його пропозицій лишилась), товар 4 розділився порівну між 8 і 7
        Map<Long, Long> previous = Map.of(30L, 3L, 31L, 3L, 32L, 3L, 40L, 4L, 41L, 4L);
        Map<Long, Long> current = Map.of(30L, 3L, 31L, 3L, 32L, 9L, 40L, 8L, 41L, 7L);

        assertThat(CatalogService.successors(previous, current)).containsExactly(Map.entry(4L, 7L));
    }

    @Test
    void newOffersWithoutPreviousProductChangeNothing() {
        assertThat(CatalogService.successors(Map.of(), Map.of(10L, 5L))).isEmpty();
    }

}
