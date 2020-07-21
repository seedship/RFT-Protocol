package com.protocoldesigngroup2.xxx.network;

import com.protocoldesigngroup2.xxx.messages.Message;


public interface MessageHandler {
    public void handleMessage(Message message, Endpoint endpoint);
}
