package com.protocoldesigngroup2.xxx;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.Map;


public class Network {
    private Map<Message.Type, MessageHandler> messageHandlers;
    private DatagramSocket socket;
    private boolean running;
    private byte[] buffer = new byte[2048];

    public Network() throws java.net.SocketException {
        socket = new DatagramSocket(2345);
    }

    public void start() {
        running = true;

        try {
            while (running) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                // InetAddress address = packet.getAddress();
                int port = packet.getPort();
                String received = new String(packet.getData(), 0, packet.getLength());
                System.out.println("Got string #" + received + "# on port " + port);
            }
        } catch (java.io.IOException e) {
            System.out.println("Caught exception :(");
        }
    }

    public void sendMessage(Message message, Endpoint endpoint) {
        try {
            byte[] buffer = message.encode();
            DatagramPacket packet = new DatagramPacket(
                buffer,
                buffer.length,
                endpoint.getAddress(),
                endpoint.getPort());
            socket.send(packet);
        } catch (java.io.IOException e) {
            System.out.println("Caught exception :(");
        }
    }

    public void addCallbackMethod(Message.Type messageId, MessageHandler msh) {
    }
}
