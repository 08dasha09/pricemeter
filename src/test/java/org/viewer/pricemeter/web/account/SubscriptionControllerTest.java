package org.viewer.pricemeter.web.account;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.viewer.pricemeter.subscription.domain.NotificationLog;
import org.viewer.pricemeter.subscription.service.SubscriptionService;
import org.viewer.pricemeter.subscription.service.SubscriptionService.SentNotification;
import org.viewer.pricemeter.subscription.service.SubscriptionService.SubscribedProduct;
import org.viewer.pricemeter.web.security.SecurityConfig;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SubscriptionController.class)
@Import(SecurityConfig.class)
class SubscriptionControllerTest {

    private static final String BUYER = "buyer@example.com";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private SubscriptionService subscriptions;

    @Test
    void guestIsSentToLogin() throws Exception {
        mvc.perform(get("/account/subscriptions").accept(MediaType.TEXT_HTML)).andExpect(redirectedUrl("/login"));
        mvc.perform(get("/account/follow/5").accept(MediaType.TEXT_HTML)).andExpect(redirectedUrl("/login"));
        mvc.perform(post("/account/subscriptions").param("productId", "5").with(csrf()).accept(MediaType.TEXT_HTML))
                .andExpect(redirectedUrl("/login"));
        verify(subscriptions, never()).subscribe(anyString(), anyLong(), any());
    }

    @Test
    @WithMockUser(BUYER)
    void followLinkReturnsToProductAfterLogin() throws Exception {
        mvc.perform(get("/account/follow/5")).andExpect(redirectedUrl("/products/5"));
    }

    @Test
    @WithMockUser(BUYER)
    void subscribesWithAndWithoutTargetPrice() throws Exception {
        mvc.perform(post("/account/subscriptions").param("productId", "5").param("targetPrice", "15 999,50")
                        .with(csrf()))
                .andExpect(redirectedUrl("/products/5"))
                .andExpect(flash().attribute("message",
                        "Стежимо за ціною — сповістимо про реальне зниження або ціну до 15999.5 ₴"));
        verify(subscriptions).subscribe(BUYER, 5L, new BigDecimal("15999.50"));

        mvc.perform(post("/account/subscriptions").param("productId", "6").param("targetPrice", " ").with(csrf()))
                .andExpect(redirectedUrl("/products/6"));
        verify(subscriptions).subscribe(BUYER, 6L, null);
    }

    @Test
    @WithMockUser(BUYER)
    void rejectsInvalidTargetPrice() throws Exception {
        mvc.perform(post("/account/subscriptions").param("productId", "5").param("targetPrice", "дешево")
                        .with(csrf()))
                .andExpect(redirectedUrl("/products/5"))
                .andExpect(flash().attributeExists("error"));
        verify(subscriptions, never()).subscribe(anyString(), anyLong(), any());
    }

    @Test
    @WithMockUser(BUYER)
    void unsubscribesAndReturnsWhereAsked() throws Exception {
        mvc.perform(post("/account/subscriptions/5/delete").with(csrf())).andExpect(redirectedUrl("/products/5"));
        mvc.perform(post("/account/subscriptions/5/delete").param("back", "list").with(csrf()))
                .andExpect(redirectedUrl("/account/subscriptions"));
        verify(subscriptions, times(2)).unsubscribe(BUYER, 5L);
    }

    @Test
    @WithMockUser(BUYER)
    void listsSubscriptions() throws Exception {
        when(subscriptions.list(BUYER)).thenReturn(List.of(new SubscribedProduct(5L, "LG F2Y2HS5WE", "Пральні машини",
                new BigDecimal("14999"), 2, 3, new BigDecimal("15000"), LocalDateTime.of(2026, 10, 18, 12, 0))));

        when(subscriptions.recentNotifications(BUYER)).thenReturn(List.of(new SentNotification(5L, "LG F2Y2HS5WE",
                NotificationLog.Kind.REAL_DROP, new BigDecimal("14999"), new BigDecimal("16999"), "Епіцентр",
                NotificationLog.Channel.LOG, true, LocalDateTime.of(2026, 10, 19, 5, 12))));

        mvc.perform(get("/account/subscriptions"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("LG F2Y2HS5WE")))
                .andExpect(content().string(containsString("не вища за бажану")))
                .andExpect(content().string(containsString("з 18.10.2026")))
                .andExpect(content().string(containsString("Останні сповіщення")))
                .andExpect(content().string(containsString("реальне зниження")))
                .andExpect(content().string(containsString("було 16 999 ₴")))
                .andExpect(content().string(containsString("пошту не налаштовано")));
    }

    @Test
    void parsesPricesTheWayPeopleTypeThem() {
        assertThat(SubscriptionController.parsePrice("15 999")).contains(new BigDecimal("15999"));
        assertThat(SubscriptionController.parsePrice("15999,5 ₴")).contains(new BigDecimal("15999.5"));
        assertThat(SubscriptionController.parsePrice("")).isEmpty();
        assertThatThrownBy(() -> SubscriptionController.parsePrice("-5")).isInstanceOf(NumberFormatException.class);
        assertThatThrownBy(() -> SubscriptionController.parsePrice("1.234")).isInstanceOf(NumberFormatException.class);
    }

}
