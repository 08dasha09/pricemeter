-- Модуль subscription, крок 3: журнал сповіщень підписникам (і листів, і записів у лог, коли пошту не налаштовано).
-- Товар — без FK і з назвою на момент сповіщення: товар може зникнути після зіставлення, а історія лишається.

CREATE TABLE notification_log
(
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id        BIGINT         NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    product_id     BIGINT         NOT NULL,
    product_title  VARCHAR(512)   NOT NULL,
    -- TARGET_REACHED — ціна не вища за бажану; REAL_DROP — реальне зниження
    kind           VARCHAR(16)    NOT NULL CHECK (kind IN ('TARGET_REACHED', 'REAL_DROP')),
    price          NUMERIC(12, 2) NOT NULL,
    -- ціна, від якої рахували зниження (на момент підписки або попереднього сповіщення)
    previous_price NUMERIC(12, 2),
    shop_name      VARCHAR(128)   NOT NULL,
    -- EMAIL — лист, LOG — пошту не налаштовано, лише запис у лог застосунку
    channel        VARCHAR(8)     NOT NULL CHECK (channel IN ('EMAIL', 'LOG')),
    delivered      BOOLEAN        NOT NULL,
    error          TEXT,
    created_at     TIMESTAMPTZ    NOT NULL
);

CREATE INDEX idx_notification_log_user ON notification_log (user_id, created_at DESC);
