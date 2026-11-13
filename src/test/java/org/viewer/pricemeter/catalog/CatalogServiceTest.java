package org.viewer.pricemeter.catalog;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.viewer.pricemeter.TestcontainersConfiguration;
import org.viewer.pricemeter.catalog.domain.Offer;
import org.viewer.pricemeter.catalog.domain.Product;
import org.viewer.pricemeter.catalog.repository.OfferRepository;
import org.viewer.pricemeter.catalog.repository.ProductRepository;
import org.viewer.pricemeter.catalog.service.CatalogService;
import org.viewer.pricemeter.catalog.service.OfferFilter;
import org.viewer.pricemeter.catalog.service.OfferUpsertCommand;
import org.viewer.pricemeter.catalog.service.OfferUpsertResult;
import org.viewer.pricemeter.catalog.service.ProductFilter;
import org.viewer.pricemeter.common.NotFoundException;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, CatalogService.class})
class CatalogServiceTest {

    private static final Instant T0 = Instant.parse("2026-10-01T10:00:00Z");

    @Autowired
    private CatalogService catalog;

    @Autowired
    private OfferRepository offers;

    @Autowired
    private ProductRepository products;

    @Test
    void createsNewOffer() {
        OfferUpsertResult result = catalog.upsertOffer(command("R-1", "  Samsung WW80T4040CE1UA  ", T0));

        assertThat(result.created()).isTrue();
        Offer offer = result.offer();
        assertThat(offer.getId()).isNotNull();
        assertThat(offer.getRawTitle()).isEqualTo("Samsung WW80T4040CE1UA");
        assertThat(offer.getCategory().getCode()).isEqualTo("WASHING_MACHINES");
        assertThat(offer.getGtin()).isEqualTo("8806092000000");
        assertThat(offer.getFirstSeenAt()).isEqualTo(T0);
        assertThat(offer.getLastSeenAt()).isEqualTo(T0);
    }

    @Test
    void updatesExistingOfferWithoutDuplicating() {
        catalog.upsertOffer(command("R-2", "Samsung WW80T4040CE1UA", T0));
        Instant t1 = T0.plus(1, ChronoUnit.DAYS);

        OfferUpsertResult result = catalog.upsertOffer(new OfferUpsertCommand(
                "ROZETKA", null, "R-2", "https://rozetka.com.ua/new-url/", "Samsung WW80T4040CE1/UA",
                null, null, null, t1));

        assertThat(result.created()).isFalse();
        Offer offer = result.offer();
        assertThat(offer.getUrl()).isEqualTo("https://rozetka.com.ua/new-url/");
        assertThat(offer.getRawTitle()).isEqualTo("Samsung WW80T4040CE1/UA");
        assertThat(offer.getFirstSeenAt()).isEqualTo(T0);
        assertThat(offer.getLastSeenAt()).isEqualTo(t1);
        // необов'язкові поля, що не прийшли, зберігають попередні значення
        assertThat(offer.getCategory().getCode()).isEqualTo("WASHING_MACHINES");
        assertThat(offer.getBrandRaw()).isEqualTo("Samsung");
        assertThat(offer.getGtin()).isEqualTo("8806092000000");
        assertThat(offer.getSpecs()).containsEntry("load_kg", "8");
        assertThat(offers.count()).isEqualTo(1);
    }

    @Test
    void sameExternalIdInDifferentShopsCreatesSeparateOffers() {
        catalog.upsertOffer(command("X-1", "Bosch WAN28263UA", T0));
        OfferUpsertResult comfy = catalog.upsertOffer(new OfferUpsertCommand(
                "COMFY", null, "X-1", "https://comfy.ua/x-1", "Bosch WAN28263UA", null, null, null, T0));

        assertThat(comfy.created()).isTrue();
        assertThat(offers.count()).isEqualTo(2);
    }

    @Test
    void rejectsUnknownShopAndCategory() {
        assertThatThrownBy(() -> catalog.upsertOffer(new OfferUpsertCommand(
                "UNKNOWN", null, "1", "https://x", "t", null, null, null, null)))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> catalog.upsertOffer(new OfferUpsertCommand(
                "ROZETKA", "UNKNOWN", "1", "https://x", "t", null, null, null, null)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void rejectsBlankRequiredFields() {
        assertThatThrownBy(() -> new OfferUpsertCommand("ROZETKA", null, " ", "https://x", "t", null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void dropsInvalidGtin() {
        Offer offer = catalog.upsertOffer(new OfferUpsertCommand(
                "ROZETKA", null, "G-1", "https://x", "t", null, "12-34", null, T0)).offer();

        assertThat(offer.getGtin()).isNull();
    }

    @Test
    void deactivatesStaleOffersAndReactivatesWhenSeenAgain() {
        catalog.upsertOffer(command("OLD", "Old model", T0));
        catalog.upsertOffer(command("FRESH", "Fresh model", T0.plus(Duration.ofDays(2))));

        int deactivated = catalog.deactivateOffersNotSeenSince("ROZETKA", T0.plus(Duration.ofDays(1)));

        assertThat(deactivated).isEqualTo(1);
        Long shopId = catalog.getShop("ROZETKA").getId();
        assertThat(offers.findByShopIdAndExternalId(shopId, "OLD").orElseThrow().isActive()).isFalse();
        assertThat(offers.findByShopIdAndExternalId(shopId, "FRESH").orElseThrow().isActive()).isTrue();

        Offer seenAgain = catalog.upsertOffer(command("OLD", "Old model", T0.plus(Duration.ofDays(3)))).offer();
        assertThat(seenAgain.isActive()).isTrue();
    }

    @Test
    void searchesProductsByTitleCodeAndCategory() {
        Product washer = new Product(catalog.getCategory("WASHING_MACHINES"), "Пральна машина Samsung WW80T4040CE1UA");
        washer.setModelCode("WW80T4040CE1UA");
        products.save(washer);
        Product fridge = new Product(catalog.getCategory("REFRIGERATORS"), "Холодильник LG GA-B509CQSL");
        fridge.setModelCode("GAB509CQSL");
        products.save(fridge);

        assertThat(titles(new ProductFilter(null, "samsung", false))).containsExactly(washer.getTitle());
        // код без пробілів і роздільників
        assertThat(titles(new ProductFilter(null, "ww80t4040 ce1/ua", false))).containsExactly(washer.getTitle());
        assertThat(titles(new ProductFilter(null, "GA-B509", false))).containsExactly(fridge.getTitle());
        assertThat(titles(new ProductFilter("refrigerators", null, false))).containsExactly(fridge.getTitle());
        assertThat(titles(new ProductFilter(null, " ", false))).hasSize(2);
    }

    @Test
    void filtersProductsSoldInSeveralShops() {
        Product shared = products.save(new Product(catalog.getCategory("WASHING_MACHINES"), "Samsung WW80T4040CE1UA"));
        Product single = products.save(new Product(catalog.getCategory("WASHING_MACHINES"), "Bosch WAN28263UA"));
        link(catalog.upsertOffer(command("S-1", "Samsung WW80T4040CE1UA", T0)).offer(), shared);
        link(catalog.upsertOffer(new OfferUpsertCommand("MOYO", "WASHING_MACHINES", "M-1", "https://moyo/1",
                "Samsung WW80T4040CE1UA", "Samsung", null, null, null, T0)).offer(), shared);
        // дві пропозиції одного магазину — це не «кілька магазинів»
        link(catalog.upsertOffer(command("S-2", "Bosch WAN28263UA", T0)).offer(), single);
        link(catalog.upsertOffer(command("S-3", "Бош WAN28263UA", T0)).offer(), single);

        assertThat(titles(new ProductFilter(null, null, true))).containsExactly("Samsung WW80T4040CE1UA");
        assertThat(catalog.getOffersOfProducts(List.of(shared.getId(), single.getId()))).hasSize(4);
        assertThat(catalog.getOffersOfProducts(List.of())).isEmpty();

        // для головної: 2 товари, з кількох магазинів — 1, пропозицій на сайтах — 3 з 4
        catalog.deactivateOffer(offers.findAll().getLast().getId());
        assertThat(catalog.overview()).isEqualTo(new CatalogService.CatalogOverview(2, 1, 3));
        assertThat(catalog.getMultiShopProducts()).extracting(Product::getTitle).containsExactly("Samsung WW80T4040CE1UA");
        assertThat(catalog.countActiveOffers("MOYO")).isEqualTo(1);
    }

    private List<String> titles(ProductFilter filter) {
        return catalog.searchProducts(filter, PageRequest.of(0, 10)).map(Product::getTitle).getContent();
    }

    private void link(Offer offer, Product product) {
        offer.setProduct(product);
        offers.flush();
    }

    @Test
    void searchesOffersByShopCategoryAndText() {
        catalog.upsertOffer(new OfferUpsertCommand("MOYO", "WASHING_MACHINES", "M-1", "https://moyo/1",
                "Пральна машина Samsung WW80T4040CE1UA", "Samsung", null, null, null, T0));
        catalog.upsertOffer(new OfferUpsertCommand("CITRUS", "WASHING_MACHINES", "C-1", "https://citrus/1",
                "Пральна машина Bosch WAN28263UA", "Bosch", null, "WAN28263UA", null, T0));
        catalog.upsertOffer(new OfferUpsertCommand("CITRUS", "REFRIGERATORS", "C-2", "https://citrus/2",
                "Холодильник LG GA-B509CQSL 100%_new", "LG", null, null, null, T0));

        assertThat(search(OfferFilter.all())).hasSize(3);
        assertThat(search(new OfferFilter("citrus", null, null, false)))
                .containsExactlyInAnyOrder("C-1", "C-2");
        assertThat(search(new OfferFilter("CITRUS", "WASHING_MACHINES", null, false)))
                .containsExactly("C-1");
        assertThat(search(new OfferFilter(null, null, "  SAMSUNG ", false))).containsExactly("M-1");
        // збіг за кодом моделі, а не лише за назвою
        assertThat(search(new OfferFilter(null, null, "wan2826", false))).containsExactly("C-1");
        // % і _ шукаються буквально
        assertThat(search(new OfferFilter(null, null, "100%_", false))).containsExactly("C-2");
        assertThat(search(new OfferFilter(null, null, "%", false))).containsExactly("C-2");

        Page<Offer> page = catalog.searchOffers(OfferFilter.all(), PageRequest.of(0, 2));
        assertThat(page.getTotalElements()).isEqualTo(3);
        // магазин і категорія завантажуються разом із пропозицією
        assertThat(page.getContent().getFirst().getShop().getName()).isNotBlank();
        assertThat(page.getContent().getFirst().getCategory().getName()).isNotBlank();
    }

    @Test
    void searchesOnlyActiveOffersWhenRequested() {
        catalog.upsertOffer(command("OLD", "Old model", T0));
        catalog.upsertOffer(command("FRESH", "Fresh model", T0.plus(Duration.ofDays(2))));
        catalog.deactivateOffersNotSeenSince("ROZETKA", T0.plus(Duration.ofDays(1)));

        assertThat(search(new OfferFilter(null, null, null, true))).containsExactly("FRESH");
        assertThat(search(new OfferFilter(null, null, null, false))).containsExactlyInAnyOrder("OLD", "FRESH");
    }

    @Test
    void getsOfferWithShopAndCategory() {
        Long id = catalog.upsertOffer(command("G-2", "Samsung WW80T4040CE1UA", T0)).offer().getId();

        Offer offer = catalog.getOffer(id);

        assertThat(offer.getShop().getCode()).isEqualTo("ROZETKA");
        assertThat(offer.getCategory().getCode()).isEqualTo("WASHING_MACHINES");
        assertThatThrownBy(() -> catalog.getOffer(-1L)).isInstanceOf(NotFoundException.class);
    }

    private List<String> search(OfferFilter filter) {
        return catalog.searchOffers(filter, PageRequest.of(0, 50)).map(Offer::getExternalId).getContent();
    }

    private static OfferUpsertCommand command(String externalId, String title, Instant seenAt) {
        return new OfferUpsertCommand(
                "ROZETKA", "WASHING_MACHINES", externalId, "https://rozetka.com.ua/" + externalId + "/", title,
                "Samsung", "880-6092-000000", Map.of("load_kg", "8"), seenAt);
    }

}
