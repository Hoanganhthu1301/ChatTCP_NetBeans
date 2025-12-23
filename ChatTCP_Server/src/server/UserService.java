package server;

import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * Users + Auth + Forgot password (OTP qua Email)
 *
 * YÊU CẦU DB:
 * - Users.Email (UNIQUE)
 * - Users.ResetOtpHash VARBINARY(32) NULL
 * - Users.ResetOtpSalt VARBINARY(16) NULL
 * - Users.ResetOtpExpireAt DATETIME2 NULL
 */
public class UserService {

    public boolean register(String username, String email, String password) throws Exception {
        username = username.trim();
        email = email.trim();

        if (username.isEmpty() || email.isEmpty() || password == null || password.isEmpty()) return false;

        byte[] salt = Crypto.randomSalt16();
        byte[] hash = Crypto.sha256(Crypto.combine(salt, password.getBytes(StandardCharsets.UTF_8)));

        String sql = "INSERT INTO dbo.Users(Username, Email, PasswordHash, PasswordSalt) VALUES(?,?,?,?)";
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, email);
            ps.setBytes(3, hash);
            ps.setBytes(4, salt);
            ps.executeUpdate();
            return true;
        } catch (SQLIntegrityConstraintViolationException dup) {
            return false;
        }
    }

    // legacy (không email) -> return false để client biết cần email
    public boolean register(String username, String password) throws Exception {
        return false;
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
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
                return -1;
            }
        }
    }

    public int getUserIdByEmail(String email) throws Exception {
        String sql = "SELECT UserId FROM dbo.Users WHERE Email=?";
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, email.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
                return -1;
            }
        }
    }

    public String getEmailByUserId(int userId) throws Exception {
        String sql = "SELECT Email FROM dbo.Users WHERE UserId=?";
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString(1);
                return null;
            }
        }
    }

    /**
     * Tạo OTP 6 số, lưu hash+salt+expire vào Users theo email.
     * @return otp plain (để EmailService gửi). Nếu email không tồn tại -> null.
     */
    public String createResetOtp(String email) throws Exception {
    // 1) tìm userId theo email
    String findSql = "SELECT UserId FROM dbo.Users WHERE Email = ? AND IsActive = 1";
    Integer userId = null;

    try (Connection c = Db.getConnection();
         PreparedStatement ps = c.prepareStatement(findSql)) {
        ps.setString(1, email);
        try (ResultSet rs = ps.executeQuery()) {
            if (rs.next()) userId = rs.getInt(1);
        }
    }
    if (userId == null) return null;

    // 2) tạo OTP 6 số
    String otp = String.format("%06d", (int)(Math.random() * 1_000_000));

    // 3) hash OTP + salt
    byte[] salt = randomBytes16();
    byte[] hash = sha256(otp, salt);

    // 4) insert token vào dbo.PasswordResetTokens
    String insSql =
        "INSERT INTO dbo.PasswordResetTokens(UserId, CodeHash, Salt, ExpiresAt, Used) " +
        "VALUES (?, ?, ?, DATEADD(MINUTE, 5, SYSDATETIME()), 0)";

    try (Connection c = Db.getConnection();
         PreparedStatement ps = c.prepareStatement(insSql)) {
        ps.setInt(1, userId);
        ps.setBytes(2, hash);
        ps.setBytes(3, salt);
        ps.executeUpdate();
    }

    return otp; // ClientHandler sẽ gửi mail, fail thì fallback trả OTP
}

    public boolean confirmReset(String email, String otp, String newPass) throws Exception {
    // 1) tìm userId
    String findSql = "SELECT UserId FROM dbo.Users WHERE Email = ? AND IsActive = 1";
    Integer userId = null;

    try (Connection c = Db.getConnection();
         PreparedStatement ps = c.prepareStatement(findSql)) {
        ps.setString(1, email);
        try (ResultSet rs = ps.executeQuery()) {
            if (rs.next()) userId = rs.getInt(1);
        }
    }
    if (userId == null) return false;

    // 2) lấy token mới nhất còn hiệu lực
    String selSql =
        "SELECT TOP 1 Id, CodeHash, Salt " +
        "FROM dbo.PasswordResetTokens " +
        "WHERE UserId = ? AND Used = 0 AND ExpiresAt > SYSDATETIME() " +
        "ORDER BY CreatedAt DESC";

    long tokenId;
    byte[] codeHash;
    byte[] salt;

    try (Connection c = Db.getConnection();
         PreparedStatement ps = c.prepareStatement(selSql)) {
        ps.setInt(1, userId);
        try (ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) return false;
            tokenId = rs.getLong("Id");
            codeHash = rs.getBytes("CodeHash");
            salt = rs.getBytes("Salt");
        }
    }

    // 3) verify
    byte[] incoming = sha256(otp, salt);
    if (!MessageDigest.isEqual(incoming, codeHash)) return false;

    // 4) update pass + set token used (transaction)
    try (Connection c = Db.getConnection()) {
        c.setAutoCommit(false);
        try {
            // hash pass theo kiểu mày đang dùng (ở đây demo SHA-256 + salt)
            byte[] passSalt = randomBytes16();
            byte[] passHash = Crypto.sha256(
                Crypto.combine(passSalt, newPass.getBytes(StandardCharsets.UTF_8))
            );

            String upUser =
                "UPDATE dbo.Users SET PasswordHash=?, PasswordSalt=? WHERE UserId=?";
            try (PreparedStatement ps = c.prepareStatement(upUser)) {
                ps.setBytes(1, passHash);
                ps.setBytes(2, passSalt);
                ps.setInt(3, userId);
                ps.executeUpdate();
            }

            String upTok =
                "UPDATE dbo.PasswordResetTokens SET Used=1 WHERE Id=?";
            try (PreparedStatement ps = c.prepareStatement(upTok)) {
                ps.setLong(1, tokenId);
                ps.executeUpdate();
            }

            c.commit();
            return true;
        } catch (Exception e) {
            c.rollback();
            throw e;
        } finally {
            c.setAutoCommit(true);
        }
    }
}

// ===== helpers =====
private static byte[] randomBytes16() {
    byte[] b = new byte[16];
    new java.security.SecureRandom().nextBytes(b);
    return b;
}

private static byte[] sha256(String s, byte[] salt) throws Exception {
    MessageDigest md = MessageDigest.getInstance("SHA-256");
    md.update(s.getBytes(StandardCharsets.UTF_8));
    md.update(salt);
    return md.digest();
}
}
