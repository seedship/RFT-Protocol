package com.protocoldesigngroup2.xxx.messages;


public class ServerMetadata extends Message {
    public ServerMetadata() {
        super(5);
    }

    public byte[] encode() {
        byte[] abc = new byte[2];
        return abc;
    }

    public Type getMessageType() {
        return Message.Type.SERVER_METADATA;
    }
}
