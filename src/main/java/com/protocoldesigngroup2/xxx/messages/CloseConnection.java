package com.protocoldesigngroup2.xxx.messages;


public class CloseConnection extends Message {
    public CloseConnection() {
        super(5);
    }

    public byte[] encode() {
        byte[] abc = new byte[2];
        return abc;
    }

    public Type getMessageType() {
        return Message.Type.CLOSE_CONNECTION;
    }
}
