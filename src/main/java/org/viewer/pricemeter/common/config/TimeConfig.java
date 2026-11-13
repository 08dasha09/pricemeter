package org.viewer.pricemeter.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration(proxyBeanMethods = false)
public class TimeConfig {

    /** Часовий пояс, у якому рахуються календарні дні (магазини та користувачі — в Україні). */
    public static final ZoneId ZONE = ZoneId.of("Europe/Kyiv");

    @Bean
    Clock clock() {
        return Clock.system(ZONE);
    }

}
