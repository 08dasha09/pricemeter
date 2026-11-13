package org.viewer.pricemeter.fakediscount;

/**
 * Знижки магазину за поточним станом.
 *
 * @param declared   пропозицій із заявленою знижкою
 * @param checked    з них перевірних (є інші магазини з товаром)
 * @param genuine    справжніх
 * @param suspicious підозрілих
 * @param fake       фіктивних
 */
public record ShopDiscountStats(String shopCode, String shopName, int declared, int checked, int genuine,
                                int suspicious, int fake) {

    /** Частка недостовірних (підозрілих і фіктивних) серед перевірних, %; {@code null}, якщо перевірних немає. */
    public Integer untrustworthyPercent() {
        return checked == 0 ? null : (int) Math.round((suspicious + fake) * 100.0 / checked);
    }

}
