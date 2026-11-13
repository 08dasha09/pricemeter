package org.viewer.pricemeter.catalog.service;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.viewer.pricemeter.catalog.domain.Category;
import org.viewer.pricemeter.catalog.domain.MatchStatus;
import org.viewer.pricemeter.catalog.domain.Offer;
import org.viewer.pricemeter.catalog.domain.Product;
import org.viewer.pricemeter.catalog.domain.Shop;
import org.viewer.pricemeter.catalog.repository.CategoryRepository;
import org.viewer.pricemeter.catalog.repository.OfferRepository;
import org.viewer.pricemeter.catalog.repository.ProductRepository;
import org.viewer.pricemeter.catalog.repository.ShopRepository;
import org.viewer.pricemeter.common.NotFoundException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CatalogService {

    private static final char LIKE_ESCAPE = '!';
    /** Коротший код у запиті (напр. «LG») збігався б із надто багатьма кодами моделей. */
    private static final int MIN_CODE_QUERY = 3;

    private final ShopRepository shops;
    private final CategoryRepository categories;
    private final ProductRepository products;
    private final OfferRepository offers;
    private final ApplicationEventPublisher events;

    public Shop getShop(String code) {
        return shops.findByCode(code).orElseThrow(() -> NotFoundException.of("Shop", code));
    }

    public List<Shop> getEnabledShops() {
        return shops.findAllByEnabledTrue();
    }

    public Category getCategory(String code) {
        return categories.findByCode(code).orElseThrow(() -> NotFoundException.of("Category", code));
    }

    public List<Category> getCategories() {
        return categories.findAll();
    }

    /**
     * Створює пропозицію або оновлює наявну за парою (магазин, артикул).
     * Порожні необов'язкові поля не затирають уже відомі значення.
     */
    @Transactional
    public OfferUpsertResult upsertOffer(OfferUpsertCommand cmd) {
        Shop shop = getShop(cmd.shopCode());
        Category category = cmd.categoryCode() != null ? getCategory(cmd.categoryCode()) : null;
        Instant seenAt = cmd.seenAt() != null ? cmd.seenAt() : Instant.now();
        String externalId = cmd.externalId().strip();

        Offer offer = offers.findByShopIdAndExternalId(shop.getId(), externalId).orElse(null);
        boolean created = offer == null;
        if (created) {
            offer = new Offer(shop, externalId, cmd.url().strip(), cmd.title().strip());
            offer.setFirstSeenAt(seenAt);
        } else {
            offer.setUrl(cmd.url().strip());
            offer.setRawTitle(cmd.title().strip());
        }

        if (category != null) {
            offer.setCategory(category);
        }
        if (cmd.brand() != null && !cmd.brand().isBlank()) {
            offer.setBrandRaw(cmd.brand().strip());
        }
        String gtin = normalizeGtin(cmd.gtin());
        if (gtin != null) {
            offer.setGtin(gtin);
        }
        if (cmd.mpn() != null && !cmd.mpn().isBlank()) {
            offer.setMpn(cmd.mpn().strip());
        }
        if (cmd.specs() != null && !cmd.specs().isEmpty()) {
            offer.setSpecs(cmd.specs());
        }
        offer.setActive(true);
        offer.setLastSeenAt(seenAt);

        return new OfferUpsertResult(offers.save(offer), created);
    }

    /** Чи знає каталог пропозицію магазину з таким артикулом. */
    public boolean offerExists(String shopCode, String externalId) {
        return offers.findByShopIdAndExternalId(getShop(shopCode).getId(), externalId.strip()).isPresent();
    }

    /**
     * Позначає неактивними пропозиції магазину, які не траплялися під час обходу з моменту {@code threshold}.
     *
     * @return кількість деактивованих пропозицій
     */
    @Transactional
    public int deactivateOffersNotSeenSince(String shopCode, Instant threshold) {
        return offers.deactivateNotSeenSince(getShop(shopCode).getId(), threshold);
    }

    /**
     * Активні пропозиції категорії магазину, яких немає серед {@code excludedIds} (напр. не знайдених
     * під час поточного обходу); спершу ті, що бачили найдавніше.
     */
    public List<Offer> getActiveOffersExcept(String shopCode, String categoryCode, Collection<Long> excludedIds,
                                             int limit) {
        // порожній список у "not in" — не скрізь коректний SQL, тож підставляємо неіснуючий id
        Collection<Long> excluded = excludedIds.isEmpty() ? List.of(-1L) : excludedIds;
        return offers.findActiveExcept(shopCode, categoryCode, excluded, PageRequest.of(0, limit));
    }

    /**
     * Позначає пропозицію неактивною (напр. сторінка товару повертає 404). Якщо товар знову з'явиться
     * під час обходу, {@link #upsertOffer} поверне його в активні.
     */
    @Transactional
    public void deactivateOffer(Long offerId) {
        offers.findById(offerId).ifPresent(offer -> offer.setActive(false));
    }

    /**
     * Товар разом із категорією.
     */
    public Product getProduct(Long id) {
        return products.findWithCategoryById(id).orElseThrow(() -> NotFoundException.of("Product", id));
    }

    /** Товари разом із категорією; відсутні id пропускаються. */
    public List<Product> getProducts(Collection<Long> ids) {
        return ids.isEmpty() ? List.of() : products.findWithCategoryByIdIn(ids);
    }

    /**
     * Сторінка товарів за фільтром; категорія завантажується разом із товаром. Запит шукається в назві
     * (без урахування регістру) і в коді моделі — без пробілів і роздільників, тож {@code "WW80T4040 CE1/UA"}
     * знайде {@code WW80T4040CE1UA}.
     */
    public Page<Product> searchProducts(ProductFilter filter, Pageable pageable) {
        List<Specification<Product>> specs = new ArrayList<>();
        if (filter.categoryCode() != null && !filter.categoryCode().isBlank()) {
            String categoryCode = filter.categoryCode().strip().toUpperCase(Locale.ROOT);
            specs.add((root, query, cb) -> cb.equal(root.get("category").get("code"), categoryCode));
        }
        if (filter.query() != null && !filter.query().isBlank()) {
            String pattern = "%" + escapeLike(filter.query().strip().toLowerCase(Locale.ROOT)) + "%";
            String code = filter.query().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
            specs.add((root, query, cb) -> code.length() >= MIN_CODE_QUERY
                    ? cb.or(cb.like(cb.lower(root.get("title")), pattern, LIKE_ESCAPE),
                            cb.like(root.get("modelCode"), "%" + code + "%"))
                    : cb.like(cb.lower(root.get("title")), pattern, LIKE_ESCAPE));
        }
        if (filter.multiShopOnly()) {
            specs.add((root, query, cb) -> {
                Subquery<Long> shopsOfProduct = query.subquery(Long.class);
                Root<Offer> offer = shopsOfProduct.from(Offer.class);
                shopsOfProduct.select(cb.countDistinct(offer.get("shop")))
                        .where(cb.equal(offer.get("product"), root));
                return cb.ge(shopsOfProduct, 2L);
            });
        }
        return products.findAll(Specification.allOf(specs), pageable);
    }

    /** Пропозиції за id (з магазином і категорією); відсутні id пропускаються. */
    public List<Offer> getOffers(Collection<Long> ids) {
        return ids.isEmpty() ? List.of() : offers.findAllByIdIn(ids);
    }

    /**
     * Пропозиції кількох товарів (з магазином і категорією) — для списку товарів.
     */
    public List<Offer> getOffersOfProducts(Collection<Long> productIds) {
        return productIds.isEmpty() ? List.of() : offers.findAllByProductIdIn(productIds);
    }

    /**
     * Пропозиція разом із магазином і категорією.
     */
    public Offer getOffer(Long id) {
        return offers.findWithShopAndCategoryById(id).orElseThrow(() -> NotFoundException.of("Offer", id));
    }

    /**
     * Сторінка пропозицій за фільтром; магазин і категорія завантажуються разом із пропозицією.
     */
    public Page<Offer> searchOffers(OfferFilter filter, Pageable pageable) {
        List<Specification<Offer>> specs = new ArrayList<>();
        if (filter.shopCode() != null && !filter.shopCode().isBlank()) {
            String shopCode = filter.shopCode().strip().toUpperCase(Locale.ROOT);
            specs.add((root, query, cb) -> cb.equal(root.get("shop").get("code"), shopCode));
        }
        if (filter.categoryCode() != null && !filter.categoryCode().isBlank()) {
            String categoryCode = filter.categoryCode().strip().toUpperCase(Locale.ROOT);
            specs.add((root, query, cb) -> cb.equal(root.get("category").get("code"), categoryCode));
        }
        if (filter.query() != null && !filter.query().isBlank()) {
            String pattern = "%" + escapeLike(filter.query().strip().toLowerCase(Locale.ROOT)) + "%";
            specs.add((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("rawTitle")), pattern, LIKE_ESCAPE),
                    cb.like(cb.lower(root.get("mpn")), pattern, LIKE_ESCAPE)));
        }
        if (filter.activeOnly()) {
            specs.add((root, query, cb) -> cb.isTrue(root.get("active")));
        }
        return offers.findAll(Specification.allOf(specs), pageable);
    }

    /**
     * Усі пропозиції категорії з поточним рішенням зіставлення.
     */
    public List<MatchableOffer> getMatchableOffers(String categoryCode) {
        return offers.findMatchable(categoryCode);
    }

    /**
     * Записує результат зіставлення категорії: створює або оновлює товари, прив'язує до них пропозиції,
     * видаляє товари категорії, що лишилися без пропозицій. Пропозиції, яких уже немає, пропускаються. Перед
     * видаленням публікує {@link ProductsReplacedEvent}: наступник товару — той, до якого перейшла більшість його
     * пропозицій.
     */
    @Transactional
    public void applyMatching(String categoryCode, List<ProductAssignment> assignments) {
        Category category = getCategory(categoryCode);
        List<Long> offerIds = assignments.stream()
                .flatMap(a -> a.offers().stream())
                .map(ProductAssignment.OfferMatch::offerId)
                .toList();
        Map<Long, Offer> offersById = offers.findAllById(offerIds).stream()
                .collect(Collectors.toMap(Offer::getId, Function.identity()));
        Map<Long, Product> productsById = products.findAllById(assignments.stream()
                        .map(ProductAssignment::productId)
                        .filter(Objects::nonNull)
                        .toList()).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        Map<Long, Long> previousProducts = new HashMap<>();
        offersById.values().stream()
                .filter(offer -> offer.getProduct() != null)
                .forEach(offer -> previousProducts.put(offer.getId(), offer.getProduct().getId()));

        Map<Long, Long> newProducts = new HashMap<>();
        for (ProductAssignment assignment : assignments) {
            Product product = assignment.productId() != null ? productsById.get(assignment.productId()) : null;
            if (product == null) {
                product = new Product(category, assignment.title());
            }
            product.setTitle(assignment.title());
            product.setBrand(assignment.brand());
            product.setModelCode(assignment.modelCode());
            product = products.save(product);

            for (ProductAssignment.OfferMatch match : assignment.offers()) {
                Offer offer = offersById.get(match.offerId());
                if (offer == null) {
                    continue;
                }
                offer.setProduct(product);
                newProducts.put(offer.getId(), product.getId());
                offer.setMatchStatus(match.status());
                if (match.status() != MatchStatus.MANUAL) {
                    offer.setMatchConfidence(match.confidence());
                }
            }
        }
        Map<Long, Long> replacements = successors(previousProducts, newProducts);
        if (!replacements.isEmpty()) {
            events.publishEvent(new ProductsReplacedEvent(replacements));
        }
        products.deleteWithoutOffers(category.getId());
    }

    /**
     * Товари, яких після перерозподілу пропозицій не лишилося, → товар, куди перейшла більшість їхніх пропозицій
     * (за рівності — менший id, щоб результат був детермінованим).
     */
    static Map<Long, Long> successors(Map<Long, Long> previousProducts, Map<Long, Long> newProducts) {
        Set<Long> remaining = new HashSet<>(newProducts.values());
        Map<Long, Map<Long, Long>> moves = new HashMap<>();
        previousProducts.forEach((offerId, previous) -> {
            Long current = newProducts.get(offerId);
            if (current != null && !remaining.contains(previous)) {
                moves.computeIfAbsent(previous, k -> new HashMap<>()).merge(current, 1L, Long::sum);
            }
        });
        Map<Long, Long> replacements = new HashMap<>();
        moves.forEach((previous, targets) -> replacements.put(previous, targets.entrySet().stream()
                .max(Map.Entry.<Long, Long>comparingByValue()
                        .thenComparing(Map.Entry.<Long, Long>comparingByKey().reversed()))
                .orElseThrow()
                .getKey()));
        return replacements;
    }

    /**
     * Ручне рішення: пропозиція належить товару; разом із нею вручну закріплюються й наявні пропозиції товару.
     *
     * @return код категорії пропозиції
     * @throws IllegalArgumentException товар з іншої категорії
     */
    @Transactional
    public String assignOfferManually(long offerId, long productId) {
        Offer offer = getOffer(offerId);
        Product product = getProduct(productId);
        if (offer.getCategory() == null || !offer.getCategory().getId().equals(product.getCategory().getId())) {
            throw new IllegalArgumentException("Товар #" + productId + " з іншої категорії");
        }
        offers.findAllByProductId(productId).forEach(member -> member.setMatchStatus(MatchStatus.MANUAL));
        Product previous = offer.getProduct();
        offer.setProduct(product);
        offer.setMatchStatus(MatchStatus.MANUAL);
        if (previous != null && !previous.getId().equals(productId)
                && offers.findAllByProductId(previous.getId()).stream().allMatch(o -> o.getId().equals(offerId))) {
            // єдина пропозиція товару перейшла до іншого — порожній товар видалить наступне зіставлення
            events.publishEvent(new ProductsReplacedEvent(Map.of(previous.getId(), productId)));
        }
        return offer.getCategory().getCode();
    }

    /**
     * Ручне рішення: пропозиція — окремий товар, автоматично її ні з чим не об'єднувати.
     *
     * @return код категорії пропозиції
     * @throws IllegalArgumentException пропозиція без категорії
     */
    @Transactional
    public String rejectOfferMatch(long offerId) {
        Offer offer = getOffer(offerId);
        if (offer.getCategory() == null) {
            throw new IllegalArgumentException("Пропозиція #" + offerId + " без категорії");
        }
        offer.setMatchStatus(MatchStatus.REJECTED);
        return offer.getCategory().getCode();
    }

    /**
     * Скасувати ручне рішення ({@code MANUAL} або {@code REJECTED}): пропозиція знову зіставляється автоматично.
     *
     * @return код категорії пропозиції
     * @throws IllegalArgumentException рішення не ручне або пропозиція без категорії
     */
    @Transactional
    public String resetMatchDecision(long offerId) {
        Offer offer = getOffer(offerId);
        if (offer.getMatchStatus() != MatchStatus.MANUAL && offer.getMatchStatus() != MatchStatus.REJECTED) {
            throw new IllegalArgumentException("Пропозиція #" + offerId + " зіставлена автоматично — скасовувати нічого");
        }
        if (offer.getCategory() == null) {
            throw new IllegalArgumentException("Пропозиція #" + offerId + " без категорії");
        }
        offer.setMatchStatus(MatchStatus.AUTO);
        offer.setMatchConfidence(null);
        return offer.getCategory().getCode();
    }

    /** Розмір каталогу для головної сторінки. */
    public CatalogOverview overview() {
        long multiShop = searchProducts(new ProductFilter(null, null, true), PageRequest.of(0, 1)).getTotalElements();
        return new CatalogOverview(products.count(), multiShop, offers.countByActiveTrue());
    }

    /**
     * @param products          товарів
     * @param multiShopProducts товарів, що є в кількох магазинах
     * @param activeOffers      пропозицій, які ще є на сайтах магазинів
     */
    public record CatalogOverview(long products, long multiShopProducts, long activeOffers) {
    }

    /** Усі товари, що є в кількох магазинах (разом із категорією). */
    public List<Product> getMultiShopProducts() {
        return searchProducts(new ProductFilter(null, null, true), Pageable.unpaged()).getContent();
    }

    /** Скільки пропозицій магазину ще є на його сайті. */
    public long countActiveOffers(String shopCode) {
        return offers.countByShop_CodeAndActiveTrue(shopCode);
    }

    public boolean productExists(long productId) {
        return products.existsById(productId);
    }

    /**
     * Пропозиції товару разом із магазином і категорією, включно з неактивними.
     */
    public List<Offer> getOffersOfProduct(Long productId) {
        return offers.findAllByProductId(productId);
    }

    private static String escapeLike(String value) {
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    /**
     * Залишає лише цифри; GTIN вважається валідним, якщо має 8, 12, 13 або 14 цифр.
     */
    static String normalizeGtin(String raw) {
        if (raw == null) {
            return null;
        }
        String digits = raw.replaceAll("\\D", "");
        return switch (digits.length()) {
            case 8, 12, 13, 14 -> digits;
            default -> null;
        };
    }

}
