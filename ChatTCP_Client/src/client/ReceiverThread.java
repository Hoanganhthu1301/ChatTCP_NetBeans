package client;

import javax.swing.SwingUtilities;
import java.io.DataInputStream;
import java.net.Socket;

/**
 * Single reader thread (ONLY ONE place readUTF/readFully)
 * Supports:
 * - OK|LOGIN|username
 * - USERS|..., GROUPS|..., SYS|...
 * - HISTORY|ALL|\n...
 * - IN|... realtime text
 * - IMAGE_INCOMING|... + bytes
 * - FILE_INCOMING|... + bytes
 * - ATTACH_DATA|mid|filename|size|mime + bytes (for history)
 */
public class ReceiverThread extends Thread {
    private final DataInputStream in;

    public ReceiverThread(Socket socket) throws Exception {
        this.in = new DataInputStream(socket.getInputStream());
        setName("ReceiverThread");
        setDaemon(true);
    }

    public static volatile ReceiverThread instance;
    public static synchronized void startIfNeeded(Socket socket) {
        try {
            if (instance == null || !instance.isAlive()) {
                instance = new ReceiverThread(socket);
                instance.start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        try {
            while (true) {
                String msg = in.readUTF();

                // ===== AUTH =====
                if (msg.startsWith("OK|LOGIN")) {
                    SwingUtilities.invokeLater(() -> {
                        String[] p = msg.split("\\|");
                        if (p.length >= 3) ClientContext.username = p[2];
                        if (ClientContext.main != null) ClientContext.main.onLoginSuccess();
                    });
                    continue;
                }

                if (msg.startsWith("OK|REGISTER")) {
                    SwingUtilities.invokeLater(() -> javax.swing.JOptionPane.showMessageDialog(
                            ClientContext.main,
                            "Đăng ký thành công! Giờ đăng nhập nhé.",
                            "OK",
                            javax.swing.JOptionPane.INFORMATION_MESSAGE
                    ));
                    continue;
                }
                if (msg.startsWith("ERR|USERNAME_OR_EMAIL_EXISTS")) {
                    SwingUtilities.invokeLater(() -> javax.swing.JOptionPane.showMessageDialog(
                            ClientContext.main,
                            "Username hoặc Email đã tồn tại.",
                            "Lỗi",
                            javax.swing.JOptionPane.ERROR_MESSAGE
                    ));
                    continue;
                }

                // ===== STATE =====
                if (msg.startsWith("USERS|")) {
                    String data = msg.substring("USERS|".length());
                    SwingUtilities.invokeLater(() -> ClientContext.main.getUserListPanel().setUsersFromState(data));
                    continue;
                }
                if (msg.startsWith("GROUPS|")) {
                    String data = msg.substring("GROUPS|".length());
                    SwingUtilities.invokeLater(() -> ClientContext.main.getUserListPanel().setGroupsFromState(data));
                    continue;
                }
                if (msg.startsWith("SYS|")) {
                    String s = msg.substring(4).trim();
                    boolean online = s.contains("online") && !s.contains("offline");
                    String user = s.split("\\s+")[0].trim();
                    SwingUtilities.invokeLater(() -> ClientContext.main.getUserListPanel().setUserOnline(user, online));
                    continue;
                }

                // ===== HISTORY =====
                if (msg.startsWith("HISTORY|")) {
                    handleHistory(msg);
                    continue;
                }

                // ===== BLOCK =====
                if (msg.startsWith("ERR|BLOCKED|")) {
                    String reason = msg.substring("ERR|BLOCKED|".length());
                    SwingUtilities.invokeLater(() -> ClientContext.main.getChatPanel().showBlockedBanner(reason));
                    continue;
                }
                if (msg.startsWith("OK|UNBLOCK|")) {
                    String u = msg.substring("OK|UNBLOCK|".length()).trim();
                    SwingUtilities.invokeLater(() -> ClientContext.main.getChatPanel().onUnblocked(u));
                    continue;
                }

                // ===== ATTACH (history) =====
                if (msg.startsWith("ATTACH_DATA|")) {
                    handleAttachData(msg);
                    continue;
                }

                // ===== REALTIME ATTACHMENTS =====
                if (msg.startsWith("IMAGE_INCOMING|") || msg.startsWith("FILE_INCOMING|")) {
                    handleIncomingAttachment(msg);
                    continue;
                }

                // ===== REALTIME TEXT =====
                if (msg.startsWith("IN|")) {
                    handleIncomingChat(msg);
                    continue;
                }

                // ===== FORGOT (optional show) =====
                if (msg.startsWith("OK|FORGOT_REQ")) {
                    // ForgotPasswordPanel tự read khi nó chạy riêng thread (nhưng vẫn ok nếu đến đây)
                    System.out.println("<< " + msg);
                    continue;
                }

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
                String hist = msg.substring("HISTORY|ALL|".length());
                hist = hist.replace("\\r\\n", "\n");
                String key = "ALL";
                String finalHist = hist;
                SwingUtilities.invokeLater(() -> {
                    ClientContext.getHistory(key).setLength(0);
                    ClientContext.getHistory(key).append(finalHist);
                    if (key.equals(ClientContext.currentChatKey)) {
                        ClientContext.main.getChatPanel().loadHistory(key);
                    }
                });
                return;
            }

            if (msg.startsWith("HISTORY|USER|")) {
                String[] p = msg.split("\\|", 4);
                String toUser = p[2].trim();
                String hist = p.length >= 4 ? p[3] : "";
                hist = hist.replace("\\r\\n", "\n");
                String key = "USER:" + toUser;
                String finalHist = hist;
                SwingUtilities.invokeLater(() -> {
                    ClientContext.getHistory(key).setLength(0);
                    ClientContext.getHistory(key).append(finalHist);
                    if (key.equals(ClientContext.currentChatKey)) {
                        ClientContext.main.getChatPanel().loadHistory(key);
                    }
                });
                return;
            }

            if (msg.startsWith("HISTORY|GROUP|")) {
                String[] p = msg.split("\\|", 4);
                String gid = p[2].trim();
                String hist = p.length >= 4 ? p[3] : "";
                hist = hist.replace("\\r\\n", "\n");
                String key = "GROUP:" + gid;
                String finalHist = hist;
                SwingUtilities.invokeLater(() -> {
                    ClientContext.getHistory(key).setLength(0);
                    ClientContext.getHistory(key).append(finalHist);
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
            // IN|ALL|from|text
            // IN|USER|from|text
            // IN|GROUP|gid|from|text
            String[] p = msg.split("\\|", 5);
            String type = p[1];

            if ("ALL".equals(type)) {
                String from = p[2];
                String text = p[3];
                if (from.equalsIgnoreCase(ClientContext.username)) return; // no echo dup
                SwingUtilities.invokeLater(() -> ClientContext.main.getChatPanel().appendLine("ALL",
                        "MSG|0|" + from + "|TEXT|" + text + "|" + java.time.LocalDateTime.now()));
                return;
            }
            if ("USER".equals(type)) {
                String from = p[2];
                String text = p[3];
                if (from.equalsIgnoreCase(ClientContext.username)) return;
                String key = "USER:" + from;
                SwingUtilities.invokeLater(() -> ClientContext.main.getChatPanel().appendLine(key,
                        "MSG|0|" + from + "|TEXT|" + text + "|" + java.time.LocalDateTime.now()));
                return;
            }
            if ("GROUP".equals(type)) {
                String gid = p[2];
                String from = p[3];
                String text = p[4];
                if (from.equalsIgnoreCase(ClientContext.username)) return;
                String key = "GROUP:" + gid;
                SwingUtilities.invokeLater(() -> ClientContext.main.getChatPanel().appendLine(key,
                        "MSG|0|" + from + "|TEXT|" + text + "|" + java.time.LocalDateTime.now()));
            }
        } catch (Exception ignored) {
        }
    }

    private void handleIncomingAttachment(String header) throws Exception {
        // Server:
        // IMAGE_INCOMING|convId|from|fileName|size|mime
        // FILE_INCOMING|convId|from|fileName|size|mime
        // IMAGE_INCOMING|GROUP|gid|from|fileName|size|mime
        // FILE_INCOMING|GROUP|gid|from|fileName|size|mime

        String[] p = header.split("\\|");
        boolean isGroup = p.length >= 7 && "GROUP".equalsIgnoreCase(p[1]);

        String scopeKey;
        String from;
        String fileName;
        int size;
        String mime;
        long messageId = System.currentTimeMillis();

        if (isGroup) {
            String gid = p[2];
            from = p[3];
            fileName = p[4];
            size = Integer.parseInt(p[5]);
            mime = p[6];
            scopeKey = "GROUP:" + gid;
        } else {
            // convId is numeric string
            String convId = p[1];
            from = p[2];
            fileName = p[3];
            size = Integer.parseInt(p[4]);
            mime = p[5];
            // if current chat is USER:from -> store there; else store by current chat key
            if (ClientContext.currentChatKey != null && ClientContext.currentChatKey.startsWith("USER:")) {
                scopeKey = "USER:" + from;
            } else {
                scopeKey = ClientContext.currentChatKey;
            }
        }

        byte[] data = new byte[size];
        in.readFully(data);

        // ignore echo duplicate but MUST read bytes (done) to keep stream aligned
        if (from != null && from.equalsIgnoreCase(ClientContext.username)) {
            return;
        }

        String finalScopeKey = scopeKey;
        long finalMessageId = messageId;
        SwingUtilities.invokeLater(() -> ClientContext.main.getChatPanel()
                .appendIncomingAttachment(finalScopeKey, from, finalMessageId, fileName, mime, size, data));
    }

    private void handleAttachData(String header) throws Exception {
        // ATTACH_DATA|mid|fileName|size|mime
        String[] p = header.split("\\|", 5);
        long mid = Long.parseLong(p[1]);
        String fileName = p[2];
        int size = Integer.parseInt(p[3]);
        String mime = p[4];

        byte[] data = new byte[size];
        in.readFully(data);

        SwingUtilities.invokeLater(() -> ClientContext.main.getChatPanel().onAttachData(mid, fileName, mime, data));
    }
}
