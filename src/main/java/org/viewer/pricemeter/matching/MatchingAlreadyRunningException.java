package org.viewer.pricemeter.matching;

/**
 * Зіставлення вже виконується — другий запуск одночасно призвів би до суперечливих рішень.
 */
public class MatchingAlreadyRunningException extends RuntimeException {

    public MatchingAlreadyRunningException() {
        super("Зіставлення вже виконується");
    }

}
