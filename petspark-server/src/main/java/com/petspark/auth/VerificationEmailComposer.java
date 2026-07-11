package com.petspark.auth;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

final class VerificationEmailComposer {

    private VerificationEmailComposer() {}

    static void send(JavaMailSender mailSender, String from, String to, String subject, String scene, String code) {
        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            if (from != null && !from.isBlank()) {
                helper.setFrom(from, "PetSpark 派宠");
            }
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(plainText(scene, code), htmlText(scene, code));
        } catch (MessagingException | java.io.UnsupportedEncodingException ex) {
            throw new MailPreparationException("无法创建验证码邮件", ex);
        }
        mailSender.send(message);
    }

    private static String plainText(String scene, String code) {
        return "PetSpark 派宠\n\n你正在进行" + scene + "，验证码：" + code
                + "\n\n验证码 10 分钟内有效，请勿转发或告知他人。若非本人操作，请忽略本邮件。";
    }

    private static String htmlText(String scene, String code) {
        return """
                <!doctype html>
                <html lang="zh-CN">
                <body style="margin:0;padding:32px 12px;background:#f8f4f6;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI','Microsoft YaHei',Arial,sans-serif;color:#263241">
                  <div style="max-width:560px;margin:0 auto;overflow:hidden;background:#ffffff;border:1px solid #f0e2e8;border-radius:24px;box-shadow:0 14px 40px rgba(90,55,72,.10)">
                    <div style="padding:28px 34px;background:linear-gradient(135deg,#df3f76,#ef7694);color:#ffffff">
                      <div style="font-size:24px;font-weight:800;letter-spacing:.2px">PetSpark 派宠</div>
                      <div style="margin-top:6px;font-size:14px;opacity:.9">认真照顾每一次陪伴</div>
                    </div>
                    <div style="padding:34px">
                      <div style="font-size:20px;font-weight:750">%s</div>
                      <p style="margin:12px 0 24px;color:#667085;font-size:15px;line-height:1.7">请在 PetSpark 页面中输入下面的验证码：</p>
                      <div style="padding:20px;text-align:center;background:#fff3f7;border:1px solid #ffd5e2;border-radius:16px;color:#c72f64;font-size:36px;font-weight:850;letter-spacing:10px">%s</div>
                      <p style="margin:22px 0 0;color:#667085;font-size:14px;line-height:1.7">验证码在 <strong>10 分钟</strong>内有效，请勿转发或告知他人。</p>
                      <p style="margin:8px 0 0;color:#98a2b3;font-size:13px;line-height:1.6">若非本人操作，请忽略本邮件。PetSpark 工作人员不会向你索要验证码。</p>
                    </div>
                    <div style="padding:18px 34px;background:#fcfafb;color:#98a2b3;font-size:12px;text-align:center">此邮件由 PetSpark 安全服务自动发送，请勿直接回复。</div>
                  </div>
                </body>
                </html>
                """.formatted(scene, code);
    }
}
