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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MessageService {

    private final Connection conn;

    // ✅ để ClientHandler vẫn new MessageService() được
    public MessageService() throws Exception {
        this.conn = Db.getConnection(); // ✅ m sửa Db.getConnection() theo project m
    }

    public MessageService(Connection conn) {
        this.conn = conn;
    }

    public long getLastMessageId(long convId, int senderUserId) throws Exception {
    String sql =
        "SELECT TOP 1 MessageId " +
        "FROM dbo.Messages " +
        "WHERE ConversationId = ? AND SenderUserId = ? " +
        "ORDER BY SentAt DESC, MessageId DESC";
    try (var ps = conn.prepareStatement(sql)) {
        ps.setLong(1, convId);
        ps.setInt(2, senderUserId);
        try (var rs = ps.executeQuery()) {
            if (rs.next()) return rs.getLong(1);
        }
    }
    throw new RuntimeException("Cannot get last messageId");
}

    // ===== GROUP HELPERS =====

    public Long getGroupConvId(String gid) throws SQLException {
        String sql = "SELECT ConvId FROM dbo.Groups WHERE Gid = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, gid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    long v = rs.getLong(1);
                    return rs.wasNull() ? null : v;
                }
            }
        }
        return null;
    }

    public void createGroup(String gid, String name, long convId, int createdByUserId) throws SQLException {
        String sql = "INSERT INTO dbo.Groups(Gid, Name, ConvId, CreatedBy) VALUES(?,?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, gid);
            ps.setString(2, name);
            ps.setLong(3, convId);
            ps.setInt(4, createdByUserId);
            ps.executeUpdate();
        }
    }

    public void addGroupMemberByGid(String gid, int userId) throws SQLException {
        // GroupMembers: (GroupId, UserId, JoinedAt) theo diagram
        // insert ignore duplicate: dùng try/catch
        String sql =
                "INSERT INTO dbo.GroupMembers(GroupId, UserId) " +
                "SELECT g.GroupId, ? FROM dbo.Groups g WHERE g.Gid = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, gid);
            ps.executeUpdate();
        } catch (SQLException ex) {
            // nếu đã tồn tại (unique constraint) thì bỏ qua
        }
    }

    public boolean isUserInGroup(int userId, String gid) throws SQLException {
        String sql =
                "SELECT 1 " +
                "FROM dbo.GroupMembers gm " +
                "JOIN dbo.Groups g ON g.GroupId = gm.GroupId " +
                "WHERE g.Gid = ? AND gm.UserId = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, gid);
            ps.setInt(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public List<String> listGroupMemberUsernames(String gid) throws SQLException {
        String sql =
                "SELECT u.Username " +
                "FROM dbo.GroupMembers gm " +
                "JOIN dbo.Groups g ON g.GroupId = gm.GroupId " +
                "JOIN dbo.Users u ON u.UserId = gm.UserId " +
                "WHERE g.Gid = ?";
        List<String> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, gid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(rs.getString(1));
            }
        }
        return out;
    }

    // ===== MESSAGES (đang dùng cho ALL/USER rồi) =====

    public void saveMessage(long convId, int senderUserId, String messageType, String contentText) throws SQLException {
        String sql =
                "INSERT INTO dbo.Messages(ConversationId, SenderUserId, MessageType, ContentText) " +
                "VALUES(?,?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, convId);
            ps.setInt(2, senderUserId);
            ps.setString(3, messageType);
            ps.setString(4, contentText);
            ps.executeUpdate();
        }
    }



    public String loadHistory(long convId, int limit) throws Exception {
    String sql =
        "SELECT TOP (?) m.MessageId, u.Username, m.MessageType, m.ContentText, m.SentAt " +
        "FROM dbo.Messages m " +
        "JOIN dbo.Users u ON u.UserId = m.SenderUserId " +
        "WHERE m.ConversationId = ? " +
        "ORDER BY m.SentAt DESC, m.MessageId DESC";

    List<String> rows = new ArrayList<>();

    try (PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setInt(1, limit);
        ps.setLong(2, convId);

        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                rows.add(
                    "MSG|" +
                    rs.getLong("MessageId") + "|" +
                    rs.getString("Username") + "|" +
                    rs.getString("MessageType") + "|" +
                    rs.getString("ContentText") + "|" +
                    rs.getTimestamp("SentAt")
                );
            }
        }
    }

    // đảo lại để client nhận cũ -> mới
    Collections.reverse(rows);
    return String.join("\n", rows);
}




    public String nextGid() throws Exception {
    String sql = "SELECT ISNULL(MAX(GroupId),0) + 1 FROM dbo.Groups";
    try (var c = Db.getConnection();
         var ps = c.prepareStatement(sql);
         var rs = ps.executeQuery()) {
        rs.next();
        int next = rs.getInt(1);
        return "g" + next;
    }
}

}
