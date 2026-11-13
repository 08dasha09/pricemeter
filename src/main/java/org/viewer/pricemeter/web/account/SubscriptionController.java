package org.viewer.pricemeter.web.account;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.viewer.pricemeter.subscription.service.SubscriptionService;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.Optional;

/**
 * Підписки поточного користувача: «Стежити за ціною» на сторінці товару і сторінка «Мої підписки».
 */
@Controller
@RequestMapping("/account")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptions;

    @GetMapping("/subscriptions")
    public String list(Principal user, Model model) {
        model.addAttribute("subscriptions", subscriptions.list(user.getName()));
        model.addAttribute("notifications", subscriptions.recentNotifications(user.getName()));
        return "account/subscriptions";
    }

    /**
     * Підписатися або змінити бажану ціну.
     *
     * @param targetPrice бажана ціна, як її ввів користувач («15 999», «15999,50»); порожньо — без неї
     */
    @PostMapping("/subscriptions")
    public String subscribe(Principal user, @RequestParam Long productId,
                            @RequestParam(required = false) String targetPrice, RedirectAttributes redirect) {
        Optional<BigDecimal> target;
        try {
            target = parsePrice(targetPrice);
        } catch (NumberFormatException e) {
            redirect.addFlashAttribute("error", "Бажана ціна — додатне число, напр. 15 999");
            return "redirect:/products/" + productId;
        }
        subscriptions.subscribe(user.getName(), productId, target.orElse(null));
        redirect.addFlashAttribute("message", target
                .map(price -> "Стежимо за ціною — сповістимо про реальне зниження або ціну до "
                        + price.stripTrailingZeros().toPlainString() + " ₴")
                .orElse("Стежимо за ціною — сповістимо про реальне зниження"));
        return "redirect:/products/" + productId;
    }

    /** @param back {@code list} — повернутися до «Моїх підписок», інакше — на сторінку товару */
    @PostMapping("/subscriptions/{productId}/delete")
    public String unsubscribe(Principal user, @PathVariable Long productId,
                              @RequestParam(defaultValue = "product") String back, RedirectAttributes redirect) {
        subscriptions.unsubscribe(user.getName(), productId);
        redirect.addFlashAttribute("message", "Більше не стежимо за цим товаром");
        return "list".equals(back) ? "redirect:/account/subscriptions" : "redirect:/products/" + productId;
    }

    /**
     * Посилання «Увійдіть, щоб стежити» для гостя: адреса захищена, тож Spring Security спершу веде на вхід, а після
     * нього повертає сюди — і далі на сторінку товару, вже з кнопкою підписки.
     */
    @GetMapping("/follow/{productId}")
    public String follow(@PathVariable Long productId) {
        return "redirect:/products/" + productId;
    }

    static Optional<BigDecimal> parsePrice(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        BigDecimal price = new BigDecimal(raw.replaceAll("[\\s\\u00a0₴]", "").replace(',', '.'));
        if (price.signum() <= 0 || price.scale() > 2) {
            throw new NumberFormatException("Not a price: " + raw);
        }
        return Optional.of(price);
    }

}
