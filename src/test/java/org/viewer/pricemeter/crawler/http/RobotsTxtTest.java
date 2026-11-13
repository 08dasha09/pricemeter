package org.viewer.pricemeter.crawler.http;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RobotsTxtTest {

    @Test
    void appliesWildcardGroupAndLongestMatch() {
        RobotsTxt robots = RobotsTxt.parse("""
                User-agent: *
                Disallow: /checkout/
                Disallow: /search
                Allow: /search/brand
                """, "PricemeterBot");

        assertThat(robots.isAllowed("/")).isTrue();
        assertThat(robots.isAllowed("/product/123/")).isTrue();
        assertThat(robots.isAllowed("/checkout/step1")).isFalse();
        assertThat(robots.isAllowed("/search?q=lg")).isFalse();
        assertThat(robots.isAllowed("/search/brand/lg")).isTrue();
    }

    @Test
    void allowWinsOnEqualLength() {
        RobotsTxt robots = RobotsTxt.parse("""
                User-agent: *
                Disallow: /page
                Allow: /page
                """, "PricemeterBot");

        assertThat(robots.isAllowed("/page")).isTrue();
    }

    @Test
    void supportsWildcardsAndEndAnchor() {
        RobotsTxt robots = RobotsTxt.parse("""
                User-agent: *
                Disallow: /*?sort=
                Disallow: /*.pdf$
                """, "PricemeterBot");

        assertThat(robots.isAllowed("/washers/?sort=price")).isFalse();
        assertThat(robots.isAllowed("/washers/?page=2")).isTrue();
        assertThat(robots.isAllowed("/docs/manual.pdf")).isFalse();
        assertThat(robots.isAllowed("/docs/manual.pdf?download=1")).isTrue();
    }

    @Test
    void specificAgentGroupOverridesWildcard() {
        RobotsTxt robots = RobotsTxt.parse("""
                User-agent: *
                Disallow: /

                User-agent: Googlebot
                User-agent: PricemeterBot
                Disallow: /private
                Crawl-delay: 5
                """, "PricemeterBot");

        assertThat(robots.isAllowed("/product/1")).isTrue();
        assertThat(robots.isAllowed("/private/x")).isFalse();
        assertThat(robots.crawlDelay()).contains(Duration.ofSeconds(5));
    }

    @Test
    void ignoresCommentsEmptyDisallowAndUnknownDirectives() {
        RobotsTxt robots = RobotsTxt.parse("""
                # comment
                Sitemap: https://example.com/sitemap.xml
                User-agent: * # all bots
                Disallow:
                Host: example.com
                """, "PricemeterBot");

        assertThat(robots.isAllowed("/anything")).isTrue();
        assertThat(robots.crawlDelay()).isEmpty();
    }

    @Test
    void disallowAllBlocksEverything() {
        RobotsTxt robots = RobotsTxt.parse("User-agent: *\nDisallow: /\n", "PricemeterBot");

        assertThat(robots.isAllowed("/")).isFalse();
        assertThat(robots.isAllowed("/product/1")).isFalse();
    }

}
