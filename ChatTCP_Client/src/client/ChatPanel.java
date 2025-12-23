package client;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.nio.file.Files;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Chat UI kiểu Zalo (Swing)
 * - Tin cũ ở trên, tin mới ở dưới
 * - Bubble trái/phải + avatar
 * - Gửi ảnh/file: preview + nút Lưu
 * - Lịch sử ảnh/file: placeholder rồi xin lại bytes (ATTACH_GET)
 */
public class ChatPanel extends JPanel {
    private final MainFrame main;

    // header
    private JLabel lblHeader;
    private JButton btnBack;
    private JButton btnBlock;
    private JButton btnSendImage;
    private JButton btnSendFile;

    // blocked banner
    private JPanel blockedBar;
    private JLabel lblBlocked;
    private JButton btnUnblock;
    private boolean blocked = false;

    // messages
    private JPanel messagesPanel;
    private JScrollPane scroll;

    // input
    private JTextField txtInput;
    private JButton btnSend;

    // current peer user (DM)
    private String currentPeerUser = null;

    // map mid->image label to update after ATTACH_DATA
    private final Map<Long, JLabel> midToImageLabel = new HashMap<>();
    private final Map<Long, JLabel> midToFileLabel = new HashMap<>();

    public ChatPanel(MainFrame main) {
        this.main = main;
        initUI();
        wire();
    }

    private void initUI() {
        setLayout(new BorderLayout(10, 10));
        setBorder(new EmptyBorder(8, 8, 8, 8));

        // ===== top =====
        JPanel top = new JPanel(new BorderLayout(10, 10));
        top.setOpaque(false);
        btnBack = new JButton("← Back");
        lblHeader = new JLabel("Chat");
        lblHeader.setFont(lblHeader.getFont().deriveFont(Font.BOLD, 14f));

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        btnBlock = new JButton("Chặn");
        btnSendImage = new JButton("Gửi ảnh");
        btnSendFile = new JButton("Gửi file");
        right.add(btnBlock);
        right.add(btnSendImage);
        right.add(btnSendFile);

        top.add(btnBack, BorderLayout.WEST);
        top.add(lblHeader, BorderLayout.CENTER);
        top.add(right, BorderLayout.EAST);

        blockedBar = new JPanel(new BorderLayout(10, 0));
        blockedBar.setBorder(new EmptyBorder(8, 10, 8, 10));
        blockedBar.setBackground(new Color(240, 240, 240));
        lblBlocked = new JLabel("Bạn không thể nhắn tin.");
        lblBlocked.setForeground(Color.DARK_GRAY);
        btnUnblock = new JButton("Bỏ chặn");
        btnUnblock.setVisible(false);
        blockedBar.add(lblBlocked, BorderLayout.CENTER);
        blockedBar.add(btnUnblock, BorderLayout.EAST);
        blockedBar.setVisible(false);

        JPanel north = new JPanel();
        north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));
        north.setOpaque(false);
        north.add(top);
        north.add(blockedBar);

        // ===== messages =====
        messagesPanel = new JPanel();
        messagesPanel.setLayout(new BoxLayout(messagesPanel, BoxLayout.Y_AXIS));
        messagesPanel.setBackground(new Color(245, 245, 245));
        messagesPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        scroll = new JScrollPane(messagesPanel);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(new Color(245, 245, 245));
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        // ===== bottom =====
        JPanel bottom = new JPanel(new BorderLayout(8, 8));
        txtInput = new JTextField();
        btnSend = new JButton("Send");
        bottom.add(txtInput, BorderLayout.CENTER);
        bottom.add(btnSend, BorderLayout.EAST);

        add(north, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);
    }

    private void wire() {
        btnBack.addActionListener(e -> main.showScreen("USERS"));
        btnSend.addActionListener(e -> sendText());
        txtInput.addActionListener(e -> sendText());

        btnBlock.addActionListener(e -> doBlockUI());
        btnUnblock.addActionListener(e -> doUnblockUI());

        btnSendFile.addActionListener(e -> doSendFile(false));
        btnSendImage.addActionListener(e -> doSendFile(true));
    }

    public void setHeader(String s) {
        lblHeader.setText(s == null ? "Chat" : s);
    }

    public void onOpenChatKey(String key) {
        currentPeerUser = null;
        if (key != null && key.startsWith("USER:")) {
            currentPeerUser = key.substring("USER:".length());
        }
        hideBlockedBanner();
    }

    // =========================
    // BLOCK UI
    // =========================
    public void showBlockedBanner(String serverReason) {
        if (ClientContext.currentChatKey == null || !ClientContext.currentChatKey.startsWith("USER:")) return;

        String reason = (serverReason == null) ? "" : serverReason.trim().toLowerCase();
        String text;
        boolean canUnblock = false;
        if (reason.contains("you blocked")) {
            text = "Bạn đã chặn tin nhắn";
            canUnblock = true;
        } else if (reason.contains("blocked by")) {
            text = "Bạn không thể nhắn tin vì người này đã chặn bạn";
        } else {
            text = (serverReason == null || serverReason.isBlank()) ? "Bạn không thể nhắn tin." : serverReason;
        }

        blocked = true;
        blockedBar.setVisible(true);
        lblBlocked.setText(text);
        btnUnblock.setVisible(canUnblock);
        txtInput.setEnabled(false);
        btnSend.setEnabled(false);
        txtInput.setText("");
        revalidate();
        repaint();
    }

    public void hideBlockedBanner() {
        blocked = false;
        blockedBar.setVisible(false);
        btnUnblock.setVisible(false);
        txtInput.setEnabled(true);
        btnSend.setEnabled(true);
        revalidate();
        repaint();
    }

    public void onUnblocked(String user) {
        if (user == null) return;
        if (ClientContext.currentChatKey != null && ClientContext.currentChatKey.equals("USER:" + user)) {
            hideBlockedBanner();
        }
    }

    private void doBlockUI() {
        if (currentPeerUser == null) {
            JOptionPane.showMessageDialog(this, "Chỉ chặn trong chat riêng.");
            return;
        }
        sendRaw("BLOCK|" + currentPeerUser);
        showBlockedBanner("You blocked this user");
    }

    private void doUnblockUI() {
        if (currentPeerUser == null) return;
        sendRaw("UNBLOCK|" + currentPeerUser);
    }

    private void sendRaw(String raw) {
        try {
            synchronized (ClientContext.out) {
                ClientContext.out.writeUTF(raw);
                ClientContext.out.flush();
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Không gửi được lệnh (mất kết nối?)");
        }
    }

    // =========================
    // HISTORY
    // =========================
    public void loadHistory(String key) {
        onOpenChatKey(key);
        messagesPanel.removeAll();
        midToImageLabel.clear();
        midToFileLabel.clear();

        String raw = ClientContext.getHistory(key).toString();
        java.util.List<MsgLine> lines = parseHistory(raw);
        lines.sort(Comparator.comparingLong(a -> a.sortKey));

        LocalDate lastDate = null;
        for (MsgLine m : lines) {
            if (m.date != null && !m.date.equals(lastDate)) {
                messagesPanel.add(dateSeparator(m.date));
                lastDate = m.date;
            }

            if ("IMAGE".equalsIgnoreCase(m.type)) {
                addImageBubblePlaceholder(m);
                requestAttachment(m.messageId);
            } else if ("FILE".equalsIgnoreCase(m.type)) {
                addFileBubblePlaceholder(m);
                requestAttachment(m.messageId);
            } else {
                addTextBubble(m);
            }
            messagesPanel.add(Box.createVerticalStrut(6));
        }

        messagesPanel.revalidate();
        messagesPanel.repaint();
        scrollToBottom();
    }

    // =========================
    // SEND TEXT
    // =========================
    private void sendText() {
        if (blocked) { Toolkit.getDefaultToolkit().beep(); return; }
        String text = txtInput.getText().trim();
        if (text.isEmpty()) return;
        String key = ClientContext.currentChatKey;
        if (key == null) return;

        // preview ngay
        appendLine(key, "INTERNAL_PREVIEW|" + ClientContext.username + "|TEXT|" + text + "|" + nowServerLike());

        try {
            synchronized (ClientContext.out) {
                if ("ALL".equals(key)) {
                    ClientContext.out.writeUTF("MSG|ALL|" + text);
                } else if (key.startsWith("USER:")) {
                    String to = key.substring("USER:".length());
                    ClientContext.out.writeUTF("MSG|USER|" + to + "|" + text);
                } else if (key.startsWith("GROUP:")) {
                    String gid = key.substring("GROUP:".length());
                    ClientContext.out.writeUTF("MSG|GROUP|" + gid + "|" + text);
                }
                ClientContext.out.flush();
            }
            txtInput.setText("");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Gửi thất bại (mất kết nối?)");
        }
    }

    // =========================
    // SEND FILE / IMAGE
    // =========================
    private void doSendFile(boolean imageOnly) {
        if (blocked) { Toolkit.getDefaultToolkit().beep(); return; }
        String key = ClientContext.currentChatKey;
        if (key == null) return;

        JFileChooser fc = new JFileChooser();
        if (imageOnly) {
            fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                    "Images", "png", "jpg", "jpeg", "gif", "webp"));
        }
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File f = fc.getSelectedFile();
        try {
            String fileName = f.getName();
            byte[] data = Files.readAllBytes(f.toPath());
            String mime = guessMime(fileName, imageOnly);

            long pendingId = System.currentTimeMillis();
            PendingStore.put(new PendingFile(pendingId, fileName, ClientContext.username, mime, data));

            // preview bubble
            appendOutgoingAttachment(key, pendingId, fileName, mime, data.length);

            String header;
            if ("ALL".equals(key)) {
                header = (imageOnly ? "IMAGETO" : "FILETO") + "|ALL|" + fileName + "|" + data.length + "|" + mime;
            } else if (key.startsWith("USER:")) {
                String to = key.substring("USER:".length());
                header = (imageOnly ? "IMAGETO" : "FILETO") + "|USER|" + to + "|" + fileName + "|" + data.length + "|" + mime;
            } else if (key.startsWith("GROUP:")) {
                String gid = key.substring("GROUP:".length());
                header = (imageOnly ? "IMAGETO" : "FILETO") + "|GROUP|" + gid + "|" + fileName + "|" + data.length + "|" + mime;
            } else {
                JOptionPane.showMessageDialog(this, "ChatKey không hợp lệ!");
                return;
            }

            synchronized (ClientContext.out) {
                ClientContext.out.writeUTF(header);
                ClientContext.out.flush();
                ClientContext.out.write(data);
                ClientContext.out.flush();
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Gửi thất bại: " + ex.getMessage());
        }
    }

    private void appendOutgoingAttachment(String chatKey, long pendingId, String fileName, String mime, int size) {
        ClientContext.getHistory(chatKey)
                .append("MSG|").append(pendingId).append("|")
                .append(ClientContext.username).append("|")
                .append(mime.startsWith("image/") ? "IMAGE" : "FILE").append("|")
                .append(fileName).append("|")
                .append(nowServerLike()).append("\n");

        if (!chatKey.equals(ClientContext.currentChatKey)) return;

        // render like incoming attachment but align right
        String type = mime.startsWith("image/") ? "IMAGE" : "FILE";
        MsgLine m = new MsgLine(pendingId, ClientContext.username, type, fileName, nowServerLike());
        m.mine = true;
        if ("IMAGE".equals(type)) addImageBubbleFromBytes(m, PendingStore.get(pendingId).data);
        else addFileBubbleFromBytes(m, PendingStore.get(pendingId).data, mime);

        messagesPanel.add(Box.createVerticalStrut(6));
        messagesPanel.revalidate();
        messagesPanel.repaint();
        scrollToBottom();
    }

    // =========================
    // PUBLIC API for ReceiverThread
    // =========================
    public void appendLine(String key, String line) {
        if (key == null || line == null) return;
        ClientContext.getHistory(key).append(line).append("\n");
        if (!key.equals(ClientContext.currentChatKey)) return;

        MsgLine m = parseAnyLine(line);
        if (m == null) return;
        if (m.date != null) {
            // ensure day separator if needed (only when appending live)
            Component last = messagesPanel.getComponentCount() > 0 ? messagesPanel.getComponent(messagesPanel.getComponentCount()-1) : null;
            LocalDate lastDate = null;
            if (last instanceof DateSeparatorMarker dsm) lastDate = dsm.date;
            // We don't reliably track last separator here; keep it simple: do not auto insert separator on live.
        }
        if ("IMAGE".equalsIgnoreCase(m.type)) {
            addImageBubblePlaceholder(m);
            requestAttachment(m.messageId);
        } else if ("FILE".equalsIgnoreCase(m.type)) {
            addFileBubblePlaceholder(m);
            requestAttachment(m.messageId);
        } else {
            addTextBubble(m);
        }

        messagesPanel.add(Box.createVerticalStrut(6));
        messagesPanel.revalidate();
        messagesPanel.repaint();
        scrollToBottom();
    }

    /** realtime incoming attachment (server push bytes) */
    public void appendIncomingAttachment(String scopeKey, String from, long messageId, String fileName, String mime, int size, byte[] data) {
        // store history like server lines so loadHistory works
        String type = mime != null && mime.startsWith("image/") ? "IMAGE" : "FILE";
        String keyToStore = scopeKey;
        if (keyToStore == null) {
            // DM fallback
            keyToStore = "USER:" + from;
        }

        ClientContext.getHistory(keyToStore)
                .append("MSG|").append(messageId).append("|")
                .append(from).append("|")
                .append(type).append("|")
                .append(fileName).append("|")
                .append(nowServerLike()).append("\n");

        if (!keyToStore.equals(ClientContext.currentChatKey)) return;

        MsgLine m = new MsgLine(messageId, from, type, fileName, nowServerLike());
        m.mine = from != null && from.equalsIgnoreCase(ClientContext.username);

        if ("IMAGE".equals(type)) addImageBubbleFromBytes(m, data);
        else addFileBubbleFromBytes(m, data, mime);

        messagesPanel.add(Box.createVerticalStrut(6));
        messagesPanel.revalidate();
        messagesPanel.repaint();
        scrollToBottom();
    }

    /** history attachment fetch result */
    public void onAttachData(long mid, String fileName, String mime, byte[] data) {
        PendingStore.put(new PendingFile(mid, fileName, "", mime, data));

        JLabel imgLabel = midToImageLabel.get(mid);
        if (imgLabel != null && mime != null && mime.startsWith("image/")) {
            ImageIcon icon = new ImageIcon(data);
            imgLabel.setIcon(new ImageIcon(scaleImage(icon, 240, 240)));
            imgLabel.setText(null);
            messagesPanel.revalidate();
            messagesPanel.repaint();
            return;
        }

        JLabel fileLabel = midToFileLabel.get(mid);
        if (fileLabel != null) {
            fileLabel.setText("📎 " + fileName + " (đã tải)");
            messagesPanel.revalidate();
            messagesPanel.repaint();
        }
    }

    // =========================
    // RENDER HELPERS
    // =========================
    private void addTextBubble(MsgLine m) {
        messagesPanel.add(messageRowText(m.sender, m.content, m.hhmm, m.mine));
    }

    private void addImageBubblePlaceholder(MsgLine m) {
        JPanel row = messageRowAttachment(m.sender, true, m.mine);
        JLabel img = new JLabel("(đang tải ảnh...)");
        img.setForeground(Color.GRAY);
        img.setBorder(new EmptyBorder(6, 0, 6, 0));
        ((JPanel)row.getClientProperty("bubble")).add(img, BorderLayout.CENTER);
        midToImageLabel.put(m.messageId, img);
        messagesPanel.add(row);
    }

    private void addFileBubblePlaceholder(MsgLine m) {
        JPanel row = messageRowAttachment(m.sender, false, m.mine);
        JLabel lb = new JLabel("📎 " + m.content + " (đang tải...)");
        lb.setBorder(new EmptyBorder(6, 0, 6, 0));
        ((JPanel)row.getClientProperty("bubble")).add(lb, BorderLayout.CENTER);
        midToFileLabel.put(m.messageId, lb);
        messagesPanel.add(row);
    }

    private void addImageBubbleFromBytes(MsgLine m, byte[] data) {
        JPanel row = messageRowAttachment(m.sender, true, m.mine);
        JPanel bubble = (JPanel) row.getClientProperty("bubble");

        JLabel img = new JLabel();
        img.setBorder(new EmptyBorder(6, 0, 6, 0));
        try {
            ImageIcon icon = new ImageIcon(data);
            img.setIcon(new ImageIcon(scaleImage(icon, 240, 240)));
        } catch (Exception ex) {
            img.setText("(Không render được ảnh)");
        }
        bubble.add(img, BorderLayout.CENTER);

        JButton btnSave = new JButton("Lưu");
        btnSave.addActionListener(e -> FileSaveUtil.saveWithChooser(this, m.content, data));
        bubble.add(btnSave, BorderLayout.EAST);

        messagesPanel.add(row);
    }

    private void addFileBubbleFromBytes(MsgLine m, byte[] data, String mime) {
        JPanel row = messageRowAttachment(m.sender, false, m.mine);
        JPanel bubble = (JPanel) row.getClientProperty("bubble");

        JLabel lb = new JLabel("📎 " + m.content);
        lb.setBorder(new EmptyBorder(6, 0, 6, 0));
        bubble.add(lb, BorderLayout.CENTER);

        JButton btnSave = new JButton("Lưu");
        btnSave.addActionListener(e -> FileSaveUtil.saveWithChooser(this, m.content, data));
        bubble.add(btnSave, BorderLayout.EAST);

        // right click copy name
        lb.setComponentPopupMenu(filePopup(m.content));

        messagesPanel.add(row);
    }

    private JPopupMenu filePopup(String fileName) {
        JPopupMenu m = new JPopupMenu();
        JMenuItem copy = new JMenuItem("Copy tên file");
        copy.addActionListener(e -> Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(fileName), null));
        m.add(copy);
        return m;
    }

    private JPanel messageRowText(String sender, String text, String hhmm, boolean mine) {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);

        if (!mine) {
            JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            left.setOpaque(false);
            left.add(new AvatarCircle(sender));
            left.add(new Bubble(sender, text, hhmm, false));
            row.add(left, BorderLayout.WEST);
        } else {
            JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
            right.setOpaque(false);
            right.add(new Bubble("Me", text, hhmm, true));
            row.add(right, BorderLayout.EAST);
        }
        return row;
    }

    /**
     * returns a row panel; inside bubble panel is stored at clientProperty("bubble")
     */
    private JPanel messageRowAttachment(String sender, boolean isImage, boolean mine) {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);

        JPanel bubble = new RoundedPanel(mine ? new Color(200, 230, 255) : Color.WHITE);
        bubble.setLayout(new BorderLayout(10, 6));
        bubble.setBorder(new EmptyBorder(10, 12, 10, 12));

        JPanel wrap = new JPanel(new FlowLayout(mine ? FlowLayout.RIGHT : FlowLayout.LEFT, 0, 0));
        wrap.setOpaque(false);

        if (!mine) {
            // left: avatar + bubble
            JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            left.setOpaque(false);
            left.add(new AvatarCircle(sender));
            left.add(bubble);
            wrap.add(left);
            row.add(wrap, BorderLayout.WEST);
        } else {
            wrap.add(bubble);
            row.add(wrap, BorderLayout.EAST);
        }
        row.putClientProperty("bubble", bubble);
        return row;
    }

    private JPanel dateSeparator(LocalDate date) {
        DateSeparatorMarker p = new DateSeparatorMarker(date);
        p.setLayout(new FlowLayout(FlowLayout.CENTER, 0, 0));
        p.setOpaque(false);
        JLabel lb = new JLabel(formatVietnameseDay(date));
        lb.setOpaque(true);
        lb.setForeground(Color.DARK_GRAY);
        lb.setBackground(new Color(220, 220, 220));
        lb.setBorder(new EmptyBorder(4, 10, 4, 10));
        p.add(lb);
        return p;
    }

    private void scrollToBottom() {
        SwingUtilities.invokeLater(() -> {
            JScrollBar bar = scroll.getVerticalScrollBar();
            bar.setValue(bar.getMaximum());
        });
    }

    // =========================
    // PARSE HELPERS
    // =========================
    private static class MsgLine {
        long messageId;
        String sender;
        String type;    // TEXT / IMAGE / FILE
        String content; // text or filename
        String rawTime;
        LocalDate date;
        String hhmm;
        long sortKey;
        boolean mine;

        MsgLine(long messageId, String sender, String type, String content, String rawTime) {
            this.messageId = messageId;
            this.sender = sender;
            this.type = type;
            this.content = content;
            this.rawTime = rawTime;
        }
    }

    private java.util.List<MsgLine> parseHistory(String raw) {
        java.util.List<MsgLine> out = new ArrayList<>();
        if (raw == null || raw.isBlank()) return out;
        String[] lines = raw.split("\\R");
        long fallback = 0;
        for (String line : lines) {
            MsgLine m = parseAnyLine(line);
            if (m == null) continue;
            if (m.sortKey == Long.MIN_VALUE) m.sortKey = fallback++;
            out.add(m);
        }
        return out;
    }

    private MsgLine parseAnyLine(String line) {
        if (line == null) return null;
        line = line.trim();
        if (line.isEmpty()) return null;

        // internal preview: INTERNAL_PREVIEW|sender|TEXT|content|time
        if (line.startsWith("INTERNAL_PREVIEW|")) {
            String[] p = line.split("\\|", 5);
            if (p.length < 5) return null;
            MsgLine m = new MsgLine(System.currentTimeMillis(), p[1], p[2], p[3], p[4]);
            fillTime(m);
            m.mine = true;
            return m;
        }

        // server history line: MSG|mid|sender|TYPE|content|time
        if (line.startsWith("MSG|")) {
            String[] p = line.split("\\|", 6);
            if (p.length < 6) return null;
            long mid = safeLong(p[1]);
            String sender = p[2];
            String type = p[3];
            String content = p[4];
            String time = p[5];
            MsgLine m = new MsgLine(mid, sender, type, content, time);
            fillTime(m);
            m.mine = sender != null && sender.equalsIgnoreCase(ClientContext.username);
            return m;
        }

        // fallback: "sender: text (timestamp)" (cũ)
        String sender = "unknown";
        String text = line;
        String time = "";

        int li = line.lastIndexOf('(');
        int ri = line.lastIndexOf(')');
        if (li >= 0 && ri > li) {
            time = line.substring(li + 1, ri).trim();
            line = line.substring(0, li).trim();
        }
        int colon = line.indexOf(':');
        if (colon > 0) {
            sender = line.substring(0, colon).trim();
            text = line.substring(colon + 1).trim();
        }
        MsgLine m = new MsgLine(0, sender, "TEXT", text, time);
        fillTime(m);
        m.mine = sender.equalsIgnoreCase(ClientContext.username) || sender.equalsIgnoreCase("Me");
        return m;
    }

    private void fillTime(MsgLine m) {
        m.sortKey = Long.MIN_VALUE;
        LocalDateTime ldt = tryParseDateTime(m.rawTime);
        if (ldt != null) {
            m.date = ldt.toLocalDate();
            m.hhmm = ldt.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"));
            m.sortKey = ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        } else {
            m.hhmm = null;
        }
    }

    private static long safeLong(String s) {
        try { return Long.parseLong(s.trim()); } catch (Exception e) { return 0; }
    }

    private LocalDateTime tryParseDateTime(String ts) {
        if (ts == null) return null;
        String s = ts.trim().replace('T', ' ');
        DateTimeFormatter[] fmts = new DateTimeFormatter[] {
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSSS"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSS"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        };
        for (DateTimeFormatter f : fmts) {
            try { return LocalDateTime.parse(s, f); }
            catch (DateTimeParseException ignored) {}
        }
        return null;
    }

    private String formatVietnameseDay(LocalDate date) {
        DayOfWeek dow = date.getDayOfWeek();
        int thu;
        switch (dow) {
            case MONDAY -> thu = 2;
            case TUESDAY -> thu = 3;
            case WEDNESDAY -> thu = 4;
            case THURSDAY -> thu = 5;
            case FRIDAY -> thu = 6;
            case SATURDAY -> thu = 7;
            default -> thu = 0;
        }
        String ddmmyyyy = date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        return (thu == 0 ? "CN" : "T" + thu) + " " + ddmmyyyy;
    }

    private String nowServerLike() {
        // match server history format-ish
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private String guessMime(String fileName, boolean imageOnly) {
        String lower = (fileName == null ? "" : fileName.toLowerCase());
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".mp4")) return "video/mp4";
        if (imageOnly) return "image/*";
        return "application/octet-stream";
    }

    private void requestAttachment(long messageId) {
        if (messageId <= 0) return;
        try {
            synchronized (ClientContext.out) {
                ClientContext.out.writeUTF("ATTACH_GET|" + messageId);
                ClientContext.out.flush();
            }
        } catch (Exception ignored) {
        }
    }

    private static Image scaleImage(ImageIcon icon, int maxW, int maxH) {
        int w = Math.max(1, icon.getIconWidth());
        int h = Math.max(1, icon.getIconHeight());
        float scale = Math.min((float) maxW / w, (float) maxH / h);
        int tw = Math.max(1, (int) (w * scale));
        int th = Math.max(1, (int) (h * scale));
        return icon.getImage().getScaledInstance(tw, th, Image.SCALE_SMOOTH);
    }

    // =========================
    // UI COMPONENTS
    // =========================
    private static class DateSeparatorMarker extends JPanel {
        final LocalDate date;
        DateSeparatorMarker(LocalDate d) { this.date = d; }
    }

    private static class AvatarCircle extends JPanel {
        private final String letter;
        AvatarCircle(String name) {
            setOpaque(false);
            setPreferredSize(new Dimension(34, 34));
            String n = (name == null || name.isBlank()) ? "?" : name.trim();
            letter = n.substring(0, 1).toUpperCase();
            setToolTipText(name);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(new Color(210, 210, 210));
            g2.fillOval(0, 0, getWidth(), getHeight());
            g2.setColor(Color.DARK_GRAY);
            g2.setFont(getFont().deriveFont(Font.BOLD, 14f));
            FontMetrics fm = g2.getFontMetrics();
            int x = (getWidth() - fm.stringWidth(letter)) / 2;
            int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
            g2.drawString(letter, x, y);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    private static class Bubble extends JPanel {
        Bubble(String sender, String text, String time, boolean me) {
            setLayout(new BorderLayout());
            setOpaque(false);

            JPanel bubble = new RoundedPanel(me ? new Color(200, 230, 255) : Color.WHITE);
            bubble.setLayout(new BorderLayout());
            bubble.setBorder(new EmptyBorder(8, 10, 8, 10));

            if (!me) {
                JLabel lbSender = new JLabel(sender);
                lbSender.setFont(lbSender.getFont().deriveFont(Font.BOLD, 12f));
                lbSender.setBorder(new EmptyBorder(0, 0, 4, 0));
                bubble.add(lbSender, BorderLayout.NORTH);
            }

            JLabel lbText = new JLabel("<html><div style='width:280px;'>" + escapeHtml(text) + "</div></html>");
            lbText.setFont(lbText.getFont().deriveFont(13f));
            bubble.add(lbText, BorderLayout.CENTER);

            if (time != null) {
                JLabel lbTime = new JLabel(time);
                lbTime.setFont(lbTime.getFont().deriveFont(11f));
                lbTime.setForeground(Color.GRAY);
                JPanel pTime = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
                pTime.setOpaque(false);
                pTime.add(lbTime);
                bubble.add(pTime, BorderLayout.SOUTH);
            }

            add(bubble, BorderLayout.CENTER);
        }

        private static String escapeHtml(String s) {
            if (s == null) return "";
            return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        }
    }

    private static class RoundedPanel extends JPanel {
        private final Color bg;
        RoundedPanel(Color bg) { this.bg = bg; setOpaque(false); }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int arc = 18;
            g2.setColor(bg);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
            g2.dispose();
            super.paintComponent(g);
        }
    }
}
