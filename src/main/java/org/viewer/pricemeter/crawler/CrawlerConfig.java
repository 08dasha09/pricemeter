package org.viewer.pricemeter.crawler;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.viewer.pricemeter.crawler.adapter.CrawlerShopsProperties;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CrawlerShopsProperties.class)
public class CrawlerConfig {
}
