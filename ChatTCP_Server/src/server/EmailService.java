/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package server;

/**
 *
 * @author hoang
 */


import java.util.Properties;
import javax.mail.*;
import javax.mail.internet.*;

public class EmailService {

    private final String fromEmail;
    private final String appPassword;

    public EmailService(String fromEmail, String appPassword) {
        this.fromEmail = fromEmail == null ? "" : fromEmail.trim();
        this.appPassword = appPassword == null ? "" : appPassword.replace(" ", "").trim();
    }

    public void sendOtp(String toEmail, String username, String otp) throws MessagingException {
        Properties props = new Properties();

        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.starttls.required", "true");
        props.put("mail.smtp.host", "smtp.gmail.com");
        props.put("mail.smtp.port", "587");

        props.put("mail.smtp.ssl.protocols", "TLSv1.2");
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "10000");
        props.put("mail.smtp.writetimeout", "10000");

        Session session = Session.getInstance(props, new Authenticator() {
            @Override protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(fromEmail, appPassword);
            }
        });

        session.setDebug(true);

        Message msg = new MimeMessage(session);
        msg.setFrom(new InternetAddress(fromEmail));
        msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail, false));
        msg.setSubject("ChatTCP - Reset password (OTP)");
        msg.setText(
            "Hello " + username + ",\n\n" +
            "OTP của bạn là: " + otp + "\n" +
            "Mã có hiệu lực 5 phút.\n\n" +
            "Nếu bạn không yêu cầu, hãy bỏ qua email này."
        );

        Transport.send(msg);
    }
}
