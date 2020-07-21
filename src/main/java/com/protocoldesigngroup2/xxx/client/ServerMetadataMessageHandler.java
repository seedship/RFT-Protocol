package com.protocoldesigngroup2.xxx.client;

import com.protocoldesigngroup2.xxx.messages.Message;
import com.protocoldesigngroup2.xxx.messages.ServerMetadata;
import com.protocoldesigngroup2.xxx.network.Endpoint;
import com.protocoldesigngroup2.xxx.network.MessageHandler;

public class ServerMetadataMessageHandler implements MessageHandler {

    Client client;

    ServerMetadataMessageHandler(Client client) {
        this.client = client;
    }

    @Override
    public void handleMessage(Message message, Endpoint endpoint) {
        if (!(message instanceof ServerMetadata)) {
            return;
        }
        client.receiveAckNumber(message.getAckNumber());
        ServerMetadata _message = (ServerMetadata) message;
        client.setFileMetadata(_message.getFileNumber(), _message.getFileSize(), _message.getChecksum());;
    }

}