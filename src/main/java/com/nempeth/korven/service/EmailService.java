package com.nempeth.korven.service;

import com.nempeth.korven.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final AppProperties appProps;

    @Transactional
    public void sendPasswordResetEmail(String to, String resetLink) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, StandardCharsets.UTF_8.name());
            helper.setFrom(appProps.getMailFrom());
            helper.setTo(to);
            helper.setSubject("Restablecer contraseña - Korven");

            // Template HTML simple
            String html = new String(new ClassPathResource("templates/password-reset.html").getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                    .replace("${RESET_LINK}", resetLink);

            helper.setText(html, true);

            ClassPathResource logo = new ClassPathResource("assets/logo-korven.png");
            helper.addInline("logoKorven", logo, "image/png");

            mailSender.send(message);
        } catch (Exception e) {
            log.error("No se pudo enviar el email de reset a {}", to, e);
            // En ambientes sin SMTP, al menos logueamos el link
            log.info("LINK RESET PARA {} -> {}", to, resetLink);
        }
    }
}
