package org.viewer.pricemeter.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.ModelAndView;
import org.viewer.pricemeter.common.NotFoundException;

import java.util.Map;

/**
 * Сторінки помилок для Thymeleaf-контролерів.
 */
@ControllerAdvice(basePackageClasses = WebExceptionHandler.class)
public class WebExceptionHandler {

    @ExceptionHandler({NotFoundException.class, MethodArgumentTypeMismatchException.class})
    public ModelAndView notFound() {
        return new ModelAndView("error/404", Map.of(), HttpStatus.NOT_FOUND);
    }

}
