package org.viewer.pricemeter.api.dto;

/**
 * Тіло відповіді з помилкою.
 */
public record ApiError(int status, String error) {
}
