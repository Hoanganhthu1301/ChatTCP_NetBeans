/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package server;

/**
 *
 * @author hoang
 */

import java.security.MessageDigest;
import java.security.SecureRandom;

public class OtpUtil {
    private static final SecureRandom rnd = new SecureRandom();

    public static String genOtp6() {
        int v = rnd.nextInt(1_000_000);
        return String.format("%06d", v);
    }

    public static byte[] randomSalt16() {
        byte[] s = new byte[16];
        rnd.nextBytes(s);
        return s;
    }

    public static byte[] sha256(byte[] salt, String otp) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(salt);
        md.update(otp.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return md.digest();
    }
}
