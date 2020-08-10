package com.protocoldesigngroup2.xxx.messages;

import java.util.List;
import java.util.ArrayList;


public class ClientAck extends Message {
    
    public static final int RESEND_ENTRY_LENGTH = 10;
    public static final int CLIENT_ACK_HEADER_LENGTH = 15;
    
    public static final byte METADATA_RECEIVED_MASK = (byte)0x80;
    public static final byte RTT_MASK_BYTE_ONE = (byte)0x7f;

    public static class ResendEntry {
        public final int fileNumber;
        public final long offset;
        public final short length;

        public ResendEntry(int fileNumber, long offset, short length) {
            this.fileNumber = fileNumber;
            this.offset = offset;
            this.length = length;
        }

        public void encode(byte[] buffer, int offset) {
            buffer[offset] = (byte)((fileNumber >> 8) & 0xff);
            buffer[offset + 1] = (byte)(fileNumber & 0xff);
            buffer[offset + 2] = (byte)((this.offset >> 48) & 0xff);
            buffer[offset + 3] = (byte)((this.offset >> 40) & 0xff);
            buffer[offset + 4] = (byte)((this.offset >> 32) & 0xff);
            buffer[offset + 5] = (byte)((this.offset >> 24) & 0xff);
            buffer[offset + 6] = (byte)((this.offset >> 16) & 0xff);
            buffer[offset + 7] = (byte)((this.offset >> 8) & 0xff);
            buffer[offset + 8] = (byte)(this.offset & 0xff);
            buffer[offset + 9] = (byte)(length & 0xff);
        }

        @Override
        public String toString() {
            return "ResendEntry, fileNumber: " + fileNumber
                    + ", offset: " + offset
                    + ", length: " + length;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;

            if (!(o instanceof ResendEntry))
                return false;

            ResendEntry resendEntry = (ResendEntry)o;
            
            return fileNumber == resendEntry.fileNumber
                && offset == resendEntry.offset
                && length == resendEntry.length;
        }
    }

    public final int fileNumber;
    public final boolean metadataReceived;
    public final int rtt;
    public final long maxTransmissionRate;
    public final long offset;
    public final List<ResendEntry> resendEntries;

    public ClientAck(
            int ackNumber,
            List<Option> options,
            int fileNumber,
            boolean metadataReceived,
            int rtt,
            long maxTransmissionRate,
            long offset,
            List<ResendEntry> resendEntries) {
        super(ackNumber, options);

        if (rtt > 32767 || fileNumber > 65535 || maxTransmissionRate > 4294967295L || offset > 72057594037927935L) {
            throw new RuntimeException("Invalid inputs for ClientAck: " + rtt + ", " + fileNumber + ", " + maxTransmissionRate + ", " + offset);
        }

        this.fileNumber = fileNumber;
        this.metadataReceived = metadataReceived;
        this.rtt = rtt;
        this.maxTransmissionRate = maxTransmissionRate;
        this.offset = offset;
        this.resendEntries = resendEntries;
    }

    public static ClientAck decode(byte[] buffer, int offset, int length, int ackNumber, List<Option> options) {
        if (length < offset + CLIENT_ACK_HEADER_LENGTH) {
            System.out.println("ClientAck too short");
            return null;
        }
        
        int fileNumber = ((buffer[offset] & 0xff) << 8) + (buffer[offset + 1] & 0xff);
        boolean metadataReceived = (buffer[offset + 2] & METADATA_RECEIVED_MASK) != 0;
        int rtt = ((buffer[offset + 2] & RTT_MASK_BYTE_ONE) << 8) + (buffer[offset + 3] & 0xff);
        
        long maxTransmissionRate = 0;
        for (int i = 0; i < 4; i++) {
            maxTransmissionRate = (maxTransmissionRate << 8) + (buffer[offset + 4 + i] & 0xff);
        }
        long parsedOffset = 0;
        for (int i = 0; i < 7; i++) {
            parsedOffset = (parsedOffset << 8) + (buffer[offset + 8 + i] & 0xff);
        }
        offset += CLIENT_ACK_HEADER_LENGTH;

        // Getting the number of resend entries from the length of the packet
        int numberOfResendEntries = (length - CLIENT_ACK_HEADER_LENGTH) / RESEND_ENTRY_LENGTH;
        List<ResendEntry> resendEntries = new ArrayList<>(numberOfResendEntries);
        for (int i = 0; i < numberOfResendEntries; i++) {
            int resendFileNumber = ((buffer[offset] & 0xff) << 8) + (buffer[offset + 1] & 0xff);
            long resendOffset = 0;
            for (int j = 0; j < 7; j++) {
                resendOffset = (resendOffset << 8) + (buffer[offset + 2 + j] & 0xff);
            }
            short resendLength = (short)(buffer[offset + 9] & 0xff);
            ResendEntry resendEntry = new ResendEntry(resendFileNumber, resendOffset, resendLength);
            resendEntries.add(resendEntry);
            offset += RESEND_ENTRY_LENGTH;
        }

        return new ClientAck(ackNumber, options, fileNumber, metadataReceived, rtt, maxTransmissionRate, parsedOffset, resendEntries);
    }

    @Override
    public byte[] encode() {
        int totalLength = getGlobalHeaderLength()
                        + CLIENT_ACK_HEADER_LENGTH
                        + resendEntries.size() * RESEND_ENTRY_LENGTH;
        byte[] message = new byte[totalLength];
        int offset = encodeGlobalHeader(message);

        // Not using loops to (hopefully) improve readability
        // FileNumber & Status
        message[offset] = (byte)((fileNumber >> 8) & 0xff);
        message[offset + 1] = (byte)(fileNumber & 0xff);

        message[offset + 2] = (byte)((rtt + (metadataReceived ? 32768 : 0)) >> 8);
        message[offset + 3] = (byte)(rtt & 0xff);
        // TransmissionRate
        message[offset + 4] = (byte)((maxTransmissionRate >> 24) & 0xff);
        message[offset + 5] = (byte)((maxTransmissionRate >> 16) & 0xff);
        message[offset + 6] = (byte)((maxTransmissionRate >> 8) & 0xff);
        message[offset + 7] = (byte)(maxTransmissionRate & 0xff);
        // Offset
        message[offset + 8] = (byte)((this.offset >> 48) & 0xff);
        message[offset + 9] = (byte)((this.offset >> 40) & 0xff);
        message[offset + 10] = (byte)((this.offset >> 32) & 0xff);
        message[offset + 11] = (byte)((this.offset >> 24) & 0xff);
        message[offset + 12] = (byte)((this.offset >> 16) & 0xff);
        message[offset + 13] = (byte)((this.offset >> 8) & 0xff);
        message[offset + 14] = (byte)(this.offset & 0xff);
        
        offset += CLIENT_ACK_HEADER_LENGTH;
        for (ResendEntry resendEntry : resendEntries) {
            resendEntry.encode(message, offset);
            offset += RESEND_ENTRY_LENGTH;
        }
        return message;
    }

    @Override
    public Type getMessageType() {
        return Message.Type.CLIENT_ACK;
    }

    @Override
    public String toString() {
        String r = "ClientAck; ackNumber: " + ackNumber
                + ", Version: " + version
                + ", Filenumber: " + fileNumber
                + ", metadataReceived: " + metadataReceived
                + ", rtt: " + rtt
                + ", maxTransmissionRate: " + maxTransmissionRate
                + ", offset: " + offset;
        for (ResendEntry re : resendEntries) {
            r += ", re: " + re.toString();
        }
        return r;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;

        if (!(o instanceof ClientAck))
            return false;

        ClientAck clientAck = (ClientAck)o;
        
        return version == clientAck.version
            && ackNumber == clientAck.ackNumber
            && options.equals(clientAck.options)
            && fileNumber == clientAck.fileNumber
            && metadataReceived == clientAck.metadataReceived
            && rtt == clientAck.rtt
            && maxTransmissionRate == clientAck.maxTransmissionRate
            && offset == clientAck.offset
            && resendEntries.equals(clientAck.resendEntries);
    }
}
