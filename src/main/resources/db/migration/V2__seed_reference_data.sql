-- Довідкові дані: магазини та категорії для MVP

INSERT INTO shop (code, name, base_url, crawl_delay_ms)
VALUES ('ROZETKA', 'Rozetka', 'https://rozetka.com.ua', 3000),
       ('COMFY', 'Comfy', 'https://comfy.ua', 3000),
       ('FOXTROT', 'Foxtrot', 'https://www.foxtrot.com.ua', 3000),
       ('ALLO', 'Allo', 'https://allo.ua', 3000),
       ('MOYO', 'Moyo', 'https://www.moyo.ua', 3000);

INSERT INTO category (code, name)
VALUES ('WASHING_MACHINES', 'Пральні машини'),
       ('REFRIGERATORS', 'Холодильники'),
       ('DISHWASHERS', 'Посудомийні машини'),
       ('VACUUM_CLEANERS', 'Пилососи'),
       ('MICROWAVES', 'Мікрохвильові печі');
