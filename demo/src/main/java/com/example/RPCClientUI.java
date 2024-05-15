package com.example;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.concurrent.TimeoutException;

public class RPCClientUI extends JFrame {
    private RPCClient rpcClient;

    private JTextArea outputTextArea;
    private JTextField inputTextField;

    public RPCClientUI() {
        super("RPC Client");

        try {
            rpcClient = new RPCClient();
        } catch (IOException | TimeoutException e) {
            e.printStackTrace();
        }

        initComponents();
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    private void initComponents() {
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());

        outputTextArea = new JTextArea(10, 40);
        outputTextArea.setEditable(false);
        outputTextArea.setFont(new Font("Arial", Font.PLAIN, 18));
        JScrollPane scrollPane = new JScrollPane(outputTextArea);
        panel.add(scrollPane, BorderLayout.CENTER);

        inputTextField = new JTextField(20);
        inputTextField.setFont(new Font("Arial", Font.PLAIN, 18));
        JButton sendButton = new JButton("Send");
        sendButton.setFont(new Font("Arial", Font.BOLD, 18));
        sendButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String input = inputTextField.getText().trim();
                if (!input.isEmpty()) {
                    try {
                        String response = rpcClient.call(input);
                        outputTextArea.append(" [.] Got '" + response + "'\n");
                    } catch (IOException | InterruptedException ex) {
                        ex.printStackTrace();
                    }
                }
            }
        });
        JPanel inputPanel = new JPanel();
        inputPanel.add(inputTextField);
        inputPanel.add(sendButton);
        panel.add(inputPanel, BorderLayout.SOUTH);

        add(panel);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                new RPCClientUI();
            }
        });
    }
}