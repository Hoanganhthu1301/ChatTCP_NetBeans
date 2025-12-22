/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package client;

/**
 *
 * @author hoang
 */


import java.io.DataOutputStream;
import java.net.Socket;
import java.util.Scanner;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;

public class ClientMain {

    public static void main(String[] args) {
        String serverIp = "127.0.0.1";
        int port = 5000;

        try (Socket socket = new Socket(serverIp, port)) {
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            Scanner sc = new Scanner(System.in);

            // Thread nhận dữ liệu từ server
            ReceiverThread rt = new ReceiverThread(socket);
            rt.start();

            // ===== AUTH =====
            System.out.println("1) Register   2) Login");
            System.out.print("Chọn: ");
            String choice = sc.nextLine().trim();

            System.out.print("Username: ");
            String username = sc.nextLine().trim();

            System.out.print("Password: ");
            String pass = sc.nextLine();

            if (choice.equals("1")) {
                out.writeUTF("REGISTER|" + username + "|" + pass);
            } else {
                out.writeUTF("LOGIN|" + username + "|" + pass);
            }
            out.flush();

            System.out.println("\nLệnh:");
            System.out.println("  /all <noi_dung>                 : chat tất cả");
            System.out.println("  /to <username> <noi_dung>       : chat riêng");
            System.out.println("  /file <convId> <path>           : gửi file");
            System.out.println("  /img <convId> <path>            : gửi ảnh");
            System.out.println("  /save <id> <folder_or_path>     : lưu file đã nhận");
            System.out.println("  /logout                         : đăng xuất");
            System.out.println("----------------------------------------");

            // ===== MAIN LOOP =====
            while (true) {
                System.out.print(">> ");
                String line = sc.nextLine();

                // ===== SAVE FILE ĐÃ NHẬN =====
                if (line.startsWith("/save ")) {
                    String[] a = line.split("\\s+", 3);
                    if (a.length < 3) {
                        System.out.println("Dùng: /save <id> <path>");
                        continue;
                    }

                    long id = Long.parseLong(a[1]);
                    String path = a[2];

                    PendingFile pf = PendingStore.take(id);

                    if (pf == null) {
                        System.out.println("❌ Không tìm thấy file cần lưu");
                        continue;
                    }

                    File outFile = new File(path);
                    if (outFile.isDirectory()) {
                        outFile.mkdirs();
                        outFile = new File(outFile, pf.fileName);
                    } else {
                        File parent = outFile.getParentFile();
                        if (parent != null) parent.mkdirs();
                    }

                    try (FileOutputStream fos = new FileOutputStream(outFile)) {
                        fos.write(pf.data);
                    }

                    System.out.println("✅ Đã lưu file tại: " + outFile.getAbsolutePath());
                    continue;
                }

                // ===== LOGOUT =====
                if (line.equalsIgnoreCase("/logout")) {
                    out.writeUTF("LOGOUT|");
                    out.flush();
                    break;
                }

                // ===== SEND FILE =====
                if (line.startsWith("/file ")) {
                    String[] a = line.split("\\s+", 3);
                    if (a.length < 3) {
                        System.out.println("Dùng: /file <convId> <path>");
                        continue;
                    }

                    long convId = Long.parseLong(a[1]);
                    File f = new File(a[2]);
                    if (!f.exists()) {
                        System.out.println("❌ Không thấy file");
                        continue;
                    }

                    byte[] data = Files.readAllBytes(f.toPath());
                    String fileName = f.getName();
                    String mime = "application/octet-stream";

                    out.writeUTF("FILE|" + convId + "|" + fileName + "|" + data.length + "|" + mime);
                    out.flush();
                    out.write(data);
                    out.flush();
                    continue;
                }

                // ===== SEND IMAGE =====
                if (line.startsWith("/img ")) {
                    String[] a = line.split("\\s+", 3);
                    if (a.length < 3) {
                        System.out.println("Dùng: /img <convId> <path>");
                        continue;
                    }

                    long convId = Long.parseLong(a[1]);
                    File f = new File(a[2]);
                    if (!f.exists()) {
                        System.out.println("❌ Không thấy ảnh");
                        continue;
                    }

                    byte[] data = Files.readAllBytes(f.toPath());
                    String fileName = f.getName();
                    String mime = guessImageMime(fileName);

                    out.writeUTF("IMAGE|" + convId + "|" + fileName + "|" + data.length + "|" + mime);
                    out.flush();
                    out.write(data);
                    out.flush();
                    continue;
                }

                // ===== CHAT ALL =====
                if (line.startsWith("/all ")) {
                    out.writeUTF("CHAT|TO:ALL|" + line.substring(5));
                    out.flush();
                    continue;
                }

                // ===== CHAT RIÊNG =====
                if (line.startsWith("/to ")) {
                    String[] p = line.split("\\s+", 3);
                    if (p.length < 3) {
                        System.out.println("Dùng: /to <username> <noi_dung>");
                        continue;
                    }
                    out.writeUTF("CHAT|TO:" + p[1] + "|" + p[2]);
                    out.flush();
                    continue;
                }

                System.out.println("Lệnh không hợp lệ. Gõ /all, /to, /file, /img, /save hoặc /logout");
            }

            System.out.println("Đã thoát client.");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String guessImageMime(String name) {
        String n = name.toLowerCase();
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".gif")) return "image/gif";
        return "application/octet-stream";
    }
}
