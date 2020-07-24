package com.protocoldesigngroup2.xxx.messages;

import java.util.List;


public class CloseConnection extends Message {

    public static final int CLOSE_CONNECTION_HEADER_LENGTH = 2;

    public static final int UNSPECIFIED_ID = 0;
    public static final int APPLICATION_CLOSED_ID = 1;
    public static final int UNSUPPORTED_VERSION_ID = 2;
    public static final int UNKNOWN_REQUEST_ID_ID = 3;
    public static final int WRONG_CHECKSUM_ID = 4;
    public static final int DOWNLOAD_FINISHED_ID = 5;
    public static final int TIMEOUT_ID = 6;
    public static final int FILE_TOO_SMALL_ID = 7;

    public static enum Reason {
        UNSPECIFIED(UNSPECIFIED_ID),
        APPLICATION_CLOSED(APPLICATION_CLOSED_ID),
        UNSUPPORTED_VERSION(UNSUPPORTED_VERSION_ID),
        UNKNOWN_REQUEST_ID(UNKNOWN_REQUEST_ID_ID),
        WRONG_CHECKSUM(WRONG_CHECKSUM_ID),
        DOWNLOAD_FINISHED(DOWNLOAD_FINISHED_ID),
        TIMEOUT(TIMEOUT_ID),
        FILE_TOO_SMALL(FILE_TOO_SMALL_ID);

        public final int id;
        private Reason(int id) {
            this.id = id;
        }

        public static Reason fromId(int id) throws WrongIdException {
            switch (id) {
            case UNSPECIFIED_ID:
                return UNSPECIFIED;
            case APPLICATION_CLOSED_ID:
                return APPLICATION_CLOSED;
            case UNSUPPORTED_VERSION_ID:
                return UNSUPPORTED_VERSION;
            case UNKNOWN_REQUEST_ID_ID:
                return UNKNOWN_REQUEST_ID;
            case WRONG_CHECKSUM_ID:
                return WRONG_CHECKSUM;
            case DOWNLOAD_FINISHED_ID:
                return DOWNLOAD_FINISHED;
            case TIMEOUT_ID:
                return TIMEOUT;
            case FILE_TOO_SMALL_ID:
                return FILE_TOO_SMALL;
            default:
                throw new WrongIdException("Wrong reason: " + id);
            }
        }
    }

    public final Reason reason;

    public CloseConnection(int ackNumber, List<Option> options, Reason reason) {
        super(ackNumber, options);
        
        this.reason = reason;
    }

    public static CloseConnection decode(byte[] buffer, int offset, int length, int ackNumber, List<Option> options) {
        if (length != offset + CLOSE_CONNECTION_HEADER_LENGTH) {
            System.out.println("Wrong length for CloseConnection");
            return null;
        }
        try {
            int reasonId = ((buffer[offset] & 0xff) << 8) + (buffer[offset + 1] & 0xff);
            Reason reason = Reason.fromId(reasonId);
            return new CloseConnection(ackNumber, options, reason);
        } catch (WrongIdException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public byte[] encode() {
        int totalLength = getGlobalHeaderLength() + CLOSE_CONNECTION_HEADER_LENGTH;
        byte[] message = new byte[totalLength];
        int offset = encodeGlobalHeader(message);

        message[offset] = (byte)((reason.id >> 8) & 0xff);
        message[offset + 1] = (byte)(reason.id & 0xff);

        return message;
    }

    @Override
    public Type getMessageType() {
        return Message.Type.CLOSE_CONNECTION;
    }

    @Override
    public String toString() {
        return "CloseConnection, ackNumber: " + ackNumber
                + ", version: " + version
                + ", reason: " + reason;
    }
        
    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;

        if (!(o instanceof CloseConnection))
            return false;

        CloseConnection closeConnection = (CloseConnection)o;
        
        return reason == closeConnection.reason;
    }
}
