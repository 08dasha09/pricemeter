package org.viewer.pricemeter.web.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.viewer.pricemeter.catalog.service.CatalogService;
import org.viewer.pricemeter.crawler.scheduling.CrawlHealthService;
import org.viewer.pricemeter.crawler.scheduling.CrawlJournal;
import org.viewer.pricemeter.crawler.scheduling.CrawlLauncher;
import org.viewer.pricemeter.crawler.scheduling.CrawlRun;
import org.viewer.pricemeter.user.service.UserService;
import org.viewer.pricemeter.web.admin.CrawlPageController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Правила доступу: адмінські сторінки — лише адміністратору, форми — з CSRF-токеном, {@code curl} — HTTP Basic.
 */
@WebMvcTest({CrawlPageController.class, AuthController.class})
@Import({SecurityConfig.class, SecurityConfigTest.Users.class})
class SecurityConfigTest {

    @TestConfiguration
    static class Users {
        @Bean
        UserDetailsService userDetailsService() {
            return new InMemoryUserDetailsManager(
                    User.withUsername("admin@pricemeter.local").password("{noop}admin-pass").roles("ADMIN").build(),
                    User.withUsername("buyer@example.com").password("{noop}buyer-pass").roles("USER").build());
        }
    }

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CrawlLauncher launcher;

    @MockitoBean
    private CrawlJournal journal;

    @MockitoBean
    private CrawlHealthService crawlHealth;

    @MockitoBean
    private CatalogService catalog;

    @MockitoBean
    private UserService users;

    @Test
    void guestIsSentToLoginFromAdminPages() throws Exception {
        // браузер — на форму входу, curl — 401 з запитом Basic
        mvc.perform(get("/admin/crawl").accept(MediaType.TEXT_HTML)).andExpect(redirectedUrl("/login"));
        mvc.perform(get("/admin/crawl/categories")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    void ordinaryUserCannotOpenAdminPages() throws Exception {
        mvc.perform(get("/admin/crawl")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void formWithoutCsrfTokenIsRejected() throws Exception {
        mvc.perform(post("/admin/crawl/all").param("limit", "20")).andExpect(status().isForbidden());
        verifyNoInteractions(launcher);

        mvc.perform(post("/admin/crawl/all").param("limit", "20").with(csrf()))
                .andExpect(redirectedUrl("/admin/crawl"));
        verify(launcher).launchAll(CrawlRun.Trigger.MANUAL, 20);
    }

    @Test
    void curlWithBasicCredentialsNeedsNoCsrfToken() throws Exception {
        mvc.perform(post("/admin/crawl/all").param("limit", "20").with(httpBasic("admin@pricemeter.local", "admin-pass")))
                .andExpect(redirectedUrl("/admin/crawl"));
        verify(launcher).launchAll(CrawlRun.Trigger.MANUAL, 20);

        mvc.perform(post("/admin/crawl/all").param("limit", "20").with(httpBasic("buyer@example.com", "buyer-pass")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/admin/crawl/all").param("limit", "20").with(httpBasic("admin@pricemeter.local", "wrong")))
                .andExpect(status().isUnauthorized());
        verify(launcher).launchAll(CrawlRun.Trigger.MANUAL, 20);
    }

    @Test
    void loginFormAuthenticatesAndRejectsWrongPassword() throws Exception {
        mvc.perform(post("/login").param("username", "admin@pricemeter.local").param("password", "admin-pass")
                        .with(csrf()))
                .andExpect(redirectedUrl("/"));
        mvc.perform(post("/login").param("username", "admin@pricemeter.local").param("password", "wrong")
                        .with(csrf()))
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void publicPagesAndNavigationForGuest() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Увійти")))
                .andExpect(content().string(not(containsString("/admin/crawl"))));
    }

    @Test
    @WithMockUser(username = "admin@pricemeter.local", roles = "ADMIN")
    void adminSeesAdminMenuAndLogout() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(content().string(containsString("href=\"/admin/crawl\"")))
                .andExpect(content().string(containsString("admin@pricemeter.local")))
                .andExpect(content().string(containsString("action=\"/logout\"")));
    }

    @Test
    @WithMockUser(username = "buyer@example.com", roles = "USER")
    void userDoesNotSeeAdminMenu() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(content().string(containsString("buyer@example.com")))
                .andExpect(content().string(not(containsString("href=\"/admin/crawl\""))));
    }

    @Test
    void onlyBasicAuthorizationHeaderSkipsCsrf() {
        MockHttpServletRequest plain = new MockHttpServletRequest();
        MockHttpServletRequest bearer = new MockHttpServletRequest();
        bearer.addHeader("Authorization", "Bearer abc");
        MockHttpServletRequest basic = new MockHttpServletRequest();
        basic.addHeader("Authorization", "basic YWRtaW46cGFzcw==");

        assertThat(SecurityConfig.hasBasicCredentials(plain)).isFalse();
        assertThat(SecurityConfig.hasBasicCredentials(bearer)).isFalse();
        assertThat(SecurityConfig.hasBasicCredentials(basic)).isTrue();
    }

}
