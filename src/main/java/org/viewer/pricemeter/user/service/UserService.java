package org.viewer.pricemeter.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.viewer.pricemeter.common.NotFoundException;
import org.viewer.pricemeter.user.domain.AppUser;
import org.viewer.pricemeter.user.repository.AppUserRepository;

import java.time.Clock;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Реєстрація користувачів і обліковий запис адміністратора. Email зберігається в нижньому регістрі.
 */
@Service
@RequiredArgsConstructor
public class UserService {

    public static final int MIN_PASSWORD_LENGTH = 8;

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    /**
     * @throws EmailAlreadyUsedException користувач з таким email уже є
     */
    @Transactional
    public AppUser register(String email, String rawPassword) {
        String normalized = normalizeEmail(email);
        if (users.existsByEmail(normalized)) {
            throw new EmailAlreadyUsedException(normalized);
        }
        return users.save(new AppUser(normalized, passwordEncoder.encode(rawPassword), AppUser.Role.USER,
                clock.instant()));
    }

    /**
     * Обліковий запис адміністратора: створює, якщо його немає, підвищує роль і змінює пароль, якщо він інший.
     *
     * @return {@code true}, якщо обліковий запис створено або пароль змінено
     */
    @Transactional
    public boolean ensureAdmin(String email, String rawPassword) {
        String normalized = normalizeEmail(email);
        AppUser admin = users.findByEmail(normalized).orElse(null);
        if (admin == null) {
            users.save(new AppUser(normalized, passwordEncoder.encode(rawPassword), AppUser.Role.ADMIN,
                    clock.instant()));
            return true;
        }
        admin.setRole(AppUser.Role.ADMIN);
        if (passwordEncoder.matches(rawPassword, admin.getPasswordHash())) {
            return false;
        }
        admin.setPasswordHash(passwordEncoder.encode(rawPassword));
        return true;
    }

    /**
     * @throws NotFoundException користувача немає
     */
    @Transactional(readOnly = true)
    public Long getUserId(String email) {
        return users.findByEmail(normalizeEmail(email))
                .map(AppUser::getId)
                .orElseThrow(() -> NotFoundException.of("User", email));
    }

    /** Email користувачів за id; відсутні id пропускаються. */
    @Transactional(readOnly = true)
    public Map<Long, String> getEmails(Collection<Long> userIds) {
        return users.findAllById(userIds).stream().collect(Collectors.toMap(AppUser::getId, AppUser::getEmail));
    }

    @Transactional(readOnly = true)
    public boolean exists(String email) {
        return users.existsByEmail(normalizeEmail(email));
    }

    static String normalizeEmail(String email) {
        return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
    }

}
