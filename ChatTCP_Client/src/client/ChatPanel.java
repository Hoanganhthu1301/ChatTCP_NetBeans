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
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.nio.file.Files;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.List;

public class ChatPanel extends JPanel {
    private final MainFrame main;

    // header
    private JLabel lblHeader;
    private JButton btnBack;

    // action buttons (Zalo style)
    private JButton btnBlock;
    private JButton btnSendImage;
    private JButton btnSendFile;

    // blocked banner
    private JPanel blockedBar;
    private JLabel lblBlocked;
    private JButton btnUnblock;
    private boolean blocked = false;

    // input
    private JTextField txtInput;
    private JButton btnSend;

    // messages
    private JPanel messagesPanel;
    private JScrollPane scroll;

    // current peer user (for DM)
    private String currentPeerUser = null;

    public static String currentKey;

    public ChatPanel(MainFrame main) {
        this.main = main;
        initUI();
        wire();
    }

    private void initUI() {
        setLayout(new BorderLayout(10, 10));
        setBorder(new EmptyBorder(8, 8, 8, 8));

        // ===== TOP HEADER =====
        JPanel top = new JPanel(new BorderLayout(10, 10));
        btnBack = new JButton("← Back");

        lblHeader = new JLabel("Chat");
        lblHeader.setFont(lblHeader.getFont().deriveFont(Font.BOLD, 14f));

        // right actions
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

        // ===== BLOCKED BAR =====
        blockedBar = new JPanel(new BorderLayout(10, 0));
        blockedBar.setBorder(new EmptyBorder(8, 10, 8, 10));
        blockedBar.setBackground(new Color(240, 240, 240));

        lblBlocked = new JLabel("Bạn đã chặn tin nhắn.");
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

        // ===== MESSAGES =====
        messagesPanel = new JPanel();
        messagesPanel.setLayout(new BoxLayout(messagesPanel, BoxLayout.Y_AXIS));
        messagesPanel.setBackground(new Color(245, 245, 245));
        messagesPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        scroll = new JScrollPane(messagesPanel);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(new Color(245, 245, 245));
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        // ===== INPUT =====
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

        // block/unblock
        btnBlock.addActionListener(e -> doBlockUI());
        btnUnblock.addActionListener(e -> doUnblockUI());

        // file/image
        btnSendFile.addActionListener(e -> doSendFile(false));
        btnSendImage.addActionListener(e -> doSendFile(true));
    }

    // ====== public API ======
    public void setHeader(String s) {
        lblHeader.setText(s);
    }

    // gọi khi mở chat key (USERS panel gọi)
    public void onOpenChatKey(String key) {
        currentPeerUser = null;
        if (key != null && key.startsWith("USER:")) {
            currentPeerUser = key.substring("USER:".length());
        }
        hideBlockedBanner();
    }

    // ReceiverThread gọi khi nhận ERR|BLOCKED|...
    public void showBlockedBanner(String serverReason) {
        if (ClientContext.currentChatKey == null || !ClientContext.currentChatKey.startsWith("USER:")) return;

        String reason = (serverReason == null) ? "" : serverReason.trim().toLowerCase();
        String text;
        boolean canUnblock = false;

        if (reason.contains("you blocked")) {
            text = "Bạn đã chặn tin nhắn và cuộc gọi";
            canUnblock = true;
        } else if (reason.contains("blocked by this user")) {
            text = "Bạn không thể nhắn tin vì người này đã chặn bạn";
            canUnblock = false;
        } else {
            text = (serverReason == null || serverReason.isBlank())
                    ? "Bạn không thể nhắn tin."
                    : serverReason;
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

    // ====== attach incoming: hiện bubble + nút Lưu ======
    public void appendIncomingAttachment(String convId, String from, long pendingId,
                                         String fileName, String mime, int size) {

        // ✅ FIX: xác định key đúng để hiển thị bên nhận (không phụ thuộc đang mở chat nào)
        // DM: file tới từ ai -> gắn vào USER:from
        String keyToStore;
        if (ClientContext.currentChatKey != null && ClientContext.currentChatKey.startsWith("USER:")) {
            keyToStore = "USER:" + from;
        } else {
            // fallback
            keyToStore = ClientContext.currentChatKey;
        }
        if (keyToStore == null) return;

        // lưu history
        ClientContext.getHistory(keyToStore)
                .append(from).append(" [").append(mime).append("]: ").append(fileName)
                .append(" (").append(size).append(" bytes)\n");

        // nếu đang mở đúng chat thì add UI ngay
        if (ClientContext.currentChatKey == null || !ClientContext.currentChatKey.equals(keyToStore)) return;

        messagesPanel.add(attachmentRow(from, pendingId, fileName, mime, size));
        messagesPanel.add(Box.createVerticalStrut(6));
        messagesPanel.revalidate();
        messagesPanel.repaint();
        scrollToBottom();
    }

    // ====== HISTORY ======
    public void loadHistory(String key) {
        onOpenChatKey(key);
        messagesPanel.removeAll();

        String raw = ClientContext.getHistory(key).toString();
        List<ParsedLine> lines = parseLines(raw);
        lines.sort(Comparator.comparingLong(pl -> pl.sortKey));

        LocalDate lastDate = null;
        for (ParsedLine pl : lines) {
            if (pl.date != null && (lastDate == null || !pl.date.equals(lastDate))) {
                messagesPanel.add(dateSeparator(pl.date));
                messagesPanel.add(Box.createVerticalStrut(6));
                lastDate = pl.date;
            }
            messagesPanel.add(messageRow(pl));
            messagesPanel.add(Box.createVerticalStrut(6));
        }

        messagesPanel.revalidate();
        messagesPanel.repaint();
        scrollToBottom();
    }

    // ===== SEND TEXT =====
    private void sendText() {
        if (blocked) { Toolkit.getDefaultToolkit().beep(); return; }

        String text = txtInput.getText().trim();
        if (text.isEmpty()) return;

        // slash commands
        if (text.startsWith("/block ")) {
            sendRaw("BLOCK|" + text.substring(7).trim());
            txtInput.setText("");
            return;
        }
        if (text.startsWith("/unblock ")) {
            sendRaw("UNBLOCK|" + text.substring(9).trim());
            txtInput.setText("");
            return;
        }

        String key = ClientContext.currentChatKey;
        if (key == null) return;

        try {
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
            txtInput.setText("");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Gửi thất bại (mất kết nối?)");
        }
    }

    // ===== UI BLOCK BUTTON =====
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
            ClientContext.out.writeUTF(raw);
            ClientContext.out.flush();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Không gửi được lệnh (mất kết nối?)");
        }
    }

    // ===== SEND FILE/IMAGE =====
    private void doSendFile(boolean imageOnly) {
        if (blocked) { Toolkit.getDefaultToolkit().beep(); return; }

        String key = ClientContext.currentChatKey;
        if (key == null) return;

        JFileChooser fc = new JFileChooser();
        if (imageOnly) {
            fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                    "Images", "png", "jpg", "jpeg", "gif", "webp"
            ));
        }
        int ok = fc.showOpenDialog(this);
        if (ok != JFileChooser.APPROVE_OPTION) return;

        File f = fc.getSelectedFile();

        try {
            String fileName = f.getName();
            byte[] data = Files.readAllBytes(f.toPath());
            String mime = guessMime(fileName, imageOnly);

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

            ClientContext.out.writeUTF(header);
            ClientContext.out.flush();
            ClientContext.out.write(data);
            ClientContext.out.flush();

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Gửi file thất bại: " + ex.getMessage());
        }
    }

    // ReceiverThread dùng hàm này để append tin nhắn vào đúng cuộc chat
    public void appendLine(String key, String line) {
        if (key == null) return;

        ClientContext.getHistory(key).append(line).append("\n");

        if (ClientContext.currentChatKey == null || !ClientContext.currentChatKey.equals(key)) return;

        addMessageToUI(line);
    }

    private void addMessageToUI(String line) {
        System.out.println(line);
    }

    private String guessMime(String fileName, boolean imageOnly) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";

        // video (để preview/play)
        if (lower.endsWith(".mp4")) return "video/mp4";
        if (lower.endsWith(".mov")) return "video/quicktime";
        if (lower.endsWith(".mkv")) return "video/x-matroska";

        if (imageOnly) return "image/*";
        return "application/octet-stream";
    }

    // ===== UI building: attachment bubble (PREVIEW + SAVE) =====
    private JPanel attachmentRow(String from, long pendingId, String fileName, String mime, int size) {
        boolean isMe = "Me".equalsIgnoreCase(from) || from.equalsIgnoreCase(ClientContext.username);

        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);

        JPanel box = new JPanel(new BorderLayout(10, 6));
        box.setOpaque(true);
        box.setBackground(isMe ? new Color(200, 230, 255) : Color.WHITE);
        box.setBorder(new EmptyBorder(10, 12, 10, 12));

        JLabel title = new JLabel((mime.startsWith("image/") ? "🖼 " : (mime.startsWith("video/") ? "🎬 " : "📎 ")) + fileName);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 12f));

        JLabel sub = new JLabel(mime + " • " + size + " bytes");
        sub.setForeground(Color.GRAY);
        sub.setFont(sub.getFont().deriveFont(11f));

        JPanel head = new JPanel(new GridLayout(2, 1));
        head.setOpaque(false);
        head.add(title);
        head.add(sub);
        box.add(head, BorderLayout.NORTH);

        // PREVIEW (phải có bytes trong PendingStore)
        if (pendingId > 0) {
            PendingFile pf = PendingStore.get(pendingId);
            if (pf != null && pf.data != null && pf.data.length > 0) {

                // IMAGE preview
                if (mime.startsWith("image/")) {
                    JLabel img = new JLabel();
                    img.setBorder(new EmptyBorder(6, 0, 6, 0));
                    try {
                        ImageIcon icon = new ImageIcon(pf.data);
                        int targetW = 240;
                        int w = Math.max(1, icon.getIconWidth());
                        int h = Math.max(1, icon.getIconHeight());
                        int targetH = targetW * h / w;

                        Image scaled = icon.getImage().getScaledInstance(targetW, targetH, Image.SCALE_SMOOTH);
                        img.setIcon(new ImageIcon(scaled));
                    } catch (Exception e) {
                        img.setText("(Không render được ảnh)");
                    }
                    box.add(img, BorderLayout.CENTER);
                }

                // VIDEO preview: nút Play mở bằng app hệ điều hành (ổn định nhất)
                else if (mime.startsWith("video/")) {
                    JPanel pv = new JPanel(new BorderLayout(8, 8));
                    pv.setOpaque(false);
                    pv.setBorder(new EmptyBorder(10, 0, 10, 0));

                    JLabel thumb = new JLabel("▶ Video");
                    thumb.setFont(thumb.getFont().deriveFont(Font.BOLD, 13f));

                    JButton btnPlay = new JButton("Play");
                    btnPlay.addActionListener(e -> {
                        try {
                            File tmp = File.createTempFile("chat_video_", "_" + fileName);
                            tmp.deleteOnExit();
                            Files.write(tmp.toPath(), pf.data);
                            Desktop.getDesktop().open(tmp);
                        } catch (Exception ex) {
                            JOptionPane.showMessageDialog(this, "Không mở được video: " + ex.getMessage());
                        }
                    });

                    pv.add(thumb, BorderLayout.CENTER);
                    pv.add(btnPlay, BorderLayout.EAST);
                    box.add(pv, BorderLayout.CENTER);
                }
            }
        }

        // SAVE button (kế bên)
        if (pendingId > 0) {
            JButton btnSave = new JButton("Lưu");
            btnSave.addActionListener(e -> {
                PendingFile pf = PendingStore.get(pendingId);
                if (pf == null) {
                    JOptionPane.showMessageDialog(this, "Không tìm thấy file pending!");
                    return;
                }
                FileSaveUtil.saveWithChooser(this, pf.fileName, pf.data);
            });
            box.add(btnSave, BorderLayout.EAST);
        }

        JPanel wrap = new JPanel(new FlowLayout(isMe ? FlowLayout.RIGHT : FlowLayout.LEFT, 0, 0));
        wrap.setOpaque(false);
        wrap.add(box);

        row.add(wrap, isMe ? BorderLayout.EAST : BorderLayout.WEST);
        return row;
    }

    // ===== message bubble text =====
    private JPanel messageRow(ParsedLine pl) {
        boolean isMe = pl.isMe();

        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);

        if (!isMe) {
            JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            left.setOpaque(false);
            left.add(new AvatarCircle(pl.sender));
            left.add(new Bubble(pl.sender, pl.text, pl.time, false));
            row.add(left, BorderLayout.WEST);
        } else {
            JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
            right.setOpaque(false);
            right.add(new Bubble("Me", pl.text, pl.time, true));
            row.add(right, BorderLayout.EAST);
        }

        return row;
    }

    private void scrollToBottom() {
        SwingUtilities.invokeLater(() -> {
            JScrollBar bar = scroll.getVerticalScrollBar();
            bar.setValue(bar.getMaximum());
        });
    }

    // ===== parsing =====
    private static class ParsedLine {
        String sender;
        String text;
        String time;
        LocalDate date;
        long sortKey;

        boolean isMe() {
            return sender.equalsIgnoreCase("Me") || sender.equalsIgnoreCase(ClientContext.username);
        }

        ParsedLine(String sender, String text, String time, LocalDate date, long sortKey) {
            this.sender = sender;
            this.text = text;
            this.time = time;
            this.date = date;
            this.sortKey = sortKey;
        }
    }

    private List<ParsedLine> parseLines(String raw) {
        List<ParsedLine> out = new ArrayList<>();
        if (raw == null || raw.isBlank()) return out;

        String[] lines = raw.split("\\R");
        long fallback = 0;

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;
            ParsedLine pl = parseSingleLine(line);
            if (pl.sortKey == Long.MIN_VALUE) pl.sortKey = fallback++;
            out.add(pl);
        }
        return out;
    }

    private ParsedLine parseSingleLine(String line) {
        String sender = "unknown";
        String text = line;
        String time = null;
        LocalDate date = null;
        long sortKey = Long.MIN_VALUE;

        int li = line.lastIndexOf('(');
        int ri = line.lastIndexOf(')');
        if (li >= 0 && ri > li) {
            String ts = line.substring(li + 1, ri).trim();
            line = (line.substring(0, li)).trim();

            LocalDateTime ldt = tryParseDateTime(ts);
            if (ldt != null) {
                date = ldt.toLocalDate();
                time = ldt.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"));
                sortKey = ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            }
        }

        int colon = line.indexOf(':');
        if (colon > 0) {
            String left = line.substring(0, colon).trim();
            text = line.substring(colon + 1).trim();

            int br = left.indexOf(" [");
            sender = (br > 0) ? left.substring(0, br).trim() : left;
        } else {
            text = line;
        }

        if (sender.equalsIgnoreCase(ClientContext.username)) sender = "Me";
        return new ParsedLine(sender, text, time, date, sortKey);
    }

    private LocalDateTime tryParseDateTime(String ts) {
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

    private JPanel dateSeparator(LocalDate date) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        p.setOpaque(false);
        JLabel lb = new JLabel(formatVietnameseDay(date));
        lb.setOpaque(true);
        lb.setForeground(Color.DARK_GRAY);
        lb.setBackground(new Color(220, 220, 220));
        lb.setBorder(new EmptyBorder(4, 10, 4, 10));
        p.add(lb);
        return p;
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

    // ===== UI components =====
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
