package com.example;

import com.rabbitmq.client.*;

import javax.swing.*;
import java.awt.*;

public class TestServer {
    private static final String RPC_QUEUE_NAME = "rpc_queue";
    private static JTextArea messageArea;

    private static int fib(int n) {
        if (n <= 1) {
            return n;
        } else {
            return fib(n - 1) + fib(n - 2);
        }
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("RPC Server");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(400, 200);

        JPanel panel = new JPanel(new BorderLayout());
        messageArea = new JTextArea(" [x] Awaiting RPC requests...\n");
        messageArea.setEditable(false); // Make the text area read-only
        messageArea.setFont(new Font("Arial", Font.PLAIN, 18)); // Set the font size here
        JScrollPane scrollPane = new JScrollPane(messageArea);
        panel.add(scrollPane, BorderLayout.CENTER);

        frame.getContentPane().add(panel);

        frame.setVisible(true);

        new Thread(() -> {
            try {
                ConnectionFactory factory = new ConnectionFactory();
                factory.setHost("localhost");
                
                try (Connection connection = factory.newConnection();
                        Channel channel = connection.createChannel()) {
                    channel.queueDeclare(RPC_QUEUE_NAME, false, false, false, null);
                    channel.queuePurge(RPC_QUEUE_NAME);
                    channel.basicQos(1);
                    // System.out.println(" [x] Awaiting RPC requests");

                    DeliverCallback deliverCallback = (consumerTag, delivery) -> {
                        String response = "";
                        try {
                            String message = new String(delivery.getBody(), "UTF-8");
                            int n = Integer.parseInt(message);
                            // System.out.println(" [.] fib(" + message + ")");
                            messageArea.append(" [.] Calculating fib(" + message + ")\n");
                            response += fib(n);
                            try {
                                Thread.sleep(2000);
                            } catch (InterruptedException _ignored) {
                                Thread.currentThread().interrupt();
                            }
                        } catch (RuntimeException e) {
                            System.out.println(" [.] " + e.toString());
                        } finally {
                            // final String finalResponse = response;
                            channel.basicPublish("", delivery.getProperties().getReplyTo(),
                                    new AMQP.BasicProperties.Builder()
                                            .correlationId(delivery.getProperties().getCorrelationId())
                                            .build(),
                                    response.getBytes("UTF-8"));
                            channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);

                            // Update UI with the response
                            // SwingUtilities.invokeLater(
                            // () -> messageArea.append("\nLast request processed: " + finalResponse));
                        }
                    };
                    channel.basicConsume(RPC_QUEUE_NAME, false, deliverCallback, consumerTag -> {
                    });

                    // Wait for requests
                    Thread.sleep(Long.MAX_VALUE);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
}
