package org.viewer.pricemeter.user.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.rememberme.JdbcTokenRepositoryImpl;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;

import javax.sql.DataSource;

@Configuration
public class UserConfig {

    /** BCrypt з префіксом алгоритму ({@code {bcrypt}…}) — алгоритм можна буде змінити без міграції паролів. */
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /** Токени «запам'ятати мене» в таблиці {@code persistent_logins}: вхід переживає перезапуск застосунку. */
    @Bean
    PersistentTokenRepository rememberMeTokens(DataSource dataSource) {
        JdbcTokenRepositoryImpl repository = new JdbcTokenRepositoryImpl();
        repository.setDataSource(dataSource);
        return repository;
    }

}
