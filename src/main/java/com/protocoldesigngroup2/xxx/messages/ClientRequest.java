package com.protocoldesigngroup2.xxx.messages;


public class ClientRequest extends Message {
    public ClientRequest() {
        super(5);
    }

    public byte[] encode() {
        byte[] abc = new byte[2];
        return abc;
    }

    public Type getMessageType() {
        return Message.Type.CLIENT_REQUEST;
    }
}
