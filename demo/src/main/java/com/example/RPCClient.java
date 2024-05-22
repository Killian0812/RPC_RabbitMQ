package com.example;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import java.io.IOException;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeoutException;

public class RPCClient implements AutoCloseable {
    private Connection connection;
    private Channel channel;
    private String requestQueueName = "rpc_queue";
    
    private String replyQueueName;
    private HashSet<String> corrIdSet;
    private String newCorrId;

    public RPCClient() throws IOException, TimeoutException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost"); // RabbitMQ message broker running on localhost:5672
        connection = factory.newConnection();
        channel = connection.createChannel(); // Set up connection

        // Generate new queue
        replyQueueName = UUID.randomUUID().toString().substring(0, 6);
        channel.queueDeclare(replyQueueName, false, false, false, null);

        // Store request corrIds
        corrIdSet = new HashSet<>();
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

    public void close() throws IOException {
        connection.close();
    }
}
