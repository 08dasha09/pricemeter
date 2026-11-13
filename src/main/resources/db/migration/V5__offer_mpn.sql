-- Код моделі виробника (Schema.org mpn) — найцінніша ознака для зіставлення товарів між магазинами.
ALTER TABLE offer
    ADD COLUMN mpn VARCHAR(128);
