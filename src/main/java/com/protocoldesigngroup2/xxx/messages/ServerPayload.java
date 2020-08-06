package com.protocoldesigngroup2.xxx.messages;

import java.util.List;
import java.util.Arrays;

public class ServerPayload extends Message {

    public static int SERVER_PAYLOAD_HEADER_LENGTH = 9;
    
    // TODO: Remove the need to copy the payload in encode
    public final int fileNumber;
    public final long offset;
    public final byte[] payload;
    public final int payloadLength;
    
    public ServerPayload(int ackNumber, List<Option> options, int fileNumber, long offset, byte[] payload, int payloadLength) {
        super(ackNumber, options);

        if (payload.length > 1024) {
            throw new RuntimeException("Payload cannot be bigger than 1024 bytes. Size: " + payload.length);
        }
        this.fileNumber = fileNumber;
        this.offset = offset;
        this.payload = payload;
        this.payloadLength = payloadLength;
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
        byte[] payload = new byte[length - offset - SERVER_PAYLOAD_HEADER_LENGTH];
        System.arraycopy(buffer, offset + SERVER_PAYLOAD_HEADER_LENGTH, payload, 0, payload.length);
        return new ServerPayload(ackNumber, options, fileNumber, parsedOffset, payload, payload.length);
    }

    @Override
    public byte[] encode() {
        int totalLength = getGlobalHeaderLength() + SERVER_PAYLOAD_HEADER_LENGTH + payloadLength;
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

        System.arraycopy(payload, 0, message, offset + 9, payloadLength);

        return message;
    }

    @Override
    public Type getMessageType() {
        return Message.Type.SERVER_PAYLOAD;
    }

    @Override
    public String toString() {
        String r = "ServerPayload, ackNumber: " + ackNumber
                    + ", version: " + version
                    + ", fileNumber: " + fileNumber
                    + ", offset: " + offset
                    + ", payload size: " + payload.length;
        return r;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;

        if (!(o instanceof ServerPayload))
            return false;

        ServerPayload serverPayload = (ServerPayload)o;
        
        return version == serverPayload.version
            && ackNumber == serverPayload.ackNumber
            && options.equals(serverPayload.options)
            && fileNumber == serverPayload.fileNumber
            && offset == serverPayload.offset
            && Arrays.equals(payload, serverPayload.payload);
    }
}
