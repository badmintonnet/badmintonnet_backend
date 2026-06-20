package com.tlcn.sportsnet_backend.service;

import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class MailService {

    @Value("${sendgrid.api-key}")
    private String sendgridApiKey;

    @Value("${sendgrid.from-email}")
    private String fromEmail;

    @Async
    public void sendOtpEmail(String toEmail, String otpCode) {
        String subject = "Mã xác thực OTP - BadmintonNet";
        String htmlContent = """
<!DOCTYPE html>
<html lang="vi">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Mã OTP - BadmintonNet</title>
  <style>
    body {
      margin: 0;
      padding: 0;
      background-color: #f3f6fb;
      font-family: 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
      color: #333;
    }
    .container {
      max-width: 600px;
      margin: 40px auto;
      background: #ffffff;
      border-radius: 18px;
      border: 1.5px solid #dbeafe;
      box-shadow: 0 6px 20px rgba(0,0,0,0.08);
      overflow: hidden;
    }
    .header {
      background: linear-gradient(135deg, #005bea, #00c6fb);
      text-align: center;
      padding: 40px 20px;
      color: white;
      position: relative;
      border-bottom: 3px solid #1d4ed8;
    }
    .header h1 {
      font-size: 32px;
      margin: 0;
      font-weight: 900;
      font-style: italic;
      letter-spacing: 1px;
      text-shadow: 0 2px 6px rgba(0,0,0,0.2);
    }
    .header p {
      font-size: 15px;
      margin-top: 10px;
      opacity: 0.95;
    }
    .content {
      padding: 44px 34px;
      text-align: center;
    }
    .content h2 {
      color: #1d4ed8;
      font-size: 22px;
      margin-bottom: 14px;
      font-weight: 700;
    }
    .content p {
      color: #444;
      font-size: 15px;
      line-height: 1.6;
      margin-bottom: 18px;
    }
    .otp-box {
      background: #eef6ff;
      border: 2px dashed #3b82f6;
      border-radius: 12px;
      padding: 32px 0;
      margin: 30px 0;
      box-shadow: inset 0 0 6px rgba(59,130,246,0.2);
    }
    .otp-label {
      font-size: 13px;
      color: #2563eb;
      text-transform: uppercase;
      letter-spacing: 1px;
      margin-bottom: 10px;
    }
    .otp-code {
      font-family: 'Courier New', monospace;
      font-size: 42px;
      font-weight: bold;
      color: #1e3a8a;
      letter-spacing: 12px;
      text-shadow: 0 1px 3px rgba(29,78,216,0.3);
    }
    .note {
      background: #f1f5ff;
      border-left: 4px solid #2563eb;
      padding: 14px 20px;
      border-radius: 10px;
      font-size: 14px;
      color: #1e3a8a;
      text-align: left;
      line-height: 1.5;
    }
    .footer {
      background: #f8fafc;
      border-top: 1px solid #e5e7eb;
      text-align: center;
      padding: 24px;
      font-size: 13px;
      color: #6b7280;
    }
    .footer strong {
      color: #1d4ed8;
    }
  </style>
</head>
<body>
  <div class="container">
    <div class="header">
      <h1>BadmintonNet</h1>
      <p>Cộng đồng cầu lông Việt Nam</p>
    </div>
    <div class="content">
      <h2>Mã xác minh OTP của bạn</h2>
      <p>Xin chào,</p>
      <p>Để hoàn tất quá trình xác thực, vui lòng sử dụng mã OTP bên dưới:</p>
      <div class="otp-box">
        <div class="otp-label">Mã OTP</div>
        <div class="otp-code">%s</div>
      </div>
      <div class="note">
        Mã có hiệu lực trong 30 phút. Vui lòng không chia sẻ mã này với bất kỳ ai để đảm bảo an toàn cho tài khoản của bạn.
      </div>
    </div>
    <div class="footer">
      © 2025 <strong>BadmintonNet</strong> — Nền tảng cộng đồng cầu lông Việt Nam
    </div>
  </div>
</body>
</html>
""".formatted(otpCode);
        sendEmail(toEmail, subject, htmlContent);
    }

    @Async
    public void sendResetPasswordEmail(String toEmail, String newPassword) {
        String subject = "Khôi phục mật khẩu - BadmintonNet";
        String htmlContent = """
<!DOCTYPE html>
<html lang="vi">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Reset Mật khẩu - BadmintonNet</title>
  <style>
    body { margin:0; padding:0; background:#f3f6fb; font-family:'Segoe UI', Roboto, sans-serif; color:#333; }
    .container { max-width:600px; margin:40px auto; background:#fff; border-radius:18px; border:1.5px solid #dbeafe; box-shadow:0 6px 20px rgba(0,0,0,0.08); overflow:hidden; }
    .header { background: linear-gradient(135deg, #005bea, #00c6fb); text-align:center; padding:40px 20px; color:white; border-bottom:3px solid #1d4ed8; }
    .header h1 { font-size:32px; margin:0; font-weight:900; font-style:italic; }
    .content { padding:44px 34px; text-align:center; }
    .content h2 { color:#1d4ed8; font-size:22px; margin-bottom:14px; font-weight:700; }
    .password-box { background:#eef6ff; border:2px dashed #3b82f6; border-radius:12px; padding:32px 0; margin:30px 0; box-shadow: inset 0 0 6px rgba(59,130,246,0.2); }
    .password-label { font-size:13px; color:#2563eb; text-transform:uppercase; letter-spacing:1px; margin-bottom:10px; }
    .password-code { font-family:'Courier New', monospace; font-size:32px; font-weight:bold; color:#1e3a8a; letter-spacing:6px; text-shadow:0 1px 3px rgba(29,78,216,0.3); }
    .note { background:#f1f5ff; border-left:4px solid #2563eb; padding:14px 20px; border-radius:10px; font-size:14px; color:#1e3a8a; text-align:left; line-height:1.5; }
    .footer { background:#f8fafc; border-top:1px solid #e5e7eb; text-align:center; padding:24px; font-size:13px; color:#6b7280; }
    .footer strong { color:#1d4ed8; }
  </style>
</head>
<body>
  <div class="container">
    <div class="header">
      <h1>BadmintonNet</h1>
      <p>Cộng đồng cầu lông Việt Nam</p>
    </div>
    <div class="content">
      <h2>Mật khẩu mới của bạn</h2>
      <p>Xin chào,</p>
      <p>Mật khẩu của bạn đã được reset. Vui lòng sử dụng mật khẩu mới bên dưới để đăng nhập:</p>
      <div class="password-box">
        <div class="password-label">Mật khẩu mới</div>
        <div class="password-code">%s</div>
      </div>
      <div class="note">
        Bạn nên đổi mật khẩu ngay sau khi đăng nhập để đảm bảo an toàn cho tài khoản. Không chia sẻ mật khẩu với bất kỳ ai.
      </div>
    </div>
    <div class="footer">
      © 2025 <strong>BadmintonNet</strong> — Nền tảng cộng đồng cầu lông Việt Nam
    </div>
  </div>
</body>
</html>
""".formatted(newPassword);
        sendEmail(toEmail, subject, htmlContent);
    }

    private void sendEmail(String toEmail, String subject, String htmlContent) {
        Email from = new Email(fromEmail);
        Email to = new Email(toEmail);
        Content content = new Content("text/html", htmlContent);
        Mail mail = new Mail(from, subject, to, content);

        SendGrid sg = new SendGrid(sendgridApiKey);
        Request request = new Request();
        try {
            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());
            Response response = sg.api(request);
            System.out.println("Gửi email tới " + toEmail + " - Status: " + response.getStatusCode());
        } catch (IOException e) {
            System.err.println("Lỗi khi gửi email tới " + toEmail + ": " + e.getMessage());
        }
    }
}
