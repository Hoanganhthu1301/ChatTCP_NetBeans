/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package server;

/**
 *
 * @author hoang
 */


import java.sql.Connection;
import java.sql.PreparedStatement;

public class AttachmentService {

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
}
