package com.protocoldesigngroup2.xxx.server;

import com.protocoldesigngroup2.xxx.messages.ClientAck;
import com.protocoldesigngroup2.xxx.messages.CloseConnection;
import com.protocoldesigngroup2.xxx.messages.Message;
import com.protocoldesigngroup2.xxx.messages.Option;
import com.protocoldesigngroup2.xxx.network.Endpoint;
import com.protocoldesigngroup2.xxx.network.MessageHandler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class ClientAckHandler implements MessageHandler {

    private final Server server;

    public ClientAckHandler(Server server) {
        this.server = server;
    }

    public void handleMessage(Message message, Endpoint endpoint) {
        if (!(message instanceof ClientAck)) {
            System.out.println("ClientAckHandler received non Client ACK message");
            return;
        }
        ClientAck ack = (ClientAck) message;
        // TODO sync with network
        // Get Client State
        ClientState clientState = server.clientStateMap.get(endpoint);
        if (clientState == null) {
            // Close Connection (Unknown RequestID)
            server.network.sendMessage(new CloseConnection(ack.ackNumber, new ArrayList<>(),
                    CloseConnection.Reason.UNKNOWN_REQUEST_ID), endpoint);
            return;
        }
        // Update ack and last received time
        clientState.updateLastReceivedAck(ack.ackNumber);
        // Update max transmission rate
        clientState.updateClientMaxTransmissionSpeed(ack.maxTransmissionRate);
        // Check for resend metadata
        if (ack.status == ClientAck.Status.NO_METADATA_RECEIVED) {
            clientState.sentMetadata.put(ack.fileNumber, false);
        }
        // Check for resend entries
        List<ClientAck.ResendEntry> resendEntries = ack.resendEntries;
        if (resendEntries.size() > 0) {
            // If resend entries are found, add the missing chunks to client state
            for (ClientAck.ResendEntry entry : ack.resendEntries) {
                for (long index = entry.offset; index < entry.offset + entry.length; index++) {
                    int fileNumber = entry.fileNumber;
                    Set<Long> missingChunks = clientState.missingChunks.get(fileNumber);
                    if (missingChunks == null) {
                        clientState.missingChunks.put(fileNumber, Collections.singleton(index));
                    } else {
                        missingChunks.add(index);
                    }
                }
            }
            // For every N acks with resend entries, reduce sending rate
            if (clientState.checkResendAndDecrement()) {
                clientState.decreaseRate();
            }
        } else {
            clientState.resetResendCounter();
            clientState.increaseRate();
        }
    }
}
