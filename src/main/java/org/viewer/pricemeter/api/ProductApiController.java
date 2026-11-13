package org.viewer.pricemeter.api;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.viewer.pricemeter.analytics.PriceAnalyticsService;
import org.viewer.pricemeter.analytics.ProductMarket;
import org.viewer.pricemeter.analytics.ProductMarketService;
import org.viewer.pricemeter.analytics.VerdictService;
import org.viewer.pricemeter.api.dto.DiscountDto;
import org.viewer.pricemeter.api.dto.PageDto;
import org.viewer.pricemeter.api.dto.ProductHistoryDto;
import org.viewer.pricemeter.api.dto.ProductOffersDto;
import org.viewer.pricemeter.api.dto.ProductSummaryDto;
import org.viewer.pricemeter.api.dto.ProductVerdictDto;
import org.viewer.pricemeter.catalog.domain.Offer;
import org.viewer.pricemeter.catalog.domain.Product;
import org.viewer.pricemeter.catalog.service.CatalogService;
import org.viewer.pricemeter.catalog.service.ProductFilter;
import org.viewer.pricemeter.common.Period;
import org.viewer.pricemeter.fakediscount.FakeDiscountDetector;
import org.viewer.pricemeter.pricehistory.service.DailyPrice;
import org.viewer.pricemeter.pricehistory.service.PriceHistoryService;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductApiController {

    static final int MAX_PAGE_SIZE = 100;

    private final CatalogService catalog;
    private final PriceHistoryService priceHistory;
    private final ProductMarketService markets;
    private final VerdictService verdicts;
    private final FakeDiscountDetector discounts;

    /**
     * Приклад: {@code GET /api/products?query=ww80t4040&category=WASHING_MACHINES&multiShop=true&page=0&size=20}
     */
    @GetMapping
    public PageDto<ProductSummaryDto> search(@RequestParam(required = false) String query,
                                             @RequestParam(required = false) String category,
                                             @RequestParam(defaultValue = "false") boolean multiShop,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("page must be >= 0, size between 1 and " + MAX_PAGE_SIZE);
        }
        Page<Product> products = catalog.searchProducts(new ProductFilter(category, query, multiShop),
                PageRequest.of(page, size, Sort.by("title", "id")));
        Map<Long, ProductMarket> byProduct = markets.markets(products.map(Product::getId).getContent());
        return PageDto.of(products.map(product -> {
            ProductMarket market = byProduct.getOrDefault(product.getId(), ProductMarket.EMPTY);
            return new ProductSummaryDto(product.getId(), product.getTitle(), product.getCategory().getCode(),
                    product.getBrand(), product.getModelCode(),
                    market.shops().stream().map(ProductMarket.Shop::code).toList(),
                    market.minPrice(), market.purchasableShops());
        }));
    }

    /**
     * Приклад: {@code GET /api/products/5/offers}
     */
    @GetMapping("/{id}/offers")
    public ProductOffersDto offers(@PathVariable Long id) {
        catalog.getProduct(id);
        return ProductOffersDto.of(id, markets.market(id));
    }

    /**
     * Вердикти всіх пропозицій товару й підсумок — вердикт найнижчої ціни.
     * Приклад: {@code GET /api/products/5/verdict?period=QUARTER}
     */
    @GetMapping("/{id}/verdict")
    public ProductVerdictDto verdict(@PathVariable Long id, @RequestParam(required = false) Period period) {
        catalog.getProduct(id);
        Period effective = period != null ? period : PriceAnalyticsService.DEFAULT_PERIOD;
        return ProductVerdictDto.of(id, effective, verdicts.forProduct(id, effective));
    }

    /**
     * Оцінки заявлених знижок усіх пропозицій товару (без знижки — {@code NO_DISCOUNT}).
     * Приклад: {@code GET /api/products/5/discounts}
     */
    @GetMapping("/{id}/discounts")
    public List<DiscountDto> discounts(@PathVariable Long id) {
        catalog.getProduct(id);
        return discounts.assessProduct(id).entrySet().stream()
                .map(e -> DiscountDto.of(e.getKey(), e.getValue()))
                .toList();
    }

    /**
     * Приклад: {@code GET /api/products/5/history?period=QUARTER}. Пропозиції без історії за період пропускаються.
     */
    @GetMapping("/{id}/history")
    public ProductHistoryDto history(@PathVariable Long id, @RequestParam(defaultValue = "MONTH") Period period) {
        catalog.getProduct(id);
        List<Offer> offers = catalog.getOffersOfProduct(id).stream()
                .sorted(Comparator.comparing((Offer o) -> o.getShop().getName()).thenComparing(Offer::getId))
                .toList();

        Map<Offer, Map<LocalDate, DailyPrice>> daysByOffer = new LinkedHashMap<>();
        TreeSet<LocalDate> dates = new TreeSet<>();
        for (Offer offer : offers) {
            List<DailyPrice> days = priceHistory.getDailySeries(offer.getId(), period);
            if (days.isEmpty()) {
                continue;
            }
            Map<LocalDate, DailyPrice> byDate = new HashMap<>();
            for (DailyPrice day : days) {
                dates.add(day.date());
                byDate.put(day.date(), day);
            }
            daysByOffer.put(offer, byDate);
        }

        List<ProductHistoryDto.Series> series = new ArrayList<>();
        daysByOffer.forEach((offer, byDate) -> series.add(new ProductHistoryDto.Series(
                offer.getId(),
                offer.getShop().getCode(),
                offer.getShop().getName(),
                dates.stream().map(byDate::get).map(day -> day != null && day.available() ? day.price() : null)
                        .toList(),
                dates.stream().map(byDate::get).map(day -> day != null ? day.confirmed() : null).toList())));
        return new ProductHistoryDto(id, period, List.copyOf(dates), series);
    }

}
