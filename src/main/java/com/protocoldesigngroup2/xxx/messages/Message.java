package com.protocoldesigngroup2.xxx.messages;

import java.util.List;


public abstract class Message {

    public static final int VERSION = 1;
    
    public static final int CLIENT_REQUEST_ID = 0;
    public static final int SERVER_METADATA_ID = 1;
    public static final int SERVER_PAYLOAD_ID = 2;
    public static final int CLIENT_ACK_ID = 3;
    public static final int CLOSE_CONNECTION_ID = 4;
    
    public static enum Type {
        CLIENT_REQUEST(CLIENT_REQUEST_ID),
        SERVER_METADATA(SERVER_METADATA_ID),
        SERVER_PAYLOAD(SERVER_PAYLOAD_ID),
        CLIENT_ACK(CLIENT_ACK_ID),
        CLOSE_CONNECTION(CLOSE_CONNECTION_ID);

        private int id;
        private Type(int id) {
            this.id = id;
        }
        public int getId() {
            return id;
        }
        public static Type fromId(int id) throws WrongIdException {
            switch (id) {
            case CLIENT_REQUEST_ID:
                return CLIENT_REQUEST;
            case SERVER_METADATA_ID:
                return SERVER_METADATA;
            case SERVER_PAYLOAD_ID:
                return SERVER_PAYLOAD;
            case CLIENT_ACK_ID:
                return CLIENT_ACK;
            case CLOSE_CONNECTION_ID:
                return CLOSE_CONNECTION;
            default:
                throw new WrongIdException("Wrong message id: " + id);
            }
        }
    }

    private byte version;
    private int ackNumber;
    private List<Option> options;

    protected Message(int ackNumber, List<Option> options) {
        if (ackNumber > 255) {
            throw new RuntimeException("AckNumber must be less than 256. AckNumber: " + ackNumber);
        }
        if (options.size() > 255) {
            throw new RuntimeException("Length of options must be less than 256. Length: " + options.size());
        }
        this.version = VERSION;
        this.ackNumber = ackNumber;
        this.options = options;
    }

    public int getVersion() {
        return version;
    }

    public int getAckNumber() {
        return ackNumber;
    }

    public List<Option> getOptions() {
        return options;
    }

    // Calculates the length of the global header to allocate memory
    public int getGlobalHeaderLength() {
        int totalLength = 3;
        for (Option option : options) {
            // +2 for the optionType and optionLength
            totalLength += option.getLength() + 2;
        }
        return totalLength;
    }

    // Adds the global header to the beginning of the buffer and
    // returns the length of the global header
    public int encodeGlobalHeader(byte[] buffer) {
        // Adding always needed stuff
        buffer[0] = (byte)((version << 4) + getMessageType().getId());
        buffer[1] = (byte)(ackNumber);
        buffer[2] = (byte)(options.size());

        // Adding options to the header
        int offset = 3;
        for (Option option : options) {
            option.encodeOption(buffer, offset);
            offset += option.getLength() + 2;
        }
        return offset;
    }

    public abstract byte[] encode();
    public abstract Type getMessageType();
}
