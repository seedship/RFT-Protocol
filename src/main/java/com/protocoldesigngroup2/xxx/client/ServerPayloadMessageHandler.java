package com.protocoldesigngroup2.xxx.client;

import com.protocoldesigngroup2.xxx.messages.Message;
import com.protocoldesigngroup2.xxx.messages.ServerPayload;
import com.protocoldesigngroup2.xxx.network.Endpoint;
import com.protocoldesigngroup2.xxx.network.MessageHandler;

public class ServerPayloadMessageHandler implements MessageHandler {

    Client client;

    ServerPayloadMessageHandler(Client client) {
        this.client = client;
    }

    @Override
    public void handleMessage(Message message, Endpoint endpoint) {
        if (!(message instanceof ServerPayload)) {
            return;
        }
        client.receiveAckNumber(message.getAckNumber());
        ServerPayload _message = (ServerPayload) message;
        client.writeChunkToFile(_message.getFileNumber(), _message.getOffset(), _message.getPayload());
    }

}