package com.protocoldesigngroup2.xxx.client;

import com.protocoldesigngroup2.xxx.messages.Message;
import com.protocoldesigngroup2.xxx.messages.ServerMetadata;
import com.protocoldesigngroup2.xxx.network.Endpoint;
import com.protocoldesigngroup2.xxx.network.MessageHandler;

public class ServerMetadataMessageHandler implements MessageHandler {

    private Client client;

    public ServerMetadataMessageHandler(Client client) {
        this.client = client;
    }

    @Override
    public void handleMessage(Message message, Endpoint endpoint) {
        if (!(message instanceof ServerMetadata)) {
            return;
        }
        client.receiveAckNumber(message.ackNumber);
        ServerMetadata _message = (ServerMetadata) message;

        if (_message.status.id != ServerMetadata.DOWNLOAD_NORMAL_ID) {
            client.deletePendingFile(_message.fileNumber);
            return;
        }
        // TODO: Add correct checksum here
        client.setFileMetadata(_message.fileNumber, _message.fileSize, _message.checksum);
    }

}