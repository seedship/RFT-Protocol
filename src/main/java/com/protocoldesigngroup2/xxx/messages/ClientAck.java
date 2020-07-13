package com.protocoldesigngroup2.xxx.messages;


public class ClientAck extends Message {
    public ClientAck() {
        super(5);
    }

    public byte[] encode() {
        byte[] abc = new byte[2];
        return abc;
    }

    public Type getMessageType() {
        return Message.Type.CLIENT_ACK;
    }
}
