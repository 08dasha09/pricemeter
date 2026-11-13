package org.viewer.pricemeter.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.viewer.pricemeter.TestcontainersConfiguration;
import org.viewer.pricemeter.common.config.TimeConfig;
import org.viewer.pricemeter.user.config.UserConfig;
import org.viewer.pricemeter.user.domain.AppUser;
import org.viewer.pricemeter.user.repository.AppUserRepository;
import org.viewer.pricemeter.user.service.AppUserDetailsService;
import org.viewer.pricemeter.user.service.EmailAlreadyUsedException;
import org.viewer.pricemeter.user.service.UserService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, TimeConfig.class, UserConfig.class, UserService.class,
        AppUserDetailsService.class})
class UserServiceTest {

    @Autowired
    private UserService service;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private AppUserDetailsService userDetails;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void registersUserWithHashedPasswordAndLowercaseEmail() {
        AppUser user = service.register("  Buyer@Example.COM ", "secret-123");

        assertThat(user.getEmail()).isEqualTo("buyer@example.com");
        assertThat(user.getRole()).isEqualTo(AppUser.Role.USER);
        assertThat(user.getPasswordHash()).startsWith("{bcrypt}").doesNotContain("secret-123");
        assertThat(passwordEncoder.matches("secret-123", user.getPasswordHash())).isTrue();
    }

    @Test
    void emailIsUniqueRegardlessOfCase() {
        service.register("buyer@example.com", "secret-123");

        assertThatThrownBy(() -> service.register("BUYER@example.com", "other-password"))
                .isInstanceOf(EmailAlreadyUsedException.class);
    }

    @Test
    void loadsUserDetailsByEmailIgnoringCase() {
        service.register("buyer@example.com", "secret-123");

        UserDetails details = userDetails.loadUserByUsername("Buyer@Example.com");

        assertThat(details.getUsername()).isEqualTo("buyer@example.com");
        assertThat(details.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_USER");
        assertThatThrownBy(() -> userDetails.loadUserByUsername("nobody@example.com"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void ensureAdminCreatesThenKeepsThenChangesPassword() {
        assertThat(service.ensureAdmin("admin@pricemeter.local", "first-password")).isTrue();
        assertThat(service.ensureAdmin("admin@pricemeter.local", "first-password")).isFalse();
        assertThat(service.ensureAdmin("admin@pricemeter.local", "second-password")).isTrue();

        AppUser admin = users.findByEmail("admin@pricemeter.local").orElseThrow();
        assertThat(admin.getRole()).isEqualTo(AppUser.Role.ADMIN);
        assertThat(passwordEncoder.matches("second-password", admin.getPasswordHash())).isTrue();
    }

    @Test
    void ensureAdminPromotesExistingUser() {
        service.register("owner@example.com", "secret-123");

        service.ensureAdmin("owner@example.com", "secret-123");

        assertThat(userDetails.loadUserByUsername("owner@example.com").getAuthorities())
                .extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_ADMIN");
    }

}
