package org.viewer.pricemeter.web.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.viewer.pricemeter.user.service.EmailAlreadyUsedException;
import org.viewer.pricemeter.user.service.UserService;

/**
 * Сторінки входу й реєстрації; сам вхід (POST {@code /login}) і вихід обробляє Spring Security.
 */
@Controller
@RequiredArgsConstructor
public class AuthController {

    private final UserService users;

    @GetMapping("/login")
    public String login() {
        return "auth/login";
    }

    @GetMapping("/register")
    public String registerForm(Model model) {
        model.addAttribute("form", new RegistrationForm("", "", ""));
        return "auth/register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("form") RegistrationForm form, BindingResult errors) {
        if (!errors.hasFieldErrors("password") && !form.password().equals(form.confirmPassword())) {
            errors.rejectValue("confirmPassword", "mismatch", "Паролі не збігаються");
        }
        if (errors.hasErrors()) {
            return "auth/register";
        }
        try {
            users.register(form.email(), form.password());
        } catch (EmailAlreadyUsedException e) {
            errors.rejectValue("email", "taken", "Користувач з таким email уже є");
            return "auth/register";
        }
        return "redirect:/login?registered";
    }

    public record RegistrationForm(
            @NotBlank(message = "Вкажіть email") @Email(message = "Неправильний email") @Size(max = 254) String email,
            @Size(min = UserService.MIN_PASSWORD_LENGTH, max = 100,
                    message = "Пароль — від " + UserService.MIN_PASSWORD_LENGTH + " до 100 символів") String password,
            String confirmPassword) {
    }

}
