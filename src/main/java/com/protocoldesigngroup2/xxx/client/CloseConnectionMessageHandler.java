package com.protocoldesigngroup2.xxx.client;

import com.protocoldesigngroup2.xxx.messages.CloseConnection;
import com.protocoldesigngroup2.xxx.messages.Message;
import com.protocoldesigngroup2.xxx.network.Endpoint;
import com.protocoldesigngroup2.xxx.network.MessageHandler;

public class CloseConnectionMessageHandler implements MessageHandler {
    private Client client;

    public CloseConnectionMessageHandler(Client client) {
        this.client = client;
    }

    @Override
    public void handleMessage(Message message, Endpoint endpoint) {
        if (!(message instanceof CloseConnection)) {
            return;
        }
        client.receiveAckNumber(message.ackNumber);
        CloseConnection _message = (CloseConnection) message;
        switch (_message.reason.id) {
            case 0:
            case 1:
            case 2:
            case 7:
                client.deletePendingFiles();
                break;
            case 3:
            case 6:
                client.restartDownloads();
                break;
            default:
                break;
        }
    }

}