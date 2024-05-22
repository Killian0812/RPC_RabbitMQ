package com.example;

import com.rabbitmq.client.*;

import javax.swing.*;
import java.awt.*;

public class RPCServerApp {
    private static final String RPC_QUEUE_NAME = "rpc_queue";
    private static JTextArea messageArea;
    private static final int serverCount = 2;

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
        messageArea = new JTextArea("[OK] Awaiting RPCs...\n");
        messageArea.setEditable(false); // Make the text area read-only
        messageArea.setFont(new Font("Arial", Font.PLAIN, 18)); // Set the font size 
        JScrollPane scrollPane = new JScrollPane(messageArea);
        panel.add(scrollPane, BorderLayout.CENTER);

        frame.getContentPane().add(panel);

        frame.setVisible(true);

        new Thread(() -> {
            try {
                ConnectionFactory factory = new ConnectionFactory();
                factory.setHost("localhost"); // RabbitMQ message broker running on localhost:5672

                // Set up connection
                try (Connection connection = factory.newConnection();
                        Channel channel = connection.createChannel()) { 
                    // Bind "rpc_queue" to channel
                    channel.queueDeclare(RPC_QUEUE_NAME, false, false, false, null); 
                    channel.queuePurge(RPC_QUEUE_NAME);
                    channel.basicQos(serverCount);  // Load balancing, equal to number of servers

                    DeliverCallback deliverCallback = (consumerTag, delivery) -> {
                        String response = "";
                        try {
                            String message = new String(delivery.getBody(), "UTF-8");
                            int n = Integer.parseInt(message);

                            // Display corrId of the request
                            messageArea.append("[.] Recieved an RPC with id: "
                                    + delivery.getProperties().getCorrelationId() + "\n"); 
                            messageArea.append("[.] Calculating fibonacci(" + message + ")\n");
                            response += fib(n); 
                            try {
                                Thread.sleep(2000); // Extra delay for realistic
                            } catch (InterruptedException _ignored) {
                                Thread.currentThread().interrupt();
                            }
                        } catch (RuntimeException e) {
                            System.out.println(" [ERROR] " + e.toString());
                        } finally {
                            channel.basicPublish("", delivery.getProperties().getReplyTo(),
                                    new AMQP.BasicProperties.Builder()
                                            // Bind corrId of the request to its response
                                            .correlationId(delivery.getProperties().getCorrelationId())                           
                                            .build(),                                                   
                                    response.getBytes("UTF-8"));
                            channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);

                            // Display what queue should server response to
                            messageArea.append("[OK] Sent to queue: " + delivery.getProperties().getReplyTo() + "\n\n");
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
