package com.protocoldesigngroup2.xxx.network;

import java.lang.Math;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.protocoldesigngroup2.xxx.messages.*;


public class Network {

    public static final int NUMBER_OF_THREADS = 1;

    private Map<Message.Type, MessageHandler> messageHandlers;
    private DatagramSocket socket;
    private float p, q;
    private boolean lastSent;
    boolean running = true;

    private Network(float p, float q) throws java.net.SocketException {
        this.p = p;
        this.q = q;
        
        messageHandlers = new HashMap<Message.Type, MessageHandler>();
        lastSent = true;
    }
    
    // Creates the listening endpoint with a fixed port
    public static Network createServer(float p, float q, int port) {
        try {
            Network network = new Network(p, q);
            network.socket = new DatagramSocket(port);
            return network;
        } catch (java.net.SocketException ex) {
            System.out.println("Socket Exception in server creation");
            ex.printStackTrace();
            return null;
        }
    }
    
    // Creates the listening endpoint with a random but available port
    public static Network createClient(float p, float q) {
        try {
            Network network = new Network(p, q);
            network.socket = new DatagramSocket();
            return network;
        } catch (java.net.SocketException ex) {
            System.out.println("Socket Exception in client creation");
            ex.printStackTrace();
            return null;
        }
    }

    public void listen() {
        byte[] buffer = new byte[2048];
        ExecutorService executor = Executors.newFixedThreadPool(NUMBER_OF_THREADS);

        try {
            while (running) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                try {
                    handleMessage(buffer, packet.getLength(), new Endpoint(packet.getAddress(), packet.getPort()), executor);
                } catch (RuntimeException e) {
                    System.out.println("Error while handling message: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        } catch (java.io.IOException e) {
            System.out.println("Done: " + e.getMessage());
        }
        executor.shutdownNow();
    }

    public void stopListening() {
        running = false;
        socket.close();
    }

    public void handleMessage(byte[] buffer, int length, final Endpoint endpoint, ExecutorService executor) {
        if (length < 3) {
            // Global header has a min length of 3
            System.out.println("Length too short (min 3)");
            return;
        }
        // Checking the version
        int version = buffer[0] >> 4;
        if (version != Message.VERSION) {
            System.out.println("Wrong version. Expected: " + Message.VERSION + ", got: " + version);
            return;
        }

        // Checking the message type
        Message.Type typ;
        try {
            typ = Message.Type.fromId(buffer[0] & 0x0f);
        } catch (WrongIdException e) {
            e.printStackTrace();
            return;
        }
        if (!messageHandlers.containsKey(typ)) {
            System.out.println("No message handler for type " + typ.id);
            return;
        }

        // Getting ackNumbers and options
        int ackNumber = buffer[1] & 0xff;
        int numberOptions = buffer[2] & 0xff;
        int offset = 3;
        for (int i = 0; i < numberOptions; i++) {
            // We do not know any options and therefore skip them
            if (offset + 1 > length) {
                throw new RuntimeException("Message to short.");
            }
            offset += (buffer[offset + 1] & 0xff) + 2;
        }
        List<Option> options = new ArrayList<Option>();
        
        Message msg = null;
        switch (typ) {
        case CLIENT_REQUEST:
            msg = ClientRequest.decode(buffer, offset, length, ackNumber, options);
            break;
        case SERVER_METADATA:
            msg = ServerMetadata.decode(buffer, offset, length, ackNumber, options);
            break;
        case SERVER_PAYLOAD:
            msg = ServerPayload.decode(buffer, offset, length, ackNumber, options);
            break;
        case CLIENT_ACK:
            msg = ClientAck.decode(buffer, offset, length, ackNumber, options);
            break;
        case CLOSE_CONNECTION:
            msg = CloseConnection.decode(buffer, offset, length, ackNumber, options);
            break;
        default:
            // Should never happen ...
            break;
        }
        if (msg != null) {
            // Already checked before if key exists; parameters for lambda must be
            // effectively final, hence the newMsg
            final Message newMsg = msg;
            executor.submit(() -> messageHandlers.get(typ).handleMessage(newMsg, endpoint));
        }
    }

    public synchronized void sendMessage(Message message, Endpoint endpoint) {
        lastSent = lastSent ? Math.random() > p : Math.random() > q;
        if (!lastSent) {
            // Oopsie, sending failed :(
            return;
        }
        try {
            byte[] buffer = message.encode();
            DatagramPacket packet = new DatagramPacket(
                buffer,
                buffer.length,
                endpoint.getAddress(),
                endpoint.getPort());
            socket.send(packet);
        } catch (java.io.IOException e) {
//            System.out.println("Caught exception :(");
        }
    }

    public void addCallbackMethod(Message.Type messageId, MessageHandler msh) {
        messageHandlers.put(messageId, msh);
    }
}
