-- Модуль matching: ручна розмітка пар пропозицій для оцінки якості зіставлення (Precision / Recall).
-- Пара зберігається впорядкованою (offer_a_id < offer_b_id), тож кожна пара має не більше однієї мітки.

CREATE TABLE match_label
(
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    offer_a_id    BIGINT       NOT NULL REFERENCES offer (id) ON DELETE CASCADE,
    offer_b_id    BIGINT       NOT NULL REFERENCES offer (id) ON DELETE CASCADE,
    category_code VARCHAR(64)  NOT NULL,
    -- рішення людини: той самий товар чи різні
    same_product  BOOLEAN      NOT NULL,
    -- оцінка й вирішальна ознака на момент розмітки (для аналізу; метрики рахуються за поточною оцінкою)
    score         NUMERIC(4, 3) NOT NULL CHECK (score BETWEEN 0 AND 1),
    reason        VARCHAR(32)  NOT NULL,
    labeled_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT chk_match_label_order CHECK (offer_a_id < offer_b_id),
    CONSTRAINT uq_match_label_pair UNIQUE (offer_a_id, offer_b_id)
);

CREATE INDEX idx_match_label_category ON match_label (category_code);
