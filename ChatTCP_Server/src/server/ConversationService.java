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
import java.util.List;

public class ConversationService {

    public long getAllConversationId() throws Exception {
        String sql = "SELECT TOP 1 ConversationId FROM dbo.Conversations WHERE ConversationType='GROUP' AND Title=N'ALL'";
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return rs.getLong(1);
            return -1;
        }
    }

    public void addMemberIfNotExists(long convId, int userId, boolean isAdmin) throws Exception {
        String sql =
            "IF NOT EXISTS (SELECT 1 FROM dbo.ConversationMembers WHERE ConversationId=? AND UserId=?) " +
            "INSERT INTO dbo.ConversationMembers(ConversationId, UserId, IsAdmin) VALUES (?,?,?)";
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, convId);
            ps.setInt(2, userId);
            ps.setLong(3, convId);
            ps.setInt(4, userId);
            ps.setBoolean(5, isAdmin);
            ps.executeUpdate();
        }
    }

    public long getOrCreateDirectConversation(int userA, int userB, int createdByUserId) throws Exception {
        String find =
            "SELECT c.ConversationId " +
            "FROM dbo.Conversations c " +
            "JOIN dbo.ConversationMembers m1 ON m1.ConversationId=c.ConversationId AND m1.UserId=? " +
            "JOIN dbo.ConversationMembers m2 ON m2.ConversationId=c.ConversationId AND m2.UserId=? " +
            "WHERE c.ConversationType='DIRECT'";
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(find)) {
            ps.setInt(1, userA);
            ps.setInt(2, userB);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getLong(1);
            }
        }

        String create =
            "INSERT INTO dbo.Conversations(ConversationType, Title, CreatedByUserId) VALUES('DIRECT', NULL, ?); " +
            "SELECT SCOPE_IDENTITY();";
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(create)) {
            ps.setInt(1, createdByUserId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                long convId = ((Number) rs.getObject(1)).longValue();
                addMemberIfNotExists(convId, userA, false);
                addMemberIfNotExists(convId, userB, false);
                return convId;
            }
        }
    }

    public List<String> listMemberUsernames(long convId) throws Exception {
        List<String> list = new ArrayList<>();
        String sql =
            "SELECT u.Username " +
            "FROM dbo.ConversationMembers cm " +
            "JOIN dbo.Users u ON u.UserId=cm.UserId " +
            "WHERE cm.ConversationId=?";
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, convId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(rs.getString(1));
            }
        }
        return list;
    }
    public long createGroupConversation(String groupName, int createdByUserId) throws Exception {
    // Tạo 1 conversation type GROUP và add creator vào members
    // Tùy schema Conversation của mày, tao giả định có bảng Conversations + ConversationMembers
    // Nếu tên cột khác, mày gửi schema tao map lại đúng 100%.

    try (var c = Db.getConnection()) {

        long convId;
        try (var ps = c.prepareStatement(
                "INSERT INTO dbo.Conversations(Type, Title, CreatedBy) VALUES('GROUP', ?, ?)",
                java.sql.Statement.RETURN_GENERATED_KEYS
        )) {
            ps.setString(1, groupName);
            ps.setInt(2, createdByUserId);
            ps.executeUpdate();
            try (var rs = ps.getGeneratedKeys()) {
                rs.next();
                convId = rs.getLong(1);
            }
        }

        try (var ps = c.prepareStatement(
                "INSERT INTO dbo.ConversationMembers(ConversationId, UserId, IsOwner) VALUES(?,?,1)"
        )) {
            ps.setLong(1, convId);
            ps.setInt(2, createdByUserId);
            ps.executeUpdate();
        }

        return convId;
    }
}

}
