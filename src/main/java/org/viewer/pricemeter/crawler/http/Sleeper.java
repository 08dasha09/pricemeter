package org.viewer.pricemeter.crawler.http;

import java.time.Duration;

/**
 * Очікування; виділено в інтерфейс, щоб у тестах не чекати реально.
 */
@FunctionalInterface
public interface Sleeper {

    Sleeper THREAD = Thread::sleep;

    void sleep(Duration duration) throws InterruptedException;

}
