/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package server;

/**
 *
 * @author hoang
 */


import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ClientHandler extends Thread {

    final Socket socket;
    private final DataInputStream in;
    private final DataOutputStream out;

    private String username;
    private int userId = -1;
    private long sessionId = -1;

    private final UserService userService = new UserService();
    private final ConversationService convService = new ConversationService();
    private final MessageService msgService = new MessageService();
    private final AttachmentService attService = new AttachmentService();

    // ✅ block contact (DM only)
    private final BlockService blockService = new BlockService();

    private long allConvId = -1;

    // online users
    public static final ConcurrentHashMap<String, ClientHandler> onlineUsers = new ConcurrentHashMap<>();

    // ===== GROUP in-memory demo =====
    static class Group {
        final String id;
        final String name;
        final Set<String> members = ConcurrentHashMap.newKeySet();
        Group(String id, String name) { this.id = id; this.name = name; }
    }
    public static final ConcurrentHashMap<String, Group> groups = new ConcurrentHashMap<>();
    public static final AtomicInteger groupSeq = new AtomicInteger(1);

    public ClientHandler(Socket socket) throws Exception {
        this.socket = socket;
        this.in = new DataInputStream(socket.getInputStream());
        this.out = new DataOutputStream(socket.getOutputStream());
    }

    @Override
    public void run() {
        try {
            // =========================
            // 1) AUTH LOOP
            // =========================
            while (true) {
                String cmd = in.readUTF(); // REGISTER|u|p or LOGIN|u|p
                String[] p = cmd.split("\\|", 3);

                if (p.length < 3) {
                    send("ERR|Use REGISTER|user|pass or LOGIN|user|pass");
                    continue;
                }

                String op = p[0];
                String u = p[1].trim();
                String pass = p[2];

                if ("REGISTER".equalsIgnoreCase(op)) {
                    boolean ok = userService.register(u, pass);
                    send(ok ? "OK|REGISTER" : "ERR|Username exists");
                    continue;
                }

                if ("LOGIN".equalsIgnoreCase(op)) {
                    int uid = userService.login(u, pass);
                    if (uid < 0) {
                        send("ERR|Wrong user/pass");
                        continue;
                    }

                    if (onlineUsers.containsKey(u)) {
                        send("ERR|User online");
                        continue;
                    }

                    this.username = u;
                    this.userId = uid;

                    this.sessionId = userService.createSession(
                            uid,
                            socket.getInetAddress().getHostAddress(),
                            socket.getPort()
                    );

                    onlineUsers.put(username, this);

                    // ALL conversation
                    allConvId = convService.getAllConversationId();
                    if (allConvId > 0) {
                        convService.addMemberIfNotExists(allConvId, userId, false);

                        String hist = msgService.loadHistory(allConvId, 20);
                        if (hist != null && !hist.isBlank()) {
                            send("HISTORY|ALL|\n" + hist);
                        }
                    }

                    send("OK|LOGIN|" + username);

                    broadcast("SYS|" + username + " online", username);

                    // send initial state
                    sendUsersState();
                    sendGroupsState();
                    break;
                }

                send("ERR|Invalid command");
            }

            // =========================
            // 2) MAIN LOOP
            // =========================
            while (true) {
                String msg = in.readUTF();
                try {
                    if (msg.startsWith("FILE|") || msg.startsWith("IMAGE|")) {
                        handleFileIncoming(msg);
                    } else {
                        handleCommand(msg);
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                    try {
                        send("ERR|SERVER|" + ex.getClass().getSimpleName() + "|" + ex.getMessage());
                    } catch (Exception ignore) {}
                }
            }

        } catch (Exception e) {
            // client disconnected
        } finally {
            cleanup();
        }
    }
    private void handleSendToAttachment(String raw) throws Exception {
    // FILETO|ALL|fileName|size|mime
    // FILETO|USER|toUser|fileName|size|mime
    // FILETO|GROUP|gid|fileName|size|mime
    String[] p = raw.split("\\|", 6);
    if (p.length < 5) { send("ERR|Bad FILETO/IMAGETO"); return; }

    boolean isImage = p[0].equals("IMAGETO");
    String scope = p[1];

    long convId;
    if ("ALL".equals(scope)) {
        convId = convService.getAllConversationId();
        convService.addMemberIfNotExists(convId, userId, false);
    } else if ("USER".equals(scope)) {
        if (p.length < 6) { send("ERR|Bad USER FILETO"); return; }
        String toUser = p[2].trim();
        int targetId = userService.getUserIdByUsername(toUser);
        if (targetId < 0) { send("ERR|User not found"); return; }

        // block DM
        if (blockService.isBlocked(userId, targetId)) { send("ERR|BLOCKED|You blocked this user"); return; }
        if (blockService.isBlocked(targetId, userId)) { send("ERR|BLOCKED|You are blocked by this user"); return; }

        convId = convService.getOrCreateDirectConversation(userId, targetId, userId);
    } else if ("GROUP".equals(scope)) {
        if (p.length < 6) { send("ERR|Bad GROUP FILETO"); return; }
        String gid = p[2].trim();
        Group g = groups.get(gid);
        if (g == null) { send("ERR|Group not found"); return; }
        if (!g.members.contains(username)) { send("ERR|Not a member"); return; }

        // group in-memory => tạo conv giả: dùng hash gid (tạm). Nếu mày có group conv DB thì lấy đúng convId.
        // Tạm demo: lưu file kiểu broadcast không lưu DB message.
        convId = -1;
    } else {
        send("ERR|Scope not supported");
        return;
    }

    String fileName = p[p.length - 3];
    int size = Integer.parseInt(p[p.length - 2]);
    String mime = p[p.length - 1];

    byte[] data = new byte[size];
    in.readFully(data);

    // nếu GROUP in-memory: chỉ broadcast file cho member online (không lưu DB)
    if ("GROUP".equals(scope)) {
        String gid = p[2].trim();
        Group g = groups.get(gid);

        for (String mem : g.members) {
            ClientHandler h = onlineUsers.get(mem);
            if (h == null) continue;

            String head = (isImage ? "IMAGE_INCOMING" : "FILE_INCOMING")
                    + "|" + gid + "|" + username + "|" + fileName + "|" + size + "|" + mime;
            h.send(head);
            h.out.write(data);
            h.out.flush();
        }
        send("OK|SENT|" + (isImage ? "IMAGE" : "FILE") + "|" + fileName);
        return;
    }

    // ALL / USER: dùng pipeline cũ lưu DB + broadcast theo convId
    String type = isImage ? "IMAGE" : "FILE";

    String storagePath = FileUtil.saveBytesToUploads(fileName, data);
    byte[] hash = FileUtil.sha256(data);

    long messageId = msgService.saveMessage(convId, userId, type, fileName);
    attService.saveAttachment(messageId, fileName, mime, size, storagePath, hash);

    List<String> members = convService.listMemberUsernames(convId);
    for (String u : members) {
        ClientHandler h = onlineUsers.get(u);
        if (h == null) continue;

        h.send(type + "_INCOMING|" + convId + "|" + username + "|" + fileName + "|" + size + "|" + mime);
        h.out.write(data);
        h.out.flush();
    }

    send("OK|SENT|" + type + "|" + fileName);
}


    private void handleCommand(String raw) throws Exception {
        raw = raw == null ? "" : raw.trim();

        // ===== compatible old LIST commands =====
        if (raw.equalsIgnoreCase("LIST") || raw.equalsIgnoreCase("L") || raw.equalsIgnoreCase("L?")) {
            sendUsersState();
            return;
        }
        if (raw.startsWith("FILETO|") || raw.startsWith("IMAGETO|")) {
            handleSendToAttachment(raw);
            return;
        }

        // ===== STATE =====
        if (raw.equals("GET_STATE")) {
            sendUsersState();
            sendGroupsState();
            return;
        }
        if (raw.equals("GET_USERS")) { sendUsersState(); return; }
        if (raw.equals("GET_GROUPS")) { sendGroupsState(); return; }

        // ===== BLOCK/UNBLOCK direct command =====
        // BLOCK|username
        if (raw.startsWith("BLOCK|")) {
            String target = raw.substring("BLOCK|".length()).trim();
            doBlock(target);
            return;
        }
        // UNBLOCK|username
        if (raw.startsWith("UNBLOCK|")) {
            String target = raw.substring("UNBLOCK|".length()).trim();
            doUnblock(target);
            return;
        }

        // ======================================================
        // ✅ IMPORTANT: intercept "/block" "/unblock" even if sent as MSG
        // ======================================================
        if (raw.startsWith("MSG|")) {
            String[] p = raw.split("\\|", 4);
            if (p.length >= 3) {
                String type = p[1];

                String content = null;
                if ("ALL".equals(type)) content = p[2];
                else if (p.length == 4) content = p[3];

                if (content != null) {
                    String t = content.trim();

                    if (t.startsWith("/block ")) {
                        String target = t.substring(7).trim();
                        doBlock(target);
                        return;
                    }

                    if (t.startsWith("/unblock ")) {
                        String target = t.substring(9).trim();
                        doUnblock(target);
                        return;
                    }
                }
            }
        }

        // ===== HISTORY =====
        if (raw.startsWith("HISTORY|ALL|")) {
            int limit = parseLimitSafe(raw.substring("HISTORY|ALL|".length()).trim(), 50);
            String hist = "";
            if (allConvId > 0) {
                String h = msgService.loadHistory(allConvId, limit);
                if (h != null) hist = h;
            }
            send("HISTORY|ALL|\n" + hist);
            return;
        }

        if (raw.startsWith("HISTORY|USER|")) {
            String[] p = raw.split("\\|", 4);
            if (p.length < 4) { send("ERR|Bad HISTORY|USER|to|limit"); return; }

            String toUser = p[2].trim();
            int limit = parseLimitSafe(p[3].trim(), 50);

            int targetId = userService.getUserIdByUsername(toUser);
            if (targetId < 0) { send("ERR|User not found"); return; }

            long convId = convService.getOrCreateDirectConversation(userId, targetId, userId);
            String hist = msgService.loadHistory(convId, limit);
            if (hist == null) hist = "";

            send("HISTORY|USER|" + toUser + "|\n" + hist);
            return;
        }

        if (raw.startsWith("HISTORY|GROUP|")) {
            String[] p = raw.split("\\|", 4);
            if (p.length < 4) { send("ERR|Bad HISTORY|GROUP|gid|limit"); return; }

            String gid = p[2].trim();
            Group g = groups.get(gid);
            if (g == null) { send("ERR|Group not found"); return; }
            if (!g.members.contains(username)) { send("ERR|Not a member"); return; }

            // in-memory group -> no DB history
            send("HISTORY|GROUP|" + gid + "|\n");
            return;
        }

        // ===== GROUP =====
        if (raw.startsWith("GROUP_CREATE|")) {
            String name = raw.substring("GROUP_CREATE|".length()).trim();
            if (name.isEmpty()) { send("ERR|Missing group name"); return; }

            String gid = "g" + groupSeq.getAndIncrement();
            Group g = new Group(gid, name);
            g.members.add(username);
            groups.put(gid, g);

            send("OK|GROUP_CREATE|" + gid + "|" + name);
            sendGroupsState();
            return;
        }

        if (raw.startsWith("GROUP_JOIN|")) {
            String gid = raw.substring("GROUP_JOIN|".length()).trim();
            Group g = groups.get(gid);
            if (g == null) { send("ERR|Group not found"); return; }

            g.members.add(username);
            send("OK|GROUP_JOIN|" + gid);
            sendGroupsState();
            return;
        }

        // ===== MSG =====
        if (raw.startsWith("MSG|")) {
            String[] p = raw.split("\\|", 4);
            if (p.length < 3) { send("ERR|Bad MSG"); return; }

            String type = p[1];

            if ("ALL".equals(type)) {
                String content = p[2];
                if (allConvId > 0) msgService.saveMessage(allConvId, userId, "TEXT", content);
                broadcast("IN|ALL|" + username + "|" + content, null);
                return;
            }

            if ("USER".equals(type)) {
                if (p.length < 4) { send("ERR|Bad MSG|USER|to|content"); return; }
                String toUser = p[2].trim();
                String content = p[3];

                int targetId = userService.getUserIdByUsername(toUser);
                if (targetId < 0) { send("ERR|User not found"); return; }

                // ✅ BLOCK check for DM only
                if (blockService.isBlocked(userId, targetId)) {
                    send("ERR|BLOCKED|You blocked this user");
                    return;
                }
                if (blockService.isBlocked(targetId, userId)) {
                    send("ERR|BLOCKED|You are blocked by this user");
                    return;
                }

                long directConvId = convService.getOrCreateDirectConversation(userId, targetId, userId);
                msgService.saveMessage(directConvId, userId, "TEXT", content);

                ClientHandler h = onlineUsers.get(toUser);
                if (h != null) h.send("IN|USER|" + username + "|" + content);

                // echo back to sender (client side can display as Me)
                send("IN|USER|ME|" + content + "|TO:" + toUser);
                return;
            }

            if ("GROUP".equals(type)) {
                if (p.length < 4) { send("ERR|Bad MSG|GROUP|gid|content"); return; }
                String gid = p[2].trim();
                String content = p[3];

                Group g = groups.get(gid);
                if (g == null) { send("ERR|Group not found"); return; }
                if (!g.members.contains(username)) { send("ERR|Not a member"); return; }

                // ✅ group does NOT apply block (as requested)
                for (String mem : g.members) {
                    ClientHandler h = onlineUsers.get(mem);
                    if (h == null) continue;
                    h.send("IN|GROUP|" + gid + "|" + username + "|" + content);
                }
                return;
            }

            send("ERR|MSG type not supported");
            return;
        }

        // ===== old CHAT compatible =====
        if (raw.startsWith("CHAT|")) {
            handleChat(raw);
            return;
        }

        send("ERR|Command not supported");
    }

    private void doBlock(String target) throws Exception {
        if (target == null || target.isBlank()) { send("ERR|Missing username"); return; }
        int tid = userService.getUserIdByUsername(target);
        if (tid < 0) { send("ERR|User not found"); return; }
        if (tid == userId) { send("ERR|Cannot block self"); return; }

        blockService.block(userId, tid);
        send("OK|BLOCK|" + target);
    }

    private void doUnblock(String target) throws Exception {
        if (target == null || target.isBlank()) { send("ERR|Missing username"); return; }
        int tid = userService.getUserIdByUsername(target);
        if (tid < 0) { send("ERR|User not found"); return; }

        blockService.unblock(userId, tid);
        send("OK|UNBLOCK|" + target);
    }

    // ===== old CHAT|TO:ALL|content / CHAT|TO:user|content =====
    private void handleChat(String raw) throws Exception {
        String[] p = raw.split("\\|", 3);
        if (p.length < 3 || !"CHAT".equals(p[0])) {
            send("ERR|Bad CHAT. Use CHAT|TO:ALL|msg or CHAT|TO:user|msg");
            return;
        }

        String to = p[1];
        String content = p[2];

        // intercept /block /unblock even in old chat
        String t = content == null ? "" : content.trim();
        if (t.startsWith("/block ")) { doBlock(t.substring(7).trim()); return; }
        if (t.startsWith("/unblock ")) { doUnblock(t.substring(9).trim()); return; }

        if ("TO:ALL".equals(to)) {
            if (allConvId > 0) msgService.saveMessage(allConvId, userId, "TEXT", content);
            broadcast("IN|ALL|" + username + "|" + content, null);
            return;
        }

        if (to.startsWith("TO:")) {
            String targetUsername = to.substring(3).trim();
            if (targetUsername.isEmpty()) {
                send("ERR|Missing target username");
                return;
            }

            int targetId = userService.getUserIdByUsername(targetUsername);
            if (targetId < 0) {
                send("ERR|User not found");
                return;
            }

            // ✅ DM block check
            if (blockService.isBlocked(userId, targetId)) {
                send("ERR|BLOCKED|You blocked this user");
                return;
            }
            if (blockService.isBlocked(targetId, userId)) {
                send("ERR|BLOCKED|You are blocked by this user");
                return;
            }

            long directConvId = convService.getOrCreateDirectConversation(userId, targetId, userId);
            msgService.saveMessage(directConvId, userId, "TEXT", content);

            ClientHandler h = onlineUsers.get(targetUsername);
            if (h != null) h.send("IN|USER|" + username + "|" + content);

            send("IN|USER|ME|" + content + "|TO:" + targetUsername);
            return;
        }

        send("ERR|Bad TO:");
    }

    private void handleFileIncoming(String header) throws Exception {
        // FILE|convId|fileName|size|mime
        // IMAGE|convId|fileName|size|mime
        String[] p = header.split("\\|", 5);
        if (p.length < 5) {
            send("ERR|Bad FILE/IMAGE header");
            return;
        }

        String type = p[0]; // FILE or IMAGE
        long convId = Long.parseLong(p[1]);
        String fileName = p[2];
        long size = Long.parseLong(p[3]);
        String mime = p[4];

        if (size <= 0 || size > 50L * 1024 * 1024) {
            send("ERR|File too large (<=50MB)");
            return;
        }

        byte[] data = new byte[(int) size];
        in.readFully(data);

        String storagePath = FileUtil.saveBytesToUploads(fileName, data);
        byte[] hash = FileUtil.sha256(data);

        long messageId = msgService.saveMessage(convId, userId, type, fileName);
        attService.saveAttachment(messageId, fileName, mime, size, storagePath, hash);

        List<String> members = convService.listMemberUsernames(convId);
        for (String u : members) {
            ClientHandler h = onlineUsers.get(u);
            if (h == null) continue;

            h.send(type + "_INCOMING|" + convId + "|" + username + "|" + fileName + "|" + size + "|" + mime);
            h.out.write(data);
            h.out.flush();
        }

        send("OK|SENT|" + type + "|" + fileName);
    }

    private int parseLimitSafe(String s, int def) {
        try {
            int v = Integer.parseInt(s);
            if (v <= 0) return def;
            if (v > 200) return 200;
            return v;
        } catch (Exception e) {
            return def;
        }
    }

    private void sendUsersState() throws Exception {
        StringBuilder sb = new StringBuilder();
        for (String u : onlineUsers.keySet()) {
            if (sb.length() > 0) sb.append(",");
            sb.append(u).append(":1");
        }
        send("USERS|" + sb);
    }

    private void sendGroupsState() throws Exception {
        StringBuilder sb = new StringBuilder();
        for (Group g : groups.values()) {
            if (username == null) continue;
            if (!g.members.contains(username)) continue;
            if (sb.length() > 0) sb.append(",");
            sb.append(g.id).append(":").append(g.name);
        }
        send("GROUPS|" + sb);
    }

    public void send(String message) throws Exception {
        out.writeUTF(message);
        out.flush();
    }

    private void broadcast(String message, String exceptUser) {
        onlineUsers.forEach((u, h) -> {
            try {
                if (exceptUser != null && exceptUser.equals(u)) return;
                h.send(message);
            } catch (Exception ignored) {}
        });
    }

    private void cleanup() {
        try {
            if (username != null) {
                onlineUsers.remove(username);
                broadcast("SYS|" + username + " offline", username);
            }
            if (sessionId > 0) {
                userService.logoutSession(sessionId);
            }
            socket.close();
        } catch (Exception ignored) {}
    }
}
