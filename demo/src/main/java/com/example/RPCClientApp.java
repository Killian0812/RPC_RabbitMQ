package com.example;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeoutException;

public class RPCClientApp extends JFrame {
    private Connection connection;
    private Channel channel;
    private String requestQueueName = "rpc_queue";

    private String replyQueueName;
    private HashSet<String> corrIdSet;
    private String newCorrId;

    private JTextArea outputTextArea;
    private JTextField inputTextField;

    public RPCClientApp() throws IOException, TimeoutException {
        super("RPC Client");

        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost"); // RabbitMQ message broker running on localhost:5672
        connection = factory.newConnection();
        channel = connection.createChannel(); // Set up connection

        // Generate new queue
        replyQueueName = UUID.randomUUID().toString().substring(0, 6);
        channel.queueDeclare(replyQueueName, false, false, false, null);

        // Store request corrIds
        corrIdSet = new HashSet<>();

        initComponents();
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    public String getReplyQueueName() {
        return this.replyQueueName;
    }

    public String newCorrId() {
        String corrId = UUID.randomUUID().toString().substring(0, 6);
        corrIdSet.add(corrId);
        newCorrId = corrId;
        return corrId;
    }

    public String call(String message) throws IOException,
            InterruptedException {

        AMQP.BasicProperties props = new AMQP.BasicProperties.Builder()
                .correlationId(newCorrId)
                .replyTo(replyQueueName)
                .build();

        channel.basicPublish("", requestQueueName, props,
                message.getBytes("UTF-8"));
        final BlockingQueue<String> response = new ArrayBlockingQueue<>(1); // Process response one by one
        String ctag = channel.basicConsume(replyQueueName, true,
                (consumerTag, delivery) -> {
                    // Check if current client should recieve this response
                    if (corrIdSet.contains(delivery.getProperties().getCorrelationId())) {
                        corrIdSet.remove(delivery.getProperties().getCorrelationId());
                        response.offer(new String(delivery.getBody(), "UTF-8")); // Add to BlockingQueue
                    }
                }, consumerTag -> {
                });
        String result = response.take();
        channel.basicCancel(ctag);
        return result;
    }

    private void initComponents() {
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());

        outputTextArea = new JTextArea(10, 40);
        outputTextArea.setEditable(false);
        outputTextArea.setFont(new Font("Arial", Font.PLAIN, 18));
        JScrollPane scrollPane = new JScrollPane(outputTextArea);
        panel.add(scrollPane, BorderLayout.CENTER);

        // Name of queue
        outputTextArea.append("[OK] Your queue: " + getReplyQueueName() + "\n\n");

        inputTextField = new JTextField(20);
        inputTextField.setFont(new Font("Arial", Font.PLAIN, 18));
        JButton sendButton = new JButton("Send");
        sendButton.setFont(new Font("Arial", Font.BOLD, 18));
        sendButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String input = inputTextField.getText().trim();
                if (!input.isEmpty()) {
                    String currentCorrId = newCorrId(); // Create new corrId
                    outputTextArea.append("[.] Requesting RPC with id: " + currentCorrId + "\n");

                    // Use SwingWorker for asynchronous RPC call
                    SwingWorker<String, Void> worker = new SwingWorker<String, Void>() {
                        @Override
                        protected String doInBackground() throws Exception {
                            return call(input);
                        }

                        @Override
                        protected void done() {
                            try {
                                String response = get();
                                // Display response to corresponding corrId
                                outputTextArea.append("[OK] Response to " + currentCorrId + ": " + response + "\n");
                            } catch (Exception ex) {
                                ex.printStackTrace();
                                outputTextArea.append("[ERROR] " + ex.getMessage() + "\n");
                            }
                        }
                    };

                    worker.execute();
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
        try {
            new RPCClientApp();
        } catch (IOException | TimeoutException e) {
            e.printStackTrace();
        }
    }
}
