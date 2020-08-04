package com.protocoldesigngroup2.xxx.server;

import com.protocoldesigngroup2.xxx.messages.ClientRequest;
import com.protocoldesigngroup2.xxx.messages.Message;
import com.protocoldesigngroup2.xxx.network.Endpoint;
import com.protocoldesigngroup2.xxx.network.MessageHandler;

public class ClientRequestHandler implements MessageHandler {

    private final Server server;

    public ClientRequestHandler(Server server) {
        this.server = server;
    }

    public void handleMessage(Message message, Endpoint endpoint) {
        if (!(message instanceof ClientRequest)) {
            System.out.println("ClientRequestHandler received non Client Request message");
            return;
        }
        ClientRequest req = (ClientRequest) message;
        ClientState s = new ClientState(req.files, req.maxTransmissionRate, System.currentTimeMillis(),
                req.ackNumber);
        server.clientStateMap.put(endpoint, s);
        System.out.println("Added client state from endpoint " + endpoint + ". FileHash: " + s.files.hashCode());
    }
}
