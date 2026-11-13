package org.viewer.pricemeter;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** Розклад вимкнено: кешований контекст інакше міг би сам почати обходити справжні магазини (надолужування). */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "crawler.schedule.enabled=false")
class PricemeterApplicationTests {

    @Test
    void contextLoads() {
    }

}
