-- Модуль subscription, крок 2: підписки користувачів на товари.
-- Товар може зникнути після зіставлення — підписка переходить до товару-наступника (ProductsReplacedEvent),
-- каскадне видалення спрацьовує лише для товару без наступника.

CREATE TABLE subscription
(
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id             BIGINT        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    product_id          BIGINT        NOT NULL REFERENCES product (id) ON DELETE CASCADE,
    -- сповістити, коли найнижча ціна в наявності опуститься до цієї суми; NULL — лише про реальне зниження
    target_price        NUMERIC(12, 2) CHECK (target_price > 0),
    created_at          TIMESTAMPTZ   NOT NULL,
    -- останнє сповіщення: не частіше разу на добу й лише про нову, нижчу ціну
    last_notified_at    TIMESTAMPTZ,
    last_notified_price NUMERIC(12, 2),
    CONSTRAINT uq_subscription_user_product UNIQUE (user_id, product_id)
);

CREATE INDEX idx_subscription_product ON subscription (product_id);
