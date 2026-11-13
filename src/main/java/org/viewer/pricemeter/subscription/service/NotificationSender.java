package org.viewer.pricemeter.subscription.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.viewer.pricemeter.subscription.domain.NotificationLog;

/**
 * Доставка сповіщення: лист, якщо пошту налаштовано ({@code SPRING_MAIL_HOST} тощо — тоді Spring Boot створює
 * {@link JavaMailSender}), інакше — запис у лог застосунку. Помилку надсилання викликач записує в журнал.
 */
@Slf4j
@Component
public class NotificationSender {

    private final JavaMailSender mail;
    private final String from;

    public NotificationSender(ObjectProvider<JavaMailSender> mail, @Value("${app.notifications.from}") String from) {
        this.mail = mail.getIfAvailable();
        this.from = from;
    }

    public NotificationLog.Channel channel() {
        return mail != null ? NotificationLog.Channel.EMAIL : NotificationLog.Channel.LOG;
    }

    /**
     * @throws org.springframework.mail.MailException лист не надіслано
     */
    public void send(String to, String subject, String text) {
        if (mail == null) {
            log.info("Notification for {} (mail is not configured): {}\n{}", to, subject, text);
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        mail.send(message);
    }

}
