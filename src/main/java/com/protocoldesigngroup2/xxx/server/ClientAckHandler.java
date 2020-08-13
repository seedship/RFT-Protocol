package com.protocoldesigngroup2.xxx.server;

import com.protocoldesigngroup2.xxx.messages.ClientAck;
import com.protocoldesigngroup2.xxx.messages.CloseConnection;
import com.protocoldesigngroup2.xxx.messages.Message;
import com.protocoldesigngroup2.xxx.network.Endpoint;
import com.protocoldesigngroup2.xxx.network.MessageHandler;
import com.protocoldesigngroup2.xxx.utils.utils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ClientAckHandler implements MessageHandler {

    private final Server server;

    public ClientAckHandler(Server server) {
        this.server = server;
    }

    public void handleMessage(Message message, Endpoint endpoint) {
        if (!(message instanceof ClientAck)) {
            utils.printDebug("ClientAckHandler received non Client ACK message");
            return;
        }
        ClientAck ack = (ClientAck) message;
        // Get Client State
        utils.printDebug("Received ACK from " + endpoint);
        ClientState clientState = server.clientStateMap.get(endpoint);
        if (clientState == null) {
            // Close Connection (Unknown RequestID)
            assert server.network != null;
            server.network.sendMessage(new CloseConnection(ack.ackNumber, new ArrayList<>(),
                    CloseConnection.Reason.UNKNOWN_REQUEST_ID), endpoint);
            return;
        }
        // Update ack, rtt, and last received time
        clientState.updateLastReceivedAck(ack.ackNumber, ack.offset, ack.fileNumber);
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
                utils.printDebug("Adding Resend Entry at offset:" + entry.offset + " length: " + entry.length);
                int fileNumber = entry.fileNumber;
                if (entry.length == 0) {
                    // Entry with length of 0 means resend file metadata
                    clientState.sentMetadata.put(fileNumber, false);
                }
                for (long index = entry.offset; index < entry.offset + entry.length; index++) {
                    Set<Long> missingChunks = clientState.missingChunks.get(fileNumber);
                    if (missingChunks == null) {
                        clientState.missingChunks.put(fileNumber, ConcurrentHashMap.newKeySet());
                        missingChunks = clientState.missingChunks.get(fileNumber);
                    }
                    missingChunks.add(index);
                }
            }
            // If there are resend entries, decrease rate
            clientState.decreaseRate();
        } else {
            // If no resend entries, increase rate
            clientState.increaseRate();
        }
    }
}
