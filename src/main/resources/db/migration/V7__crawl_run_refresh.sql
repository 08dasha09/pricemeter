-- Обхід оновлює відомі пропозиції за збереженими адресами і деактивує зниклі (404 / 410).

ALTER TABLE crawl_run
    ADD COLUMN refreshed   INT,
    ADD COLUMN deactivated INT;
