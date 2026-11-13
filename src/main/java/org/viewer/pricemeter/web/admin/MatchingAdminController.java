package org.viewer.pricemeter.web.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.viewer.pricemeter.common.NotFoundException;
import org.viewer.pricemeter.matching.MatchingAlreadyRunningException;
import org.viewer.pricemeter.matching.MatchingReport;
import org.viewer.pricemeter.matching.MatchingService;

import java.util.List;
import java.util.Map;

/**
 * Синхронний запуск зіставлення для {@code curl}.
 */
@RestController
@RequestMapping("/admin/matching")
@RequiredArgsConstructor
public class MatchingAdminController {

    private final MatchingService matching;

    /**
     * Приклад: {@code curl -X POST http://localhost:8080/admin/matching}
     */
    @PostMapping
    public List<MatchingReport> matchAll() {
        return matching.matchAll();
    }

    /**
     * Приклад: {@code curl -X POST http://localhost:8080/admin/matching/washing_machines}
     */
    @PostMapping("/{categoryCode}")
    public MatchingReport matchCategory(@PathVariable String categoryCode) {
        return matching.matchCategory(categoryCode);
    }

    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> notFound(NotFoundException e) {
        return Map.of("error", e.getMessage());
    }

    @ExceptionHandler(MatchingAlreadyRunningException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> conflict(MatchingAlreadyRunningException e) {
        return Map.of("error", e.getMessage());
    }

}
