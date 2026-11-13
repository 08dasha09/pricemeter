-- Модуль crawler: журнал запусків обходу та блокування планувальника (ShedLock).

CREATE TABLE crawl_run
(
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    shop_code     VARCHAR(32) NOT NULL,
    category_code VARCHAR(64) NOT NULL,
    trigger_type  VARCHAR(16) NOT NULL CHECK (trigger_type IN ('MANUAL', 'SCHEDULED')),
    status        VARCHAR(16) NOT NULL CHECK (status IN ('RUNNING', 'FINISHED', 'FAILED', 'INTERRUPTED')),
    item_limit    INT         NOT NULL CHECK (item_limit > 0),
    started_at    TIMESTAMPTZ NOT NULL,
    finished_at   TIMESTAMPTZ,
    discovered    INT,
    saved         INT,
    created       INT,
    price_changes INT,
    skipped       INT,
    failed        INT,
    -- перші повідомлення про помилки окремих товарів
    errors        JSONB,
    -- причина збою всього обходу
    error         TEXT
);

CREATE INDEX idx_crawl_run_started ON crawl_run (started_at DESC);
CREATE INDEX idx_crawl_run_target ON crawl_run (shop_code, category_code, started_at DESC);
-- не більше одного незавершеного запуску на категорію магазину
CREATE UNIQUE INDEX uq_crawl_run_running ON crawl_run (shop_code, category_code) WHERE status = 'RUNNING';

-- https://github.com/lukas-krecan/ShedLock#jdbctemplate
CREATE TABLE shedlock
(
    name       VARCHAR(64)  NOT NULL PRIMARY KEY,
    lock_until TIMESTAMPTZ  NOT NULL,
    locked_at  TIMESTAMPTZ  NOT NULL,
    locked_by  VARCHAR(255) NOT NULL
);
