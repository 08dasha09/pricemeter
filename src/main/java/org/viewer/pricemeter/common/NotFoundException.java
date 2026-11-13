package org.viewer.pricemeter.common;

/**
 * Запитаний об'єкт не знайдено.
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }

    public static NotFoundException of(String entity, Object key) {
        return new NotFoundException(entity + " not found: " + key);
    }

}
