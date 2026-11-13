package org.viewer.pricemeter.catalog;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.viewer.pricemeter.TestcontainersConfiguration;
import org.viewer.pricemeter.catalog.domain.Category;
import org.viewer.pricemeter.catalog.domain.MatchStatus;
import org.viewer.pricemeter.catalog.domain.Offer;
import org.viewer.pricemeter.catalog.domain.Product;
import org.viewer.pricemeter.catalog.domain.Shop;
import org.viewer.pricemeter.catalog.repository.CategoryRepository;
import org.viewer.pricemeter.catalog.repository.OfferRepository;
import org.viewer.pricemeter.catalog.repository.ProductRepository;
import org.viewer.pricemeter.catalog.repository.ShopRepository;

import jakarta.persistence.EntityManager;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class CatalogRepositoryTest {

    @Autowired
    private ShopRepository shops;

    @Autowired
    private CategoryRepository categories;

    @Autowired
    private ProductRepository products;

    @Autowired
    private OfferRepository offers;

    @Autowired
    private EntityManager em;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void seedDataIsLoaded() {
        assertThat(shops.findAllByEnabledTrue())
                .extracting(Shop::getCode)
                .containsExactlyInAnyOrder("MOYO", "CITRUS", "EPICENTR", "ELDORADO");
        assertThat(shops.findByCode("ROZETKA").orElseThrow().isEnabled()).isFalse();
        assertThat(categories.findByCode("WASHING_MACHINES")).isPresent();
    }

    @Test
    void savesOfferWithSpecsAndProduct() {
        Shop rozetka = shops.findByCode("ROZETKA").orElseThrow();
        Category washers = categories.findByCode("WASHING_MACHINES").orElseThrow();
        Product product = products.save(new Product(washers, "Samsung WW80T4040CE1UA"));

        Offer offer = new Offer(rozetka, "123456", "https://rozetka.com.ua/123456/", "Samsung WW80T4040CE1UA");
        offer.setCategory(washers);
        offer.setSpecs(Map.of("load_kg", "8", "spin_rpm", "1400"));
        offer.setProduct(product);
        offer.setMatchStatus(MatchStatus.AUTO);
        offers.saveAndFlush(offer);
        em.clear();

        Offer loaded = offers.findByShopIdAndExternalId(rozetka.getId(), "123456").orElseThrow();
        assertThat(loaded.getSpecs()).containsEntry("load_kg", "8");
        assertThat(loaded.getMatchStatus()).isEqualTo(MatchStatus.AUTO);
        assertThat(loaded.getFirstSeenAt()).isNotNull();
        assertThat(offers.findAllByProductId(product.getId())).hasSize(1);
    }

    @Test
    void rejectsDuplicateExternalIdWithinShop() {
        Shop comfy = shops.findByCode("COMFY").orElseThrow();
        offers.saveAndFlush(new Offer(comfy, "A-1", "https://comfy.ua/a-1", "Bosch WAN28263UA"));

        assertThatThrownBy(() -> offers.saveAndFlush(new Offer(comfy, "A-1", "https://comfy.ua/a-1-dup", "Bosch WAN28263UA")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsUnknownMatchStatusAtDatabaseLevel() {
        Long shopId = shops.findByCode("ALLO").orElseThrow().getId();

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO offer (shop_id, external_id, url, raw_title, match_status) VALUES (?, 'X', 'u', 't', 'BROKEN')",
                shopId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

}
