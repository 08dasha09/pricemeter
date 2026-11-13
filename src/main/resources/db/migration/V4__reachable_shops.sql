-- Rozetka, Foxtrot, Allo закриті Cloudflare-челенджем, Comfy блокує частину запитів (розвідка 2026-10-04).
-- Вимикаємо їх, а не видаляємо, і додаємо магазини, доступні для краулера.

UPDATE shop
SET enabled = FALSE
WHERE code IN ('ROZETKA', 'FOXTROT', 'ALLO', 'COMFY');

INSERT INTO shop (code, name, base_url, crawl_delay_ms)
VALUES ('CITRUS', 'Цитрус', 'https://citrus.ua', 3000),
       ('EPICENTR', 'Епіцентр', 'https://epicentrk.ua', 3000),
       ('ELDORADO', 'Eldorado', 'https://eldorado.ua', 3000);
