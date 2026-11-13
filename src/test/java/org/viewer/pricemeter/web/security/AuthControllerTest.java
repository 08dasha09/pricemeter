package org.viewer.pricemeter.web.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.viewer.pricemeter.user.service.EmailAlreadyUsedException;
import org.viewer.pricemeter.user.service.UserService;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private UserService users;

    @Test
    void showsLoginAndRegistrationPages() throws Exception {
        mvc.perform(get("/login")).andExpect(status().isOk()).andExpect(view().name("auth/login"));
        mvc.perform(get("/login").param("error", ""))
                .andExpect(content().string(containsString("Неправильний email або пароль")));
        mvc.perform(get("/register")).andExpect(status().isOk()).andExpect(view().name("auth/register"));
    }

    @Test
    void registersAndSendsToLogin() throws Exception {
        mvc.perform(register("buyer@example.com", "secret-123", "secret-123"))
                .andExpect(redirectedUrl("/login?registered"));

        verify(users).register("buyer@example.com", "secret-123");
    }

    @Test
    void rejectsInvalidForm() throws Exception {
        mvc.perform(register("not-an-email", "short", "short"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/register"))
                .andExpect(model().attributeHasFieldErrors("form", "email", "password"))
                .andExpect(content().string(containsString("Пароль — від 8 до 100 символів")));
        mvc.perform(register("buyer@example.com", "secret-123", "secret-456"))
                .andExpect(model().attributeHasFieldErrors("form", "confirmPassword"))
                .andExpect(content().string(containsString("Паролі не збігаються")));

        verify(users, never()).register(any(), any());
    }

    @Test
    void reportsTakenEmail() throws Exception {
        when(users.register("buyer@example.com", "secret-123"))
                .thenThrow(new EmailAlreadyUsedException("buyer@example.com"));

        mvc.perform(register("buyer@example.com", "secret-123", "secret-123"))
                .andExpect(model().attributeHasFieldErrors("form", "email"))
                .andExpect(content().string(containsString("Користувач з таким email уже є")));
    }

    private static RequestBuilder register(String email, String password, String confirm) {
        return post("/register").with(csrf())
                .param("email", email).param("password", password).param("confirmPassword", confirm);
    }

}
