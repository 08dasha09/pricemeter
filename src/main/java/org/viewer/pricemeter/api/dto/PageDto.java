package org.viewer.pricemeter.api.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Сторінка результатів: стабільний формат замість серіалізації {@link Page}.
 *
 * @param page номер сторінки, з нуля
 */
public record PageDto<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> PageDto<T> of(Page<T> page) {
        return new PageDto<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages());
    }

}
