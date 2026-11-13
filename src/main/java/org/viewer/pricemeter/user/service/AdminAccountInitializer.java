package org.viewer.pricemeter.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Обліковий запис адміністратора під час старту: email — {@code app.security.admin-email}, пароль — змінна
 * середовища {@code ADMIN_PASSWORD}. Без пароля адміністратор створюється з випадковим паролем, який один раз
 * виводиться в лог; наявного адміністратора без пароля не чіпаємо.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminAccountInitializer implements ApplicationRunner {

    private final UserService users;

    @Value("${app.security.admin-email}")
    private String adminEmail;

    @Value("${app.security.admin-password:}")
    private String adminPassword;

    @Override
    public void run(ApplicationArguments args) {
        if (!adminPassword.isBlank()) {
            if (users.ensureAdmin(adminEmail, adminPassword)) {
                log.info("Admin account {} is set up with the password from ADMIN_PASSWORD", adminEmail);
            }
            return;
        }
        if (users.exists(adminEmail)) {
            return;
        }
        String generated = randomPassword();
        users.ensureAdmin(adminEmail, generated);
        log.warn("Created admin account {} with generated password {} (save it or set ADMIN_PASSWORD)",
                adminEmail, generated);
    }

    private static String randomPassword() {
        byte[] bytes = new byte[12];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

}
