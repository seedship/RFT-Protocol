package com.protocoldesigngroup2.xxx.server;

import com.protocoldesigngroup2.xxx.messages.ClientRequest;
import com.protocoldesigngroup2.xxx.messages.Message;
import com.protocoldesigngroup2.xxx.network.Endpoint;
import com.protocoldesigngroup2.xxx.network.MessageHandler;

import java.time.LocalTime;

public class ClientRequestHandler implements MessageHandler {

    private final Server server;

    public ClientRequestHandler(Server server) {
        this.server = server;
    }

    public void handleMessage(Message message, Endpoint endpoint) {
        if (!(message instanceof ClientRequest)) {
            System.out.println("ClientRequestHandler received non Client Reuqest message");
            return;
        }
        ClientRequest req = (ClientRequest) message;
        //TODO sync with network
        ClientState s = new ClientState(req.files, req.maxTransmissionRate, LocalTime.now().toNanoOfDay(),
                req.ackNumber);

        server.clientStateMap.put(endpoint, s);
        server.interrupt();
    }
}
