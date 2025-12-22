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

public class GroupService {

    // tạo group: tạo conversation GROUP + insert Groups + add member creator
    public CreatedGroup createGroup(String gid, String name, int createdByUserId,
                                    ConversationService convService) throws Exception {

        long convId = convService.createGroupConversation(name, createdByUserId);

        try (Connection c = Db.getConnection()) {
            // insert group
            int groupId;
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO dbo.Groups(Gid, Name, ConvId, CreatedBy) VALUES(?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS
            )) {
                ps.setString(1, gid);
                ps.setString(2, name);
                ps.setLong(3, convId);
                ps.setInt(4, createdByUserId);
                ps.executeUpdate();

                try (ResultSet rs = ps.getGeneratedKeys()) {
                    rs.next();
                    groupId = rs.getInt(1);
                }
            }

            // add member creator
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO dbo.GroupMembers(GroupId, UserId) VALUES(?,?)"
            )) {
                ps.setInt(1, groupId);
                ps.setInt(2, createdByUserId);
                ps.executeUpdate();
            }

            return new CreatedGroup(groupId, gid, name, convId);
        }
    }

    public GroupInfo findByGid(String gid) throws Exception {
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT GroupId, Gid, Name, ConvId FROM dbo.Groups WHERE Gid=?"
             )) {
            ps.setString(1, gid);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new GroupInfo(
                        rs.getInt("GroupId"),
                        rs.getString("Gid"),
                        rs.getString("Name"),
                        rs.getLong("ConvId")
                );
            }
        }
    }

    public boolean isMember(int groupId, int userId) throws Exception {
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT 1 FROM dbo.GroupMembers WHERE GroupId=? AND UserId=?"
             )) {
            ps.setInt(1, groupId);
            ps.setInt(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public void joinGroup(int groupId, int userId) throws Exception {
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "IF NOT EXISTS (SELECT 1 FROM dbo.GroupMembers WHERE GroupId=? AND UserId=?) " +
                     "INSERT INTO dbo.GroupMembers(GroupId, UserId) VALUES(?,?)"
             )) {
            ps.setInt(1, groupId);
            ps.setInt(2, userId);
            ps.setInt(3, groupId);
            ps.setInt(4, userId);
            ps.executeUpdate();
        }
    }

    public List<String> listGroupsOfUser(int userId) throws Exception {
        List<String> out = new ArrayList<>();
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT g.Gid, g.Name FROM dbo.Groups g " +
                     "JOIN dbo.GroupMembers m ON g.GroupId=m.GroupId " +
                     "WHERE m.UserId=? ORDER BY g.GroupId"
             )) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(rs.getString("Gid") + ":" + rs.getString("Name"));
                }
            }
        }
        return out;
    }

    public record GroupInfo(int groupId, String gid, String name, long convId) {}
    public record CreatedGroup(int groupId, String gid, String name, long convId) {}
}
