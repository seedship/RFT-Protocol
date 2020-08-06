package com.protocoldesigngroup2.xxx.server;

import com.protocoldesigngroup2.xxx.messages.CloseConnection;
import com.protocoldesigngroup2.xxx.messages.Message;
import com.protocoldesigngroup2.xxx.network.Endpoint;
import com.protocoldesigngroup2.xxx.network.MessageHandler;
import com.protocoldesigngroup2.xxx.utils.utils;

public class CloseConnectionHandler implements MessageHandler {
    private final Server server;

    public CloseConnectionHandler(Server server) {
        this.server = server;
    }

    public void handleMessage(Message message, Endpoint endpoint) {
        if (!(message instanceof CloseConnection)) {
            utils.printDebug("CloseConnectionHandler received non Close Connection message");
            return;
        }
        ClientState s = server.clientStateMap.remove(endpoint);
        if (s != null) {
            s.closeAllFiles();
            utils.printDebug("Received Close Connection from endpoint " + endpoint + ". FileHash: " + s.files.hashCode());
        } else {
            utils.printDebug("Received Close Connection from endpoint " + endpoint + ". However, this endpoint was not found.");
        }
    }

}
