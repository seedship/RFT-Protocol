package com.protocoldesigngroup2.xxx.messages;

import java.util.List;


public class ServerPayload extends Message {
    // TODO: Remove the need to copy the payload in encode
    private int fileNumber;
    private long offset;
    private byte[] payload;
    
    public ServerPayload(int ackNumber, List<Option> options, int fileNumber, long offset, byte[] payload) {
        super(ackNumber, options);

        if (payload.length > 1024) {
            throw new RuntimeException("Payload cannot be bigger than 1024 bytes. Size: " + payload.length);
        }
        this.fileNumber = fileNumber;
        this.offset = offset;
        this.payload = payload;
    }

    public static ServerPayload decode(byte[] buffer, int offset, int length, int ackNumber, List<Option> options) {
        if (length < offset + 8) {
            System.out.println("Payload message too short");
            return null;
        }

        int fileNumber = ((buffer[offset] & 0xff) << 8) + (buffer[offset + 1] & 0xff);
        long parsedOffset = 0;
        for (int i = 0; i < 7; i++) {
            parsedOffset = (parsedOffset << 8) + (buffer[offset + 2 + i] & 0xff);
        }
        byte[] payload = new byte[length - offset - 9];
        System.arraycopy(buffer, offset + 9, payload, 0, payload.length);
        return new ServerPayload(ackNumber, options, fileNumber, parsedOffset, payload);
    }

    public int getFileNumber() {
        return fileNumber;
    }

    public long getOffset() {
        return offset;
    }

    public byte[] getPayload() {
        return payload;
    }

    @Override
    public byte[] encode() {
        int totalLength = getGlobalHeaderLength() + 9 + payload.length;
        byte[] message = new byte[totalLength];
        int offset = encodeGlobalHeader(message);

        message[offset] = (byte)((fileNumber >> 8) & 0xff);
        message[offset + 1] = (byte)(fileNumber & 0xff);
        message[offset + 2] = (byte)((this.offset >> 48) & 0xff);
        message[offset + 3] = (byte)((this.offset >> 40) & 0xff);
        message[offset + 4] = (byte)((this.offset >> 32) & 0xff);
        message[offset + 5] = (byte)((this.offset >> 24) & 0xff);
        message[offset + 6] = (byte)((this.offset >> 16) & 0xff);
        message[offset + 7] = (byte)((this.offset >> 8) & 0xff);
        message[offset + 8] = (byte)(this.offset & 0xff);

        System.arraycopy(payload, 0, message, offset + 8, payload.length);

        return message;
    }

    @Override
    public Type getMessageType() {
        return Message.Type.SERVER_PAYLOAD;
    }

    @Override
    public String toString() {
        String r = "ServerPayload, ackNumber: " + getAckNumber()
                    + ", version: " + getVersion()
                    + ", fileNumber: " + fileNumber
                    + ", offset: " + offset
                    + ", payload size: " + payload.length;
        return r;
    }
}
