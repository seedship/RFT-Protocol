package com.protocoldesigngroup2.xxx.client;

import com.protocoldesigngroup2.xxx.messages.Message;
import com.protocoldesigngroup2.xxx.messages.ServerPayload;
import com.protocoldesigngroup2.xxx.network.Endpoint;
import com.protocoldesigngroup2.xxx.network.MessageHandler;

public class ServerPayloadMessageHandler implements MessageHandler {

    private Client client;

    public ServerPayloadMessageHandler(Client client) {
        this.client = client;
    }

    @Override
    public void handleMessage(Message message, Endpoint endpoint) {
        if (!(message instanceof ServerPayload)) {
            return;
        }
        client.serverResponds();
        client.receiveAckNumber(message.ackNumber);
        ServerPayload _message = (ServerPayload) message;
        client.writeChunkToFile(_message.fileNumber, _message.offset, _message.payload);
    }

}