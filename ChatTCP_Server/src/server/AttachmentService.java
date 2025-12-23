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

public class AttachmentService {

    public static class AttachmentMeta {
        public final String fileName;
        public final String mimeType;
        public final long sizeBytes;
        public final String storagePath;

        public AttachmentMeta(String fileName, String mimeType, long sizeBytes, String storagePath) {
            this.fileName = fileName;
            this.mimeType = mimeType;
            this.sizeBytes = sizeBytes;
            this.storagePath = storagePath;
        }
    }

    public void saveAttachment(long messageId, String fileName, String mimeType, long sizeBytes,
                               String storagePath, byte[] fileHash) throws Exception {
        String sql =
            "INSERT INTO dbo.Attachments(MessageId, FileName, MimeType, FileSizeBytes, StoragePath, FileHash) " +
            "VALUES(?,?,?,?,?,?)";
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, messageId);
            ps.setString(2, fileName);
            ps.setString(3, mimeType);
            ps.setLong(4, sizeBytes);
            ps.setString(5, storagePath);
            ps.setBytes(6, fileHash);
            ps.executeUpdate();
        }
    }

    /**
     * Load attachment meta by MessageId. Return null if not found.
     */
    public AttachmentMeta getAttachmentMeta(long messageId) throws Exception {
        String sql = "SELECT FileName, MimeType, FileSizeBytes, StoragePath FROM dbo.Attachments WHERE MessageId=?";
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, messageId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new AttachmentMeta(
                        rs.getString(1),
                        rs.getString(2),
                        rs.getLong(3),
                        rs.getString(4)
                );
            }
        }
    }
}
