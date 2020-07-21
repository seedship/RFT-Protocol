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
        switch (_message.getReason()) {
            case CloseConnection.Reason.UNSPECIFIED_ID:
            case CloseConnection.Reason.APPLICATION_CLOSED_ID:
            case CloseConnection.Reason.UNSUPPORTED_VERSION_ID:
            case CloseConnection.Reason.FILE_TOO_SMALL_ID:
                client.deletePendingFiles();
                break;
            case CloseConnection.Reason.UNKNOWN_REQUEST_ID_ID:
            case CloseConnection.Reason.TIMEOUT_ID:
                client.restartDownloads();
                break;
            default:
                break;

        }
    }

}