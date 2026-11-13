package org.viewer.pricemeter;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Один PostgreSQL на весь прогін тестів. Кожен тестовий клас з власним набором {@code @Import} — окремий Spring-контекст,
 * а кеш контекстів тримає їх усі; власний контейнер на кожен контекст означав би десятки PostgreSQL одночасно.
 * Контексти не зупиняють контейнер ({@code destroyMethod = ""}); прибирає його
 * Ryuk після завершення JVM. Дані між контекстами спільні: тести або відкочують транзакцію ({@code @DataJpaTest}), або
 * очищають таблиці самі.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    /** Кожен закешований контекст тримає свій пул з'єднань (до 10) — стандартних 100 на всі не вистачає. */
    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
                    .withCommand("postgres", "-c", "max_connections=400");

    @Bean(destroyMethod = "")
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        synchronized (POSTGRES) {
            if (!POSTGRES.isRunning()) {
                POSTGRES.start();
            }
        }
        return POSTGRES;
    }

}
