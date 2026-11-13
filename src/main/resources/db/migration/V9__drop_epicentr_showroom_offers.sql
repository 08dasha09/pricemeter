-- Епіцентр: вітринні зразки («ВІТРИНА! …», адреса /ua/shop/vitryna-*) — уцінені виставкові екземпляри,
-- а не той самий товар, що продається новим: їхня ціна спотворює ринкову медіану й вердикти. Краулер їх
-- більше не збирає; видаляємо вже збережені (історія цін і розмітка пар — каскадом).

DELETE
FROM offer o
    USING shop s
WHERE s.id = o.shop_id
  AND s.code = 'EPICENTR'
  AND (o.external_id LIKE 'vitryna-%' OR upper(o.raw_title) LIKE 'ВІТРИНА%');

-- товари, що лишилися без пропозицій (зіставлення прибрало б їх при наступному перерахунку)
DELETE
FROM product p
WHERE NOT EXISTS (SELECT 1 FROM offer o WHERE o.product_id = p.id);
