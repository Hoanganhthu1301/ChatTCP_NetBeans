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
import java.io.File;

public class StickerPickerDialog extends JDialog {
    public interface StickerSelectListener {
        void onSelect(String stickerId, File file);
    }

    public StickerPickerDialog(JFrame owner, StickerSelectListener listener) {
        super(owner, "Sticker", true);
        setSize(420, 520);
        setLocationRelativeTo(owner);

        JTabbedPane tabs = new JTabbedPane();
        add(tabs);

        // ===== STICKER TAB =====
        JPanel stickerTab = new JPanel(new BorderLayout(8, 8));
        JTextField txtSearch = new JTextField();
        txtSearch.setToolTipText("Tìm sticker...");
        stickerTab.add(txtSearch, BorderLayout.NORTH);

        JPanel grid = new JPanel(new GridLayout(0, 4, 8, 8));
        grid.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
        JScrollPane sp = new JScrollPane(grid);
        stickerTab.add(sp, BorderLayout.CENTER);

        tabs.addTab("STICKER", stickerTab);
        tabs.addTab("EMOJI", new JPanel()); // làm sau
        tabs.addTab("GIF", new JPanel());   // để trống

        // Load sticker pack1
        File folder = new File("src/resources/stickers/pack1");
        if (!folder.exists()) {
            grid.add(new JLabel("Không tìm thấy folder: " + folder.getPath()));
        } else {
            File[] files = folder.listFiles();
            if (files != null) {
                for (File f : files) {
                    String name = f.getName().toLowerCase();
                    if (!name.matches(".*\\.(png|jpg|jpeg|gif|webp)$")) continue;

                    ImageIcon icon = new ImageIcon(f.getPath());
                    Image img = icon.getImage().getScaledInstance(90, 90, Image.SCALE_SMOOTH);

                    JButton btn = new JButton(new ImageIcon(img));
                    btn.setBorderPainted(false);
                    btn.setContentAreaFilled(false);
                    btn.setFocusPainted(false);

                    String stickerId = "pack1:" + f.getName();

                    btn.addActionListener(e -> {
                        listener.onSelect(stickerId, f);
                        dispose();
                    });

                    grid.add(btn);
                }
            }
        }
    }
}

