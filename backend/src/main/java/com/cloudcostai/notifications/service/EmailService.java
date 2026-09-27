package com.cloudcostai.notifications.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class EmailService {
    private final JavaMailSender mailSender;
    private final String host;
    private final String username;
    private final String password;
    private final String from;

    public EmailService(JavaMailSender mailSender,
                        @Value("${spring.mail.host:}") String host,
                        @Value("${spring.mail.username:}") String username,
                        @Value("${spring.mail.password:}") String password,
                        @Value("${app.mail.from:}") String from) {
        this.mailSender = mailSender;
        this.host = host;
        this.username = username;
        this.password = password;
        this.from = from;
    }

    public void send(String recipient, String subject, String type, String account, String period,
                     String currentCost, String budget, String usage, String details) {
        ensureConfigured();
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(from);
            helper.setTo(recipient);
            helper.setSubject(subject);
            helper.setText(html(type, account, period, currentCost, budget, usage, details), true);
            mailSender.send(message);
        } catch (MessagingException | MailException exception) {
            throw new IllegalStateException("Email delivery failed. Verify SMTP settings and the recipient address.", exception);
        }
    }

    private void ensureConfigured() {
        if (host.isBlank() || username.isBlank() || password.isBlank() || from.isBlank()) {
            throw new IllegalStateException("Email notifications are not configured. Set MAIL_HOST, MAIL_USERNAME, MAIL_PASSWORD, and MAIL_FROM.");
        }
    }

    private String html(String type, String account, String period, String currentCost,
                        String budget, String usage, String details) {
        return """
                <!doctype html><html><body style="margin:0;background:#f4f6f8;font-family:Arial,sans-serif;color:#17212b">
                <div style="max-width:640px;margin:32px auto;background:#ffffff;border:1px solid #dce2e8;border-radius:8px;overflow:hidden">
                  <div style="padding:22px 28px;background:#123b45;color:#ffffff;font-size:20px;font-weight:700">CloudCostAI</div>
                  <div style="padding:28px">
                    <h1 style="margin:0 0 20px;font-size:20px">%s</h1>
                    <table style="width:100%%;border-collapse:collapse;font-size:14px">
                      <tr><td style="padding:10px;border-bottom:1px solid #e7ebef;color:#61717e">AWS Account</td><td style="padding:10px;border-bottom:1px solid #e7ebef;text-align:right">%s</td></tr>
                      <tr><td style="padding:10px;border-bottom:1px solid #e7ebef;color:#61717e">Billing Period</td><td style="padding:10px;border-bottom:1px solid #e7ebef;text-align:right">%s</td></tr>
                      <tr><td style="padding:10px;border-bottom:1px solid #e7ebef;color:#61717e">Current Cost</td><td style="padding:10px;border-bottom:1px solid #e7ebef;text-align:right">%s</td></tr>
                      <tr><td style="padding:10px;border-bottom:1px solid #e7ebef;color:#61717e">Budget</td><td style="padding:10px;border-bottom:1px solid #e7ebef;text-align:right">%s</td></tr>
                      <tr><td style="padding:10px;border-bottom:1px solid #e7ebef;color:#61717e">Budget Usage</td><td style="padding:10px;border-bottom:1px solid #e7ebef;text-align:right">%s</td></tr>
                    </table>
                    <div style="margin-top:22px;padding:16px;background:#f4f6f8;border-radius:6px;font-size:14px;line-height:1.6"><strong>Details</strong><br>%s</div>
                  </div>
                  <div style="padding:16px 28px;border-top:1px solid #e7ebef;color:#61717e;font-size:12px">CloudCostAI &mdash; Intelligent FinOps &amp; Cloud Cost Optimization Platform</div>
                </div></body></html>
                """.formatted(escape(type), escape(account), escape(period), escape(currentCost),
                escape(budget), escape(usage), escape(details).replace("\n", "<br>"));
    }

    private String escape(String value) {
        if (value == null) return "Not available";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}