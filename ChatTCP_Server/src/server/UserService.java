/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package server;

/**
 *
 * @author hoang
 */

import java.nio.charset.StandardCharsets;
import java.sql.*;

public class UserService {

    public boolean register(String username, String password) throws Exception {
        username = username.trim();

        byte[] salt = Crypto.randomSalt16();
        byte[] hash = Crypto.sha256(Crypto.combine(salt, password.getBytes(StandardCharsets.UTF_8)));

        String sql = "INSERT INTO dbo.Users(Username, PasswordHash, PasswordSalt) VALUES(?,?,?)";
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setBytes(2, hash);
            ps.setBytes(3, salt);
            ps.executeUpdate();
            return true;
        } catch (SQLIntegrityConstraintViolationException dup) {
            return false;
        }
    }

    // đúng -> trả userId, sai -> -1
    public int login(String username, String password) throws Exception {
        String sql = "SELECT UserId, PasswordHash, PasswordSalt FROM dbo.Users WHERE Username=?";
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setString(1, username.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return -1;

                int userId = rs.getInt("UserId");
                byte[] dbHash = rs.getBytes("PasswordHash");
                byte[] salt = rs.getBytes("PasswordSalt");

                byte[] calcHash = Crypto.sha256(
                        Crypto.combine(salt, password.getBytes(StandardCharsets.UTF_8)));

                if (!java.util.Arrays.equals(dbHash, calcHash)) return -1;

                try (PreparedStatement up = c.prepareStatement(
                        "UPDATE dbo.Users SET LastLoginAt=SYSUTCDATETIME() WHERE UserId=?")) {
                    up.setInt(1, userId);
                    up.executeUpdate();
                }
                return userId;
            }
        }
    }

    public long createSession(int userId, String ip, int port) throws Exception {
        String sql = "INSERT INTO dbo.Sessions(UserId, ClientIp, ClientPort) VALUES(?,?,?); SELECT SCOPE_IDENTITY();";
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, ip);
            ps.setInt(3, port);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return ((Number) rs.getObject(1)).longValue();
            }
        }
    }

    public void logoutSession(long sessionId) throws Exception {
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE dbo.Sessions SET LoggedOutAt=SYSUTCDATETIME() WHERE SessionId=?")) {
            ps.setLong(1, sessionId);
            ps.executeUpdate();
        }
    }
    public int getUserIdByUsername(String username) throws Exception {
    String sql = "SELECT UserId FROM dbo.Users WHERE Username=?";
    try (java.sql.Connection c = Db.getConnection();
         java.sql.PreparedStatement ps = c.prepareStatement(sql)) {
        ps.setString(1, username.trim());
        try (java.sql.ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return rs.getInt(1);
            return -1;
        }
    }
}


}

