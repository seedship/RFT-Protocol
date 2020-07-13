package com.protocoldesigngroup2.xxx;

import java.util.List;
import java.util.ArrayList;


public abstract class Message {
    protected int version;
    protected List<Option> options;

    public enum Type {
        CLIENT_REQUEST,
        SERVER_METADATA,
        SERVER_PAYLOAD,
        CLIENT_ACK,
        CLOSE_CONNECTION
    }

    public Message(int version) {
        options = new ArrayList<Option>();
    }

    public int getVersion() {
        return version;
    }

    public List<Option> getOptions() {
        return options;
    }

    public static Message getFromBuffer() {
        return new ClientAck();
    }

    public abstract byte[] encode();
    public abstract Type getMessageType();
}
