-- Модуль catalog: магазини, категорії, товари, пропозиції магазинів

CREATE TABLE shop
(
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code           VARCHAR(32)  NOT NULL UNIQUE,
    name           VARCHAR(128) NOT NULL,
    base_url       VARCHAR(255) NOT NULL,
    enabled        BOOLEAN      NOT NULL DEFAULT TRUE,
    crawl_delay_ms INTEGER      NOT NULL DEFAULT 2000 CHECK (crawl_delay_ms >= 0)
);

CREATE TABLE category
(
    id   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code VARCHAR(64)  NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL
);

-- Канонічна товарна позиція, до якої зіставляються пропозиції різних магазинів
CREATE TABLE product
(
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    category_id BIGINT       NOT NULL REFERENCES category (id),
    brand       VARCHAR(128),
    model_code  VARCHAR(128),
    title       VARCHAR(512) NOT NULL,
    gtin        VARCHAR(14),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_product_category_brand ON product (category_id, brand);
CREATE INDEX idx_product_model_code ON product (model_code);
CREATE UNIQUE INDEX uq_product_gtin ON product (gtin) WHERE gtin IS NOT NULL;

-- Пропозиція конкретного магазину (сторінка товару в магазині)
CREATE TABLE offer
(
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    shop_id          BIGINT        NOT NULL REFERENCES shop (id),
    category_id      BIGINT REFERENCES category (id),
    external_id      VARCHAR(128)  NOT NULL,
    url              VARCHAR(1024) NOT NULL,
    raw_title        VARCHAR(512)  NOT NULL,
    brand_raw        VARCHAR(128),
    gtin             VARCHAR(14),
    specs            JSONB,
    product_id       BIGINT REFERENCES product (id) ON DELETE SET NULL,
    match_confidence NUMERIC(4, 3) CHECK (match_confidence BETWEEN 0 AND 1),
    match_status     VARCHAR(16)   NOT NULL DEFAULT 'PENDING'
        CHECK (match_status IN ('PENDING', 'AUTO', 'MANUAL', 'REJECTED')),
    active           BOOLEAN       NOT NULL DEFAULT TRUE,
    first_seen_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    last_seen_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_offer_shop_external UNIQUE (shop_id, external_id)
);

CREATE INDEX idx_offer_product ON offer (product_id);
CREATE INDEX idx_offer_category ON offer (category_id);
CREATE INDEX idx_offer_match_status ON offer (match_status) WHERE match_status = 'PENDING';
CREATE INDEX idx_offer_gtin ON offer (gtin) WHERE gtin IS NOT NULL;
