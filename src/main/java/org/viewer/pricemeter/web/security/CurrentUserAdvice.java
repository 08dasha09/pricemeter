package org.viewer.pricemeter.web.security;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Поточний користувач у моделі кожної сторінки — для шапки (вхід / вихід, адмінські пункти меню).
 */
@ControllerAdvice(basePackages = "org.viewer.pricemeter.web")
public class CurrentUserAdvice {

    /** {@code null} — гість. */
    @ModelAttribute("currentUser")
    public CurrentUser currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return null;
        }
        boolean admin = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
        return new CurrentUser(auth.getName(), admin);
    }

    public record CurrentUser(String email, boolean admin) {
    }

}
