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
import java.io.File;
import java.nio.file.Files;

public class FileSaveUtil {

    public static void saveWithChooser(java.awt.Component parent, String defaultName, byte[] data) {
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File(defaultName));

        int ok = fc.showSaveDialog(parent);
        if (ok != JFileChooser.APPROVE_OPTION) return;

        File f = fc.getSelectedFile();
        try {
            Files.write(f.toPath(), data);
            JOptionPane.showMessageDialog(parent, "Đã lưu: " + f.getAbsolutePath());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(parent, "Lưu thất bại: " + ex.getMessage());
        }
    }
}
