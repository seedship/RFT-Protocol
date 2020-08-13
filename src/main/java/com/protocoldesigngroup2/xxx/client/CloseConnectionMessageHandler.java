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
        client.serverResponds();
        client.receiveAckNumber(message.ackNumber);
        CloseConnection _message = (CloseConnection) message;
        switch (_message.reason.id) {
            case CloseConnection.UNSPECIFIED_ID:
            case CloseConnection.APPLICATION_CLOSED_ID:
            case CloseConnection.UNSUPPORTED_VERSION_ID:
            case CloseConnection.FILE_TOO_SMALL_ID:
                client.deletePendingFiles();
                break;
            case CloseConnection.UNKNOWN_REQUEST_ID_ID:
            case CloseConnection.TIMEOUT_ID:
                client.restartDownloads();
                break;
            default:
                break;
        }
    }

}