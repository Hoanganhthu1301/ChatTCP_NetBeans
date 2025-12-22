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
import java.sql.ResultSet;

public class BlockService {

    // A block B ?
    public boolean isBlocked(int blockerId, int blockedId) throws Exception {
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT 1 FROM dbo.BlockList WHERE BlockerUserId=? AND BlockedUserId=?"
             )) {
            ps.setInt(1, blockerId);
            ps.setInt(2, blockedId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public void block(int blockerId, int blockedId) throws Exception {
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "IF NOT EXISTS (SELECT 1 FROM dbo.BlockList WHERE BlockerUserId=? AND BlockedUserId=?) " +
                     "INSERT INTO dbo.BlockList(BlockerUserId, BlockedUserId) VALUES(?,?)"
             )) {
            ps.setInt(1, blockerId);
            ps.setInt(2, blockedId);
            ps.setInt(3, blockerId);
            ps.setInt(4, blockedId);
            ps.executeUpdate();
        }
    }

    public void unblock(int blockerId, int blockedId) throws Exception {
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM dbo.BlockList WHERE BlockerUserId=? AND BlockedUserId=?"
             )) {
            ps.setInt(1, blockerId);
            ps.setInt(2, blockedId);
            ps.executeUpdate();
        }
    }
}
