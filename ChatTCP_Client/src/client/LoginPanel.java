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
import java.net.Socket;

public class LoginPanel extends JPanel {

    private final MainFrame main;

    private JTextField txtUser;
    private JPasswordField txtPass;

    private JButton btnLogin;
    private JButton btnRegister;
    private JButton btnForgot;

    private JLabel lblStatus;

    public LoginPanel(MainFrame main) {
        this.main = main;
        initUI();
    }

    private void initUI() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));

        JLabel title = new JLabel("ChatTCP - Login");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        add(title, BorderLayout.NORTH);

        JPanel center = new JPanel(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(10, 10, 10, 10);
        gc.fill = GridBagConstraints.HORIZONTAL;

        txtUser = new JTextField(20);
        txtPass = new JPasswordField(20);

        btnLogin = new JButton("Login");
        btnRegister = new JButton("Register");
        btnForgot = new JButton("Quên mật khẩu");

        lblStatus = new JLabel(" ");
        lblStatus.setForeground(new Color(90, 90, 90));

        int r = 0;

        gc.gridx = 0; gc.gridy = r; gc.weightx = 0;
        center.add(new JLabel("Username:"), gc);

        gc.gridx = 1; gc.gridy = r; gc.weightx = 1;
        center.add(txtUser, gc);

        r++;
        gc.gridx = 0; gc.gridy = r; gc.weightx = 0;
        center.add(new JLabel("Password:"), gc);

        gc.gridx = 1; gc.gridy = r; gc.weightx = 1;
        center.add(txtPass, gc);

        r++;
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        buttons.add(btnLogin);
        buttons.add(btnRegister);
        buttons.add(btnForgot);

        gc.gridx = 1; gc.gridy = r; gc.weightx = 1;
        center.add(buttons, gc);

        add(center, BorderLayout.CENTER);
        add(lblStatus, BorderLayout.SOUTH);

        btnLogin.addActionListener(e -> doLogin());
        btnRegister.addActionListener(e -> doRegister());
        btnForgot.addActionListener(e -> goForgot());
    }

    private void setStatus(String text, boolean error) {
        lblStatus.setText(text);
        lblStatus.setForeground(error ? new Color(180, 0, 0) : new Color(0, 130, 0));
    }

    private boolean ensureConnection() {
        try {
            if (ClientContext.socket == null || ClientContext.socket.isClosed()) {
                ClientContext.socket = new Socket("127.0.0.1", 5000);
                ClientContext.in = new DataInputStream(ClientContext.socket.getInputStream());
                ClientContext.out = new DataOutputStream(ClientContext.socket.getOutputStream());
            }
            return true;
        } catch (Exception ex) {
            ex.printStackTrace();
            setStatus("Không kết nối được server", true);
            return false;
        }
    }

    private void doLogin() {
        if (!ensureConnection()) return;

        String user = txtUser.getText().trim();
        String pass = new String(txtPass.getPassword());

        if (user.isEmpty() || pass.isEmpty()) {
            setStatus("Nhập username + password.", true);
            return;
        }

        btnLogin.setEnabled(false);
        setStatus("Đang đăng nhập...", false);

        // ✅ chỉ ReceiverThread được quyền readUTF
        ClientContext.main = main;

        new Thread(() -> {
            try {
                synchronized (ClientContext.out) {
                    ClientContext.out.writeUTF("LOGIN|" + user + "|" + pass);
                    ClientContext.out.flush();
                }

                // start receiver để nó nhận OK|LOGIN và gọi main.onLoginSuccess()
                ReceiverThread.startIfNeeded(ClientContext.socket);

                SwingUtilities.invokeLater(() -> btnLogin.setEnabled(true));
            } catch (Exception ex) {
                ex.printStackTrace();
                SwingUtilities.invokeLater(() -> {
                    btnLogin.setEnabled(true);
                    setStatus("Gửi LOGIN thất bại", true);
                });
            }
        }).start();
    }

    private void doRegister() {
        if (!ensureConnection()) return;

        // Dialog register có Email (1 email = 1 tài khoản)
        JTextField u = new JTextField(18);
        JTextField email = new JTextField(18);
        JPasswordField p1 = new JPasswordField(18);
        JPasswordField p2 = new JPasswordField(18);
        u.setText(txtUser.getText().trim());

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(6, 6, 6, 6);
        gc.fill = GridBagConstraints.HORIZONTAL;

        int r = 0;
        gc.gridx = 0; gc.gridy = r; gc.weightx = 0; panel.add(new JLabel("Username:"), gc);
        gc.gridx = 1; gc.gridy = r; gc.weightx = 1; panel.add(u, gc);
        r++;
        gc.gridx = 0; gc.gridy = r; gc.weightx = 0; panel.add(new JLabel("Email:"), gc);
        gc.gridx = 1; gc.gridy = r; gc.weightx = 1; panel.add(email, gc);
        r++;
        gc.gridx = 0; gc.gridy = r; gc.weightx = 0; panel.add(new JLabel("Password:"), gc);
        gc.gridx = 1; gc.gridy = r; gc.weightx = 1; panel.add(p1, gc);
        r++;
        gc.gridx = 0; gc.gridy = r; gc.weightx = 0; panel.add(new JLabel("Confirm:"), gc);
        gc.gridx = 1; gc.gridy = r; gc.weightx = 1; panel.add(p2, gc);

        int ok = JOptionPane.showConfirmDialog(this, panel, "Đăng ký", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (ok != JOptionPane.OK_OPTION) return;

        String user = u.getText().trim();
        String mail = email.getText().trim();
        String pass = new String(p1.getPassword());
        String confirm = new String(p2.getPassword());

        if (user.isEmpty() || mail.isEmpty() || pass.isEmpty()) {
            setStatus("Nhập đủ username + email + password.", true);
            return;
        }
        if (!pass.equals(confirm)) {
            setStatus("Password confirm không khớp.", true);
            return;
        }

        btnRegister.setEnabled(false);
        setStatus("Đang tạo tài khoản...", false);

        ClientContext.main = main;
        ReceiverThread.startIfNeeded(ClientContext.socket);

        new Thread(() -> {
            try {
                synchronized (ClientContext.out) {
                    ClientContext.out.writeUTF("REGISTER|" + user + "|" + mail + "|" + pass);
                    ClientContext.out.flush();
                }
                SwingUtilities.invokeLater(() -> {
                    btnRegister.setEnabled(true);
                    setStatus("Đã gửi REGISTER. Nếu OK sẽ hiện thông báo.", false);
                });
            } catch (Exception ex) {
                ex.printStackTrace();
                SwingUtilities.invokeLater(() -> {
                    btnRegister.setEnabled(true);
                    setStatus("Gửi REGISTER thất bại", true);
                });
            }
        }).start();
    }

    private void goForgot() {
        try {
            main.showForgotPassword();
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this,
                    "MainFrame chưa có showForgotPassword().",
                    "Thiếu hàm", JOptionPane.WARNING_MESSAGE);
        }
    }       
}
