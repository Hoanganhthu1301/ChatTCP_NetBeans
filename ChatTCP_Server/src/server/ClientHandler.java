/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package server;

/**
 *
 * @author hoang
 */
import java.sql.*;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;
import java.util.ArrayList;
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

    // ===== GROUP in-memory demo (chỉ để hiển thị list nhanh) =====
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
    EmailService mail = new EmailService(
    "tn1301000@gmail.com",
    "askrdtpjiinwbuyd"
);

    @Override
    public void run() {
        try {
            // =========================
            // 1) AUTH LOOP
            // =========================
            while (true) {
                String cmd = in.readUTF();
                String[] p = cmd.split("\\|");
                if (p.length == 0) continue;

                String op = p[0].trim().toUpperCase();

                // ===== REGISTER (new): REGISTER|username|email|password =====
                if ("REGISTER".equals(op)) {
                    if (p.length < 4) {
                        send("ERR|REGISTER_NEED_EMAIL");
                        continue;
                    }
                    String u = p[1].trim();
                    String email = p[2].trim();
                    String pass = p[3];
                    boolean ok = userService.register(u, email, pass);
                    send(ok ? "OK|REGISTER" : "ERR|USERNAME_OR_EMAIL_EXISTS");
                    continue;
                }

                // ===== FORGOT =====
                if ("FORGOT_REQ".equals(op)) {
                    if (p.length < 2) { send("ERR|Bad FORGOT_REQ"); continue; }
                    String email = p[1].trim();
                    String otp = userService.createResetOtp(email);
                    if (otp == null) { send("ERR|EMAIL_NOT_FOUND"); continue; }

                    // send mail. nếu fail -> fallback trả otp
                    try {
                        System.out.println("[FORGOT] email=" + email);
                        System.out.println("[FORGOT] Mail enabled=" + MailConfig.enabled());
                        System.out.println("[FORGOT] From=" + MailConfig.fromEmail());

                        if (!MailConfig.enabled()) throw new RuntimeException("Mail config missing");

                        EmailService es = new EmailService(MailConfig.fromEmail(), MailConfig.appPassword());

                        es.sendOtp(email, email, otp);

                        send("OK|FORGOT_REQ|EMAIL_SENT");
                    } catch (Exception ex) {
                        System.out.println("[MAIL] SEND FAIL: " + ex);
                        ex.printStackTrace(); // <<< CỰC QUAN TRỌNG: có cái này mới biết vì sao fail
                        send("OK|FORGOT_REQ|OTP|" + otp); // fallback giữ nguyên
                    }

                    continue;
                }
                if ("FORGOT_CONFIRM".equals(op)) {
                    if (p.length < 4) { send("ERR|Bad FORGOT_CONFIRM"); continue; }
                    String email = p[1].trim();
                    String otp = p[2].trim();
                    String newPass = p[3];
                    boolean ok = userService.confirmReset(email, otp, newPass);
                    send(ok ? "OK|FORGOT_CONFIRM" : "ERR|OTP_INVALID");
                    continue;
                }

                // ===== LOGIN: LOGIN|username|password =====
                if ("LOGIN".equals(op)) {
                    if (p.length < 3) { send("ERR|Bad LOGIN"); continue; }
                    String u = p[1].trim();
                    String pass = p[2];
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
        boolean isSticker = p[0].equals("STICKERTO");
    

        String scope = p[1];

        long convId;
        String gidForGroup = null;

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
            gidForGroup = p[2].trim();

            Long c = msgService.getGroupConvId(gidForGroup);
            if (c == null) { send("ERR|Group not found"); return; }
            convId = c;

            // check membership DB (không phụ thuộc in-memory)
            if (!msgService.isUserInGroup(userId, gidForGroup)) { send("ERR|Not a member"); return; }

        } else {
            send("ERR|Scope not supported");
            return;
        }

        String fileName = p[p.length - 3];
        int size = Integer.parseInt(p[p.length - 2]);
        String mime = p[p.length - 1];

        byte[] data = new byte[size];
        in.readFully(data);

        String type = isSticker ? "STICKER" : (isImage ? "IMAGE" : "FILE");

        // ===== LƯU DB (tất cả: ALL/USER/GROUP) =====
        String storagePath = FileUtil.saveBytesToUploads(fileName, data);
        byte[] hash = FileUtil.sha256(data);

        msgService.saveMessage(convId, userId, type, fileName);

// ✅ lấy messageId mới nhất của conversation (cần 1 hàm nhỏ)
        long messageId = msgService.getLastMessageId(convId, userId);
        attService.saveAttachment(messageId, fileName, mime, size, storagePath, hash);


        // ===== realtime: gửi theo members =====
        if ("GROUP".equals(scope)) {
            // members theo DB
            List<String> members = msgService.listGroupMemberUsernames(gidForGroup);
            for (String u : members) {
                ClientHandler h = onlineUsers.get(u);
                if (h == null) continue;

                String head = type + "_INCOMING|GROUP|" + gidForGroup + "|" + username + "|" + fileName + "|" + size + "|" + mime;
                h.send(head);
                h.out.write(data);
                h.out.flush();
            }
            send("OK|SENT|" + type + "|" + fileName);
            return;
        }

        // ALL/USER: theo conv members
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
        if (raw.startsWith("FILETO|") || raw.startsWith("IMAGETO|") || raw.startsWith("STICKERTO|")) {
            handleSendToAttachment(raw);
            return;
        }


        // ===== ATTACH_GET|messageId (client fetch attachment bytes for history) =====
        if (raw.startsWith("ATTACH_GET|")) {
            long mid;
            try {
                mid = Long.parseLong(raw.substring("ATTACH_GET|".length()).trim());
            } catch (Exception e) {
                send("ERR|Bad ATTACH_GET");
                return;
            }
            AttachmentService.AttachmentMeta meta = attService.getAttachmentMeta(mid);
            if (meta == null || meta.storagePath == null) {
                send("ERR|ATTACH_NOT_FOUND|" + mid);
                return;
            }
            java.nio.file.Path path = java.nio.file.Paths.get(meta.storagePath);
            byte[] data;
            try {
                data = java.nio.file.Files.readAllBytes(path);
            } catch (Exception ex) {
                send("ERR|ATTACH_READ_FAIL|" + mid);
                return;
            }
            send("ATTACH_DATA|" + mid + "|" + meta.fileName + "|" + data.length + "|" + meta.mimeType);
            out.write(data);
            out.flush();
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
            int limit = parseLimitSafe(p[3].trim(), 50);

            // ✅ lấy convId từ DB
            Long convId = msgService.getGroupConvId(gid);
            if (convId == null) {
                send("HISTORY|GROUP|" + gid + "|\n");
                return;
            }

            // ✅ check member DB
            if (!msgService.isUserInGroup(userId, gid)) {
                send("ERR|Not a member");
                return;
            }

            String hist = msgService.loadHistory(convId, limit);
            if (hist == null) hist = "";
            send("HISTORY|GROUP|" + gid + "|\n" + hist);
            return;
        }

        // ===== GROUP =====
        if (raw.startsWith("GROUP_CREATE|")) {
            String name = raw.substring("GROUP_CREATE|".length()).trim();
            if (name.isEmpty()) { send("ERR|Missing group name"); return; }

            String gid = msgService.nextGid();


            // ✅ 1) tạo conversation DB cho group
            long convId = convService.createGroupConversation(name, userId);

            // ✅ 2) insert Groups(gid,name,convId,createdBy)
            msgService.createGroup(gid, name, convId, userId);

            // ✅ 3) add creator vào ConversationMembers + GroupMembers
            convService.addMemberIfNotExists(convId, userId, true);
            msgService.addGroupMemberByGid(gid, userId);

            // optional: in-memory cho list UI
            Group g = new Group(gid, name);
            g.members.add(username);
            groups.put(gid, g);

            send("OK|GROUP_CREATE|" + gid + "|" + name);
            sendGroupsState();
            return;
        }

        if (raw.startsWith("GROUP_JOIN|")) {
            String gid = raw.substring("GROUP_JOIN|".length()).trim();
            if (gid.isEmpty()) { send("ERR|Missing gid"); return; }

            Long convId = msgService.getGroupConvId(gid);
            if (convId == null) { send("ERR|Group not found"); return; }

            // ✅ add DB
            convService.addMemberIfNotExists(convId, userId, false);
            msgService.addGroupMemberByGid(gid, userId);

            // optional: in-memory
            Group g = groups.computeIfAbsent(gid, k -> new Group(gid, gid));
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
                broadcast("IN|ALL|" + username + "|" + content, username);
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

                // 1) lấy convId của group từ DB
                Long convId = msgService.getGroupConvId(gid);
                if (convId == null) { send("ERR|GROUP|NOT_FOUND"); return; }

                // ✅ check membership DB
                if (!msgService.isUserInGroup(userId, gid)) { send("ERR|Not a member"); return; }

                // 2) LƯU DB
                msgService.saveMessage(convId, userId, "TEXT", content);

                // 3) realtime: lấy member từ DB
                List<String> members = msgService.listGroupMemberUsernames(gid);
                for (String mem : members) {
                    if (mem.equalsIgnoreCase(username)) continue; // chống lặp
                    ClientHandler h = onlineUsers.get(mem);
                    if (h != null) h.send("IN|GROUP|" + gid + "|" + username + "|" + content);
                }

                return; // ✅ không rớt xuống ERR
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
            broadcast("IN|ALL|" + username + "|" + content, username);
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

        msgService.saveMessage(convId, userId, type, fileName);

        long messageId = msgService.getLastMessageId(convId, userId);
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
    String sql =
        "SELECT g.Gid, g.Name " +
        "FROM dbo.Groups g " +
        "JOIN dbo.GroupMembers gm ON gm.GroupId = g.GroupId " +
        "WHERE gm.UserId = ?";

    StringBuilder sb = new StringBuilder();

    try (Connection c = Db.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

        ps.setInt(1, userId);
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                if (sb.length() > 0) sb.append(",");
                sb.append(rs.getString("Gid"))
                  .append(":")
                  .append(rs.getString("Name"));
            }
        }
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
    private void sendGroupsForUser(int userId) throws Exception {
    String sql =
        "SELECT g.Gid " +
        "FROM Groups g " +
        "JOIN GroupMembers gm ON gm.GroupId = g.GroupId " +
        "WHERE gm.UserId = ?";

    List<String> list = new ArrayList<>();

    try (Connection c = Db.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

        ps.setInt(1, userId);
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(rs.getString(1));
            }
        }
    }

    send("GROUPS|" + String.join(",", list));
}

}
