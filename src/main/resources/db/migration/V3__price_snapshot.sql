-- Модуль pricehistory: історія цін пропозицій.
-- Кожен рядок — інтервал, протягом якого стан ціни не змінювався:
-- valid_from — перше спостереження стану, valid_to — останнє підтвердження.

CREATE TABLE price_snapshot
(
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    offer_id   BIGINT      NOT NULL REFERENCES offer (id) ON DELETE CASCADE,
    price      NUMERIC(12, 2) CHECK (price > 0),
    old_price  NUMERIC(12, 2) CHECK (old_price > 0),
    available  BOOLEAN     NOT NULL,
    valid_from TIMESTAMPTZ NOT NULL,
    valid_to   TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_price_snapshot_interval CHECK (valid_to >= valid_from),
    CONSTRAINT chk_price_snapshot_price_when_available CHECK (NOT available OR price IS NOT NULL)
);

CREATE INDEX idx_price_snapshot_offer_from ON price_snapshot (offer_id, valid_from);
