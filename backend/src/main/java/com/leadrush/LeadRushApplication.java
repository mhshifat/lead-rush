package com.leadrush;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration;
import org.springframework.boot.autoconfigure.mail.MailSenderValidatorAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Autoconfig exclusions (both are opt-in, not regressions):
 *
 * 1. {@code RedisRepositoriesAutoConfiguration} — we use RedisTemplate directly
 *    from CacheService, never {@code @RedisHash}-annotated Repository interfaces.
 *    Excluding stops Spring Data Redis from scanning every JPA repo on boot.
 *
 * 2. {@code MailSenderAutoConfiguration} + {@code MailSenderValidatorAutoConfiguration}
 *    — Spring Boot's mail starter wants to create a global {@link org.springframework.mail.javamail.JavaMailSender}
 *    bean at boot using {@code spring.mail.*} properties. In our setup each user-
 *    connected Mailbox has its own SMTP credentials, so {@link com.leadrush.email.adapter.impl.SmtpEmailSender}
 *    builds a fresh Jakarta Mail {@code Session} per send — it does NOT depend on
 *    the global bean. Excluding the autoconfig lets the app boot cleanly without
 *    any SMTP env vars set, so deploys succeed before outreach is wired up.
 *    When SMTP_USERNAME is set, {@link com.leadrush.email.service.TransactionalEmailService}
 *    sends through the same adapter; when it's blank, calls are skipped with a warning.
 */
@SpringBootApplication(exclude = {
        RedisRepositoriesAutoConfiguration.class,
        MailSenderAutoConfiguration.class,
        MailSenderValidatorAutoConfiguration.class,
})
@EnableScheduling
public class LeadRushApplication {

    public static void main(String[] args) {
        SpringApplication.run(LeadRushApplication.class, args);
    }
}
