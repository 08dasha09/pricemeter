-- Модуль subscription, крок 1: користувачі (реєстрація, вхід) і токени «запам'ятати мене» Spring Security.

CREATE TABLE app_user
(
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- зберігається в нижньому регістрі
    email         VARCHAR(254) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(16)  NOT NULL CHECK (role IN ('USER', 'ADMIN')),
    created_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_app_user_email UNIQUE (email)
);

-- схема JdbcTokenRepositoryImpl (username — email користувача)
CREATE TABLE persistent_logins
(
    series    VARCHAR(64)  PRIMARY KEY,
    username  VARCHAR(254) NOT NULL,
    token     VARCHAR(64)  NOT NULL,
    last_used TIMESTAMP    NOT NULL
);
