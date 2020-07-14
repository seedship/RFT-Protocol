package com.protocoldesigngroup2.xxx.server;

import com.protocoldesigngroup2.xxx.messages.Message;
import com.protocoldesigngroup2.xxx.network.Endpoint;
import com.protocoldesigngroup2.xxx.network.MessageHandler;
import com.protocoldesigngroup2.xxx.network.Network;

import java.util.Map;

import static java.lang.Math.max;

public class Server implements MessageHandler {
    public Server(Network network) {
        this.network = network;
    }

    @Override
    public void handleMessage(Message message, Endpoint endpoint) {
        switch (message.getMessageType()) {
            case CLIENT_REQUEST:
                //clientStateMap.put(endpoint, message.getFiles())
                break;
            case SERVER_METADATA:
                System.out.println("Error: Client sent server metadata");
                break;
            case SERVER_PAYLOAD:
                System.out.println("Error: Client sent server payload");
                break;
            case CLIENT_ACK:
                ClientState clientState = clientStateMap.get(endpoint);
//                if(message.getResendEntries().size() > 0 && clientState.uniqueResend()) {
                    int transmissionSpeed = clientState.transmissionSpeed;
                    clientState.maximumTransmissionSpeed = max(1, transmissionSpeed / 2);
//                } else {
                    clientState.maximumTransmissionSpeed++;
//                }
                break;
            case CLOSE_CONNECTION:
                clientStateMap.remove(endpoint);
                break;
        }
    }

    private Map<Endpoint, ClientState> clientStateMap;
    private Network network;

}
