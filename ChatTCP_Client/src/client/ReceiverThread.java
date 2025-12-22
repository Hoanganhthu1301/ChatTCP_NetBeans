/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package client;

/**
 *
 * @author hoang
 */

import javax.swing.SwingUtilities;
import java.io.DataInputStream;
import java.net.Socket;

public class ReceiverThread extends Thread {
    private final DataInputStream in;

    public ReceiverThread(Socket socket) throws Exception {
        this.in = new DataInputStream(socket.getInputStream());
    }

    @Override
    public void run() {
        try {
            while (true) {
                String msg = in.readUTF();

                // DEBUG: nếu cần nhìn server đang bắn gì
                // System.out.println("<< " + msg);

                // 1) FILE/IMAGE incoming: header rồi read bytes liền
                if (msg.startsWith("FILE_INCOMING|") || msg.startsWith("IMAGE_INCOMING|")) {
                    handleIncomingFile(msg);
                    continue;
                }

                // 2) USERS state
                if (msg.startsWith("USERS|")) {
                    String data = msg.substring("USERS|".length());
                    SwingUtilities.invokeLater(() ->
                            ClientContext.main.getUserListPanel().setUsersFromState(data)
                    );
                    continue;
                }

                // 3) GROUPS state
                if (msg.startsWith("GROUPS|")) {
                    String data = msg.substring("GROUPS|".length());
                    SwingUtilities.invokeLater(() ->
                            ClientContext.main.getUserListPanel().setGroupsFromState(data)
                    );
                    continue;
                }

                // 4) SYS online/offline
                if (msg.startsWith("SYS|")) {
                    String s = msg.substring(4).trim();
                    boolean online = s.contains("online");
                    boolean offline = s.contains("offline");

                    String user = s.split("\\s+")[0].trim();
                    boolean isOnline = online && !offline;

                    SwingUtilities.invokeLater(() ->
                            ClientContext.main.getUserListPanel().setUserOnline(user, isOnline)
                    );
                    continue;
                }

                // 5) HISTORY
                if (msg.startsWith("HISTORY|")) {
                    handleHistory(msg);
                    continue;
                }

                // 6) BLOCK UI (giống Zalo)
                if (msg.startsWith("ERR|BLOCKED|")) {
                    String reason = msg.substring("ERR|BLOCKED|".length());
                    SwingUtilities.invokeLater(() -> {
                        if (ClientContext.main != null && ClientContext.main.getChatPanel() != null) {
                            ClientContext.main.getChatPanel().showBlockedBanner(reason);
                        }
                    });
                    continue;
                }

                if (msg.startsWith("OK|UNBLOCK|")) {
                    String u = msg.substring("OK|UNBLOCK|".length()).trim();
                    SwingUtilities.invokeLater(() -> {
                        if (ClientContext.main != null && ClientContext.main.getChatPanel() != null) {
                            ClientContext.main.getChatPanel().onUnblocked(u);
                        }
                    });
                    continue;
                }

                // 7) IN|... chat message
                if (msg.startsWith("IN|")) {
                    handleIncomingChat(msg);
                    continue;
                }

                // 8) fallback debug
                System.out.println("<< " + msg);
            }

        } catch (Exception e) {
            System.out.println("Mất kết nối server!");
        }
    }

    private void handleHistory(String msg) {
        try {
            // HISTORY|ALL|\n...
            if (msg.startsWith("HISTORY|ALL|")) {
                String hist = msg.substring("HISTORY|ALL|".length()).trim();

                SwingUtilities.invokeLater(() -> {
                    ClientContext.getHistory("ALL").setLength(0);
                    if (!hist.isBlank()) ClientContext.getHistory("ALL").append(hist).append("\n");
                    if ("ALL".equals(ClientContext.currentChatKey)) {
                        ClientContext.main.getChatPanel().loadHistory("ALL");
                    }
                });
                return;
            }

            // HISTORY|USER|diem|\n...
            if (msg.startsWith("HISTORY|USER|")) {
                String[] p = msg.split("\\|", 4);
                String toUser = p[2].trim();
                String hist = p[3].trim();

                String key = "USER:" + toUser;
                SwingUtilities.invokeLater(() -> {
                    ClientContext.getHistory(key).setLength(0);
                    if (!hist.isBlank()) ClientContext.getHistory(key).append(hist).append("\n");
                    if (key.equals(ClientContext.currentChatKey)) {
                        ClientContext.main.getChatPanel().loadHistory(key);
                    }
                });
                return;
            }

            // HISTORY|GROUP|g1|\n...
            if (msg.startsWith("HISTORY|GROUP|")) {
                String[] p = msg.split("\\|", 4);
                String gid = p[2].trim();
                String hist = p[3].trim();

                String key = "GROUP:" + gid;
                SwingUtilities.invokeLater(() -> {
                    ClientContext.getHistory(key).setLength(0);
                    if (!hist.isBlank()) ClientContext.getHistory(key).append(hist).append("\n");
                    if (key.equals(ClientContext.currentChatKey)) {
                        ClientContext.main.getChatPanel().loadHistory(key);
                    }
                });
            }
        } catch (Exception ignored) {
        }
    }

    private void handleIncomingChat(String msg) {
        try {
            // IN|USER|ME|text|TO:xxx
            if (msg.startsWith("IN|USER|ME|")) {
                String[] p = msg.split("\\|", 5);
                String text = p[3];
                String toPart = p[4];
                String to = toPart.startsWith("TO:") ? toPart.substring(3).trim() : "unknown";
                String key = "USER:" + to;

                SwingUtilities.invokeLater(() ->
                        ClientContext.main.getChatPanel().appendLine(key, "Me: " + text)
                );
                return;
            }

            // IN|ALL|from|text
            // IN|USER|from|text
            // IN|GROUP|gid|from|text
            String[] p = msg.split("\\|", 5);
            String type = p[1];

            if ("ALL".equals(type)) {
                String from = p[2];
                String text = p[3];
                String name = from.equals(ClientContext.username) ? "Me" : from;

                SwingUtilities.invokeLater(() ->
                        ClientContext.main.getChatPanel().appendLine("ALL", name + ": " + text)
                );
                return;
            }

            if ("USER".equals(type)) {
                String from = p[2];
                String text = p[3];
                String key = "USER:" + from;

                SwingUtilities.invokeLater(() ->
                        ClientContext.main.getChatPanel().appendLine(key, from + ": " + text)
                );
                return;
            }

            if ("GROUP".equals(type)) {
                String gid = p[2];
                String from = p[3];
                String text = p[4];
                String key = "GROUP:" + gid;

                SwingUtilities.invokeLater(() ->
                        ClientContext.main.getChatPanel().appendLine(key, from + ": " + text)
                );
            }

        } catch (Exception ignored) {
        }
    }

    private void handleIncomingFile(String header) throws Exception {
        // HỖ TRỢ 2 FORMAT (để khỏi lệch server):
        // A) FILE_INCOMING|convId|from|fileName|size|mime
        // B) FILE_INCOMING|from|convId|fileName|size|mime
        String[] p = header.split("\\|", 6);

        String t1 = p[1];
        String t2 = p[2];

        String convId;
        String from;

        // convId thường là số -> nếu t1 là số thì là format A
        if (t1.matches("\\d+")) {
            convId = t1;
            from = t2;
        } else {
            from = t1;
            convId = t2;
        }

        String fileName = p[3];
        int size = Integer.parseInt(p[4]);
        String mime = p[5];

        byte[] data = new byte[size];
        in.readFully(data);

        long id = System.currentTimeMillis();
        PendingStore.put(new PendingFile(id, fileName, from, mime, data));

        // ✅ Đẩy ra UI chat để hiện bubble + nút Lưu + preview
        SwingUtilities.invokeLater(() -> {
            if (ClientContext.main != null && ClientContext.main.getChatPanel() != null) {
                ClientContext.main.getChatPanel()
                        .appendIncomingAttachment(convId, from, id, fileName, mime, size);
            }
        });
    }
}
