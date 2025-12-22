/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package server;

/**
 *
 * @author hoang
 */


import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.UUID;

public class FileUtil {

    public static final String UPLOAD_DIR = "uploads";

    public static void ensureUploadDir() throws Exception {
        Files.createDirectories(new File(UPLOAD_DIR).toPath());
    }

    public static String randomStoredName(String originalName) {
        String ext = "";
        int idx = originalName.lastIndexOf('.');
        if (idx >= 0) ext = originalName.substring(idx);
        return UUID.randomUUID().toString().replace("-", "") + ext;
    }

    public static String saveBytesToUploads(String originalName, byte[] data) throws Exception {
        ensureUploadDir();
        String stored = randomStoredName(originalName);
        File f = new File(UPLOAD_DIR, stored);
        try (FileOutputStream fos = new FileOutputStream(f)) {
            fos.write(data);
        }
        return f.getPath().replace("\\", "/");
    }

    public static byte[] sha256(byte[] data) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(data);
    }
}
