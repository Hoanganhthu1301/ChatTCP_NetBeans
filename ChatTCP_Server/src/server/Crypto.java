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

public class Crypto {
    public static byte[] randomSalt16() {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return salt;
    }

    public static byte[] sha256(byte[] data) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(data);
    }

    public static byte[] combine(byte[] salt, byte[] passwordBytes) {
        byte[] out = new byte[salt.length + passwordBytes.length];
        System.arraycopy(salt, 0, out, 0, salt.length);
        System.arraycopy(passwordBytes, 0, out, salt.length, passwordBytes.length);
        return out;
    }
}

