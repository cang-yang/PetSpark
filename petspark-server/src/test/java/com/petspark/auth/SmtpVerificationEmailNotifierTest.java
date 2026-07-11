package com.petspark.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

class SmtpVerificationEmailNotifierTest {

    @Test
    void registrationMailUsesUtf8BrandedHtmlAndPlainTextFallback() throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class);
        MimeMessage message = new JavaMailSenderImpl().createMimeMessage();
        when(sender.createMimeMessage()).thenReturn(message);

        new SmtpRegistrationEmailNotifier(sender, "petspark@example.com")
                .sendCode("member@example.com", "123456");

        verify(sender).send(message);
        assertThat(message.getSubject()).isEqualTo("PetSpark 注册验证码");
        message.saveChanges();
        assertThat(message.getContent()).isInstanceOf(Multipart.class);
        assertThat(message.getAllRecipients()[0].toString()).isEqualTo("member@example.com");
        assertThat(message.getContentType()).containsIgnoringCase("multipart");
        assertThat(readText(message)).contains("PetSpark 派宠", "注册 PetSpark 账号", "123456", "10 分钟");
    }

    @Test
    void passwordResetMailUsesItsOwnChineseSubject() throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class);
        MimeMessage message = new JavaMailSenderImpl().createMimeMessage();
        when(sender.createMimeMessage()).thenReturn(message);

        new SmtpPasswordResetNotifier(sender, "petspark@example.com")
                .sendCode("member@example.com", "654321");

        verify(sender).send(message);
        assertThat(message.getSubject()).isEqualTo("PetSpark 密码重置验证码");
    }

    private String readText(Part part) throws Exception {
        Object content = part.getContent();
        if (content instanceof String text) {
            assertThat(part.getContentType()).containsIgnoringCase(StandardCharsets.UTF_8.name());
            return text;
        }
        if (content instanceof Multipart multipart) {
            StringBuilder combined = new StringBuilder();
            for (int i = 0; i < multipart.getCount(); i++) {
                combined.append(readText(multipart.getBodyPart(i)));
            }
            return combined.toString();
        }
        return "";
    }
}
