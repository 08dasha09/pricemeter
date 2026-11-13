package org.viewer.pricemeter.crawler.http;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CrawlerHttpProperties.class)
public class CrawlerHttpConfig {

    @Bean
    HttpClient crawlerHttpClient(CrawlerHttpProperties props) {
        return HttpClient.newBuilder()
                .connectTimeout(props.connectTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Bean
    Sleeper sleeper() {
        return Sleeper.THREAD;
    }

    @Bean
    HostRateLimiter hostRateLimiter(Clock clock, Sleeper sleeper) {
        return new HostRateLimiter(clock, sleeper);
    }

    @Bean
    HttpFetcher httpFetcher(HttpClient crawlerHttpClient, CrawlerHttpProperties props,
                            HostRateLimiter hostRateLimiter, Sleeper sleeper, Clock clock) {
        return new HttpFetcher(crawlerHttpClient, props, hostRateLimiter, sleeper, clock);
    }

}
