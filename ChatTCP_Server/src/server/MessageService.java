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

public class MessageService {

    public long saveMessage(long convId, int senderUserId, String type, String contentText) throws Exception {
        String sql =
            "INSERT INTO dbo.Messages(ConversationId, SenderUserId, MessageType, ContentText) " +
            "VALUES(?,?,?,?); SELECT SCOPE_IDENTITY();";
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, convId);
            ps.setInt(2, senderUserId);
            ps.setString(3, type); // TEXT/FILE/IMAGE
            ps.setString(4, contentText);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return ((Number) rs.getObject(1)).longValue();
            }
        }
    }

    public String loadHistory(long convId, int limit) throws Exception {
        StringBuilder sb = new StringBuilder();
        String sql =
            "SELECT TOP (?) m.SentAt, u.Username, m.MessageType, ISNULL(m.ContentText,'') AS ContentText " +
            "FROM dbo.Messages m JOIN dbo.Users u ON u.UserId=m.SenderUserId " +
            "WHERE m.ConversationId=? ORDER BY m.SentAt DESC";
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, limit);
            ps.setLong(2, convId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    sb.append(rs.getString("Username"))
                      .append(" [").append(rs.getString("MessageType")).append("]: ")
                      .append(rs.getString("ContentText"))
                      .append(" (").append(rs.getString("SentAt")).append(")")
                      .append("\n");
                }
            }
        }
        return sb.toString();
    }
}
