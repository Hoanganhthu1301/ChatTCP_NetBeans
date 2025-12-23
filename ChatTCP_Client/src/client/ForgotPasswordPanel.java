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
import java.io.DataInputStream;
import java.io.DataOutputStream;

public class ForgotPasswordPanel extends JPanel {

    private final MainFrame main;

    private JTextField txtEmail;
    private JButton btnSendOtp;

    private JTextField txtOtp;
    private JPasswordField txtNewPass;
    private JPasswordField txtConfirm;
    private JButton btnReset;

    private JButton btnBack;
    private JLabel lblStatus;

    public ForgotPasswordPanel(MainFrame main) {
        this.main = main;
        initUI();
    }

    private void initUI() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));

        JPanel top = new JPanel(new BorderLayout());
        btnBack = new JButton("← Back");
        top.add(btnBack, BorderLayout.WEST);

        JLabel title = new JLabel("Quên mật khẩu");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        top.add(title, BorderLayout.CENTER);

        add(top, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(8, 8, 8, 8);
        gc.fill = GridBagConstraints.HORIZONTAL;

        txtEmail = new JTextField(20);
        btnSendOtp = new JButton("Gửi OTP");

        txtOtp = new JTextField(10);
        txtNewPass = new JPasswordField(20);
        txtConfirm = new JPasswordField(20);
        btnReset = new JButton("Đổi mật khẩu");

        int r = 0;

        gc.gridx = 0; gc.gridy = r; gc.weightx = 0;
        form.add(new JLabel("Email:"), gc);
        gc.gridx = 1; gc.gridy = r; gc.weightx = 1;
        form.add(txtEmail, gc);
        gc.gridx = 2; gc.gridy = r; gc.weightx = 0;
        form.add(btnSendOtp, gc);

        r++;
        gc.gridx = 0; gc.gridy = r; gc.weightx = 0;
        form.add(new JLabel("OTP:"), gc);
        gc.gridx = 1; gc.gridy = r; gc.weightx = 1;
        form.add(txtOtp, gc);

        r++;
        gc.gridx = 0; gc.gridy = r; gc.weightx = 0;
        form.add(new JLabel("Mật khẩu mới:"), gc);
        gc.gridx = 1; gc.gridy = r; gc.weightx = 1;
        form.add(txtNewPass, gc);

        r++;
        gc.gridx = 0; gc.gridy = r; gc.weightx = 0;
        form.add(new JLabel("Nhập lại:"), gc);
        gc.gridx = 1; gc.gridy = r; gc.weightx = 1;
        form.add(txtConfirm, gc);

        r++;
        gc.gridx = 1; gc.gridy = r; gc.weightx = 1;
        gc.anchor = GridBagConstraints.EAST;
        form.add(btnReset, gc);

        add(form, BorderLayout.CENTER);

        lblStatus = new JLabel(" ");
        lblStatus.setForeground(new Color(90, 90, 90));
        add(lblStatus, BorderLayout.SOUTH);

        setStep2Enabled(false);

        btnBack.addActionListener(e -> main.showLogin());
        btnSendOtp.addActionListener(e -> sendOtp());
        btnReset.addActionListener(e -> resetPassword());
    }

    private void setStep2Enabled(boolean enabled) {
        txtOtp.setEnabled(enabled);
        txtNewPass.setEnabled(enabled);
        txtConfirm.setEnabled(enabled);
        btnReset.setEnabled(enabled);
    }

    private void setStatus(String text, boolean error) {
        lblStatus.setText(text);
        lblStatus.setForeground(error ? new Color(180, 0, 0) : new Color(0, 130, 0));
    }

    private boolean ensureConnection() {
        try {
            if (ClientContext.socket == null || ClientContext.socket.isClosed()) {
                ClientContext.socket = new java.net.Socket("127.0.0.1", 5000);
                ClientContext.in = new DataInputStream(ClientContext.socket.getInputStream());
                ClientContext.out = new DataOutputStream(ClientContext.socket.getOutputStream());
            }
            return true;
        } catch (Exception ex) {
            ex.printStackTrace();
            setStatus("Không kết nối được server: " + ex.getMessage(), true);
            return false;
        }
    }

    private void sendOtp() {
        if (!ensureConnection()) return;

        String email = txtEmail.getText().trim();
        if (email.isEmpty()) {
            setStatus("Nhập email trước.", true);
            return;
        }

        btnSendOtp.setEnabled(false);
        setStatus("Đang gửi OTP...", false);

        new Thread(() -> {
            try {
                synchronized (ClientContext.out) {
                    ClientContext.out.writeUTF("FORGOT_REQ|" + email);
                    ClientContext.out.flush();
                }

                String resp;
                synchronized (ClientContext.in) {
                    resp = ClientContext.in.readUTF();
                }

                SwingUtilities.invokeLater(() -> {
                    btnSendOtp.setEnabled(true);

                    if (resp.startsWith("OK|FORGOT_REQ")) {
                        String[] p = resp.split("\\|");
                        if (p.length >= 4 && "OTP".equalsIgnoreCase(p[2])) {
                            txtOtp.setText(p[3]);
                            setStatus("Không gửi được mail, dùng OTP fallback.", false);
                        } else {
                            setStatus("Đã gửi OTP. Kiểm tra email (Spam).", false);
                        }
                        setStep2Enabled(true);
                    } else {
                        setStatus(resp, true);
                    }
                });

            } catch (Exception ex) {
                ex.printStackTrace();
                SwingUtilities.invokeLater(() -> {
                    btnSendOtp.setEnabled(true);
                    setStatus("Lỗi gửi OTP: " + ex.getMessage(), true);
                });
            }
        }).start();
    }

    private void resetPassword() {
        if (!ensureConnection()) return;

        String user = txtEmail.getText().trim();
        String otp = txtOtp.getText().trim();
        String newPass = new String(txtNewPass.getPassword());
        String confirm = new String(txtConfirm.getPassword());

        if (user.isEmpty() || otp.isEmpty() || newPass.isEmpty() || confirm.isEmpty()) {
            setStatus("Nhập đủ thông tin.", true);
            return;
        }
        if (!newPass.equals(confirm)) {
            setStatus("Mật khẩu nhập lại không khớp.", true);
            return;
        }

        btnReset.setEnabled(false);
        setStatus("Đang đổi mật khẩu...", false);

        new Thread(() -> {
            try {
                String cmd = "FORGOT_CONFIRM|" + user + "|" + otp + "|" + newPass;

                synchronized (ClientContext.out) {
                    ClientContext.out.writeUTF(cmd);
                    ClientContext.out.flush();
                }

                String resp;
                synchronized (ClientContext.in) {
                    resp = ClientContext.in.readUTF();
                }

                SwingUtilities.invokeLater(() -> {
                    btnReset.setEnabled(true);

                    if ("OK|FORGOT_CONFIRM".equals(resp)) {
                        JOptionPane.showMessageDialog(this,
                                "Đổi mật khẩu thành công! Quay lại đăng nhập.",
                                "OK", JOptionPane.INFORMATION_MESSAGE);
                        main.showLogin();
                    } else {
                        setStatus(resp, true);
                    }
                });

            } catch (Exception ex) {
                ex.printStackTrace();
                SwingUtilities.invokeLater(() -> {
                    btnReset.setEnabled(true);
                    setStatus("Lỗi đổi mật khẩu: " + ex.getMessage(), true);
                });
            }
        }).start();
    }
}
