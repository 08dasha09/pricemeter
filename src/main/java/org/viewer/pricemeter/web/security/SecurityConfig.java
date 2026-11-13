package org.viewer.pricemeter.web.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;

import java.time.Duration;

/**
 * Доступ: {@code /admin/**} — лише адміністратор, {@code /account/**} — будь-який користувач, решта (товари,
 * знижки, REST) — усім. Вхід — формою {@code /login} (з «запам'ятати мене») або HTTP Basic для {@code curl}.
 *
 * <p>CSRF-токен не потрібен лише запитам з HTTP Basic: браузер такий заголовок сам не додає (під час переходу
 * сторінкою неавторизованого відправляємо на форму входу, а не у вікно Basic — запит Basic отримують лише клієнти без
 * {@code Accept: text/html}), тож підробити такий запит з чужої сторінки не можна.
 */
@Configuration
public class SecurityConfig {

    static final Duration REMEMBER_ME_VALIDITY = Duration.ofDays(30);

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectProvider<PersistentTokenRepository> tokens,
                                            ObjectProvider<UserDetailsService> users) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/account/**").authenticated()
                        .anyRequest().permitAll())
                .formLogin(form -> form.loginPage("/login").permitAll())
                .logout(logout -> logout.logoutSuccessUrl("/"))
                .httpBasic(Customizer.withDefaults())
                .csrf(csrf -> csrf.ignoringRequestMatchers(SecurityConfig::hasBasicCredentials));
        PersistentTokenRepository tokenRepository = tokens.getIfAvailable();
        UserDetailsService userDetails = users.getIfAvailable();
        if (tokenRepository != null && userDetails != null) {
            http.rememberMe(remember -> remember
                    .tokenRepository(tokenRepository)
                    .userDetailsService(userDetails)
                    .tokenValiditySeconds((int) REMEMBER_ME_VALIDITY.toSeconds()));
        }
        return http.build();
    }

    static boolean hasBasicCredentials(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        return header != null && header.regionMatches(true, 0, "Basic ", 0, 6);
    }

}
