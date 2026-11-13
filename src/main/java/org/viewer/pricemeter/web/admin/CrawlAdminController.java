package org.viewer.pricemeter.web.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.viewer.pricemeter.common.NotFoundException;
import org.viewer.pricemeter.crawler.CrawlReport;
import org.viewer.pricemeter.crawler.scheduling.CrawlAlreadyRunningException;
import org.viewer.pricemeter.crawler.scheduling.CrawlLauncher;

import java.util.List;
import java.util.Map;

/**
 * Синхронний запуск обходу для {@code curl}; HTML-форма — {@link CrawlPageController}.
 */
@RestController
@RequestMapping("/admin/crawl")
@RequiredArgsConstructor
public class CrawlAdminController {

    static final int MAX_LIMIT = 500;

    private final CrawlLauncher launcher;

    @GetMapping("/categories")
    public Map<String, List<String>> supportedCategories() {
        return launcher.targets();
    }

    /**
     * Приклад: {@code curl -X POST "http://localhost:8080/admin/crawl/moyo/washing_machines?limit=10"}
     */
    @PostMapping("/{shopCode}/{categoryCode}")
    public CrawlReport crawl(@PathVariable String shopCode, @PathVariable String categoryCode,
                             @RequestParam(defaultValue = "20") int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT);
        }
        return launcher.crawlNow(shopCode, categoryCode, limit);
    }

    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> notFound(NotFoundException e) {
        return Map.of("error", e.getMessage());
    }

    @ExceptionHandler(CrawlAlreadyRunningException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> conflict(CrawlAlreadyRunningException e) {
        return Map.of("error", e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> badRequest(IllegalArgumentException e) {
        return Map.of("error", e.getMessage());
    }

}
