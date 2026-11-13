package org.viewer.pricemeter.matching;

import org.junit.jupiter.api.Test;
import org.viewer.pricemeter.crawler.CrawlCompletedEvent;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MatchingOnCrawlListenerTest {

    private final MatchingService matching = mock(MatchingService.class);
    private final MatchingOnCrawlListener listener = new MatchingOnCrawlListener(matching);

    @Test
    void matchesCrawledCategories() {
        listener.onCrawlCompleted(new CrawlCompletedEvent(Set.of("WASHING_MACHINES", "REFRIGERATORS")));

        verify(matching).matchCategories(Set.of("WASHING_MACHINES", "REFRIGERATORS"));
    }

    @Test
    void matchingFailureDoesNotPropagateToCrawl() {
        when(matching.matchCategories(Set.of("WASHING_MACHINES"))).thenThrow(new IllegalStateException("boom"));

        assertThatCode(() -> listener.onCrawlCompleted(new CrawlCompletedEvent(Set.of("WASHING_MACHINES"))))
                .doesNotThrowAnyException();
    }

}
