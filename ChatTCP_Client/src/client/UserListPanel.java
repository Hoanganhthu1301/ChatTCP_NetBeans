/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package client;

/**
 *
 * @author hoang
 */


import javax.swing.*;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;

public class UserListPanel extends JPanel {
    private final MainFrame main;

    private JLabel lblOnline;
    private JButton btnRefresh;

    private DefaultListModel<String> modelUsers = new DefaultListModel<>();
    private JList<String> listUsers = new JList<>(modelUsers);

    private DefaultListModel<String> modelGroups = new DefaultListModel<>();
    private JList<String> listGroups = new JList<>(modelGroups);

    private JButton btnAll;
    private JButton btnChatUser;

    private JButton btnCreateGroup;
    private JButton btnJoinGroup;
    private JButton btnChatGroup;

    private final Map<String, Boolean> presence = new LinkedHashMap<>();

    public UserListPanel(MainFrame main) {
        this.main = main;
        initUI();
        wire();
    }

    private void initUI() {
        setLayout(new BorderLayout(10, 10));

        JPanel top = new JPanel(new BorderLayout());
        lblOnline = new JLabel("Online: 0");
        btnRefresh = new JButton("Refresh");
        top.add(lblOnline, BorderLayout.WEST);
        top.add(btnRefresh, BorderLayout.EAST);

        JPanel center = new JPanel(new GridLayout(1, 2, 10, 10));

        // Users
        JPanel pUsers = new JPanel(new BorderLayout(5, 5));
        pUsers.setBorder(BorderFactory.createTitledBorder("Users"));
        listUsers.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        pUsers.add(new JScrollPane(listUsers), BorderLayout.CENTER);

        btnChatUser = new JButton("Chat riêng");
        pUsers.add(btnChatUser, BorderLayout.SOUTH);

        // Groups
        JPanel pGroups = new JPanel(new BorderLayout(5, 5));
        pGroups.setBorder(BorderFactory.createTitledBorder("Groups"));
        listGroups.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        pGroups.add(new JScrollPane(listGroups), BorderLayout.CENTER);

        JPanel groupButtons = new JPanel(new GridLayout(1, 3, 5, 5));
        btnCreateGroup = new JButton("Tạo");
        btnJoinGroup = new JButton("Join");
        btnChatGroup = new JButton("Chat nhóm");
        groupButtons.add(btnCreateGroup);
        groupButtons.add(btnJoinGroup);
        groupButtons.add(btnChatGroup);

        pGroups.add(groupButtons, BorderLayout.SOUTH);

        center.add(pUsers);
        center.add(pGroups);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT));
        btnAll = new JButton("💬 Chat tất cả");
        bottom.add(btnAll);

        add(top, BorderLayout.NORTH);
        add(center, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);
    }

    private void wire() {
        btnAll.addActionListener(e -> openAll());

        btnChatUser.addActionListener(e -> openSelectedUser());
        listUsers.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) openSelectedUser();
            }
        });

        btnChatGroup.addActionListener(e -> openSelectedGroup());
        listGroups.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) openSelectedGroup();
            }
        });

        btnRefresh.addActionListener(e -> {
            try {
                ClientContext.out.writeUTF("GET_STATE");
                ClientContext.out.flush();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Không refresh được (mất kết nối?)");
            }
        });

        btnCreateGroup.addActionListener(e -> {
            String name = JOptionPane.showInputDialog(this, "Tên nhóm?");
            if (name == null) return;
            name = name.trim();
            if (name.isEmpty()) return;

            try {
                ClientContext.out.writeUTF("GROUP_CREATE|" + name);
                ClientContext.out.flush();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Tạo nhóm thất bại");
            }
        });

        btnJoinGroup.addActionListener(e -> {
            String gid = JOptionPane.showInputDialog(this, "Nhập group id (vd: g1)");
            if (gid == null) return;
            gid = gid.trim();
            if (gid.isEmpty()) return;

            try {
                ClientContext.out.writeUTF("GROUP_JOIN|" + gid);
                ClientContext.out.flush();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Join nhóm thất bại");
            }
        });
    }

    private void openAll() {
        ClientContext.currentChatKey = "ALL";
        main.showScreen("CHAT");
        main.getChatPanel().setHeader("Chat tất cả");

        // ✅ xin lịch sử ALL
        try {
            ClientContext.out.writeUTF("HISTORY|ALL|50");
            ClientContext.out.flush();
        } catch (Exception ignored) {}

        main.getChatPanel().loadHistory("ALL"); // load cache tạm, server trả về sẽ update lại
    }

    private void openSelectedUser() {
        String item = listUsers.getSelectedValue();
        if (item == null) return;

        String to = item.split(" ")[0].trim(); // "an (online)" -> "an"
        String key = "USER:" + to;

        ClientContext.currentChatKey = key;
        main.showScreen("CHAT");
        main.getChatPanel().setHeader("Chat với: " + to);

        // ✅ xin lịch sử chat riêng
        try {
            ClientContext.out.writeUTF("HISTORY|USER|" + to + "|50");
            ClientContext.out.flush();
        } catch (Exception ignored) {}

        main.getChatPanel().loadHistory(key);
    }

    private void openSelectedGroup() {
        String item = listGroups.getSelectedValue();
        if (item == null) return;

        String gid = item.split(":")[0].trim(); // "g1:TeamA" -> "g1"
        String key = "GROUP:" + gid;

        ClientContext.currentChatKey = key;
        main.showScreen("CHAT");
        main.getChatPanel().setHeader("Nhóm: " + item);

        // ✅ xin lịch sử group (hiện server đang trả rỗng vì group chưa lưu DB)
        try {
            ClientContext.out.writeUTF("HISTORY|GROUP|" + gid + "|50");
            ClientContext.out.flush();
        } catch (Exception ignored) {}

        main.getChatPanel().loadHistory(key);
    }

    // USERS|an:1,thu:1
    public void setUsersFromState(String data) {
        presence.clear();
        modelUsers.clear();

        int onlineCount = 0;

        if (data != null && !data.isBlank()) {
            String[] parts = data.split(",");
            for (String p : parts) {
                if (p.isBlank()) continue;

                String[] uv = p.split(":");
                String u = uv[0].trim();
                boolean on = uv.length > 1 && "1".equals(uv[1].trim());

                if (u.equals(ClientContext.username)) continue;
                presence.put(u, on);
            }
        }

        for (Map.Entry<String, Boolean> e : presence.entrySet()) {
            if (e.getValue()) onlineCount++;
            modelUsers.addElement(e.getKey() + (e.getValue() ? " (online)" : " (offline)"));
        }

        lblOnline.setText("Online: " + onlineCount);
    }

    public void setUserOnline(String username, boolean online) {
        if (username == null || username.isBlank()) return;
        if (username.equals(ClientContext.username)) 
            return;

        presence.put(username, online);

        modelUsers.clear();
        int onlineCount = 0;
        for (Map.Entry<String, Boolean> e : presence.entrySet()) {
            if (e.getValue()) onlineCount++;
            modelUsers.addElement(e.getKey() + (e.getValue() ? " (online)" : " (offline)"));
        }
        lblOnline.setText("Online: " + onlineCount);
    }

    // GROUPS|g1:TeamA,g2:TeamB
    public void setGroupsFromState(String data) {
        modelGroups.clear();
        if (data == null || data.isBlank()) return;

        String[] parts = data.split(",");
        for (String p : parts) {
            if (p.isBlank()) continue;
            modelGroups.addElement(p.trim());
        }
    }
}
