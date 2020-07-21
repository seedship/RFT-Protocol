package com.protocoldesigngroup2.xxx.messages;

import java.util.List;
import java.util.ArrayList;


public class ClientAck extends Message {

    public static final int NOTHING_ID = 0;
    public static final int NO_METADATA_RECEIVED_ID = 1;

    public static enum Status {
        NOTHING(NOTHING_ID),
        NO_METADATA_RECEIVED(NO_METADATA_RECEIVED_ID);
 
        private int id;
        private Status(int id) {
            this.id = id;
        }
        public int getId() {
            return id;
        }
        public static Status fromId(int id) throws WrongIdException {
            switch (id) {
            case NOTHING_ID:
                return NOTHING;
            case NO_METADATA_RECEIVED_ID:
                return NO_METADATA_RECEIVED;
            default:
                throw new WrongIdException("Wrong status (ACK): " + id);
            }
        }
    }

    public static class ResendEntry {
        private int fileNumber;
        private long offset;
        private short length;

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
    }

    private int fileNumber;
    private Status status;
    private long maxTransmissionRate;
    private long offset;
    private List<ResendEntry> resendEntries;

    public ClientAck(int ackNumber, List<Option> options, int fileNumber, Status status, long maxTransmissionRate, long offset, List<ResendEntry> resendEntries) {
        super(ackNumber, options);

        this.fileNumber = fileNumber;
        this.status = status;
        this.maxTransmissionRate = maxTransmissionRate;
        this.offset = offset;
        this.resendEntries = resendEntries;
    }

    public static ClientAck decode(byte[] buffer, int offset, int length, int ackNumber, List<Option> options) {
        int fileNumber = ((buffer[offset] & 0xff) << 8) + (buffer[offset + 1] & 0xff);

        Status status;
        try {
            status = Status.fromId(buffer[offset + 2] & 0xff);
        } catch (WrongIdException e) {
            e.printStackTrace();
            return null;
        }
        long maxTransmissionRate = 0;
        for (int i = 0; i < 4; i++) {
            maxTransmissionRate = (maxTransmissionRate << 8) + (buffer[offset + 3 + i] & 0xff);
        }
        long parsedOffset = 0;
        for (int i = 0; i < 7; i++) {
            parsedOffset = (parsedOffset << 8) + (buffer[offset + 7 + i] & 0xff);
        }
        offset += 14;

        // Getting the number of resend entries from the length of the packet
        int numberOfResendEntries = (length - 14) / 10;
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
            offset += 10;
        }

        return new ClientAck(ackNumber, options, fileNumber, status, maxTransmissionRate, parsedOffset, resendEntries);
    }

    public int getFileNumber() {
        return fileNumber;
    }

    public Status getStatus() {
        return status;
    }

    public long getMaxTransmissionRate() {
        return maxTransmissionRate;
    }

    public long getOffset() {
        return offset;
    }

    public List<ResendEntry> getResendEntries() {
        return resendEntries;
    }

    @Override
    public byte[] encode() {
        int totalLength = getGlobalHeaderLength() + 14 + resendEntries.size() * 10;
        byte[] message = new byte[totalLength];
        int offset = encodeGlobalHeader(message);

        // Not using loops to (hopefully) improve readability
        // FileNumber & Status
        message[offset] = (byte)((fileNumber >> 8) & 0xff);
        message[offset + 1] = (byte)(fileNumber & 0xff);
        message[offset + 2] = (byte)(status.getId());
        // TransmissionRate
        message[offset + 3] = (byte)((maxTransmissionRate >> 24) & 0xff);
        message[offset + 4] = (byte)((maxTransmissionRate >> 16) & 0xff);
        message[offset + 5] = (byte)((maxTransmissionRate >> 8) & 0xff);
        message[offset + 6] = (byte)(maxTransmissionRate & 0xff);
        // Offset
        message[offset + 7] = (byte)((this.offset >> 48) & 0xff);
        message[offset + 8] = (byte)((this.offset >> 40) & 0xff);
        message[offset + 9] = (byte)((this.offset >> 32) & 0xff);
        message[offset + 10] = (byte)((this.offset >> 24) & 0xff);
        message[offset + 11] = (byte)((this.offset >> 16) & 0xff);
        message[offset + 12] = (byte)((this.offset >> 8) & 0xff);
        message[offset + 13] = (byte)(this.offset & 0xff);
        
        offset += 14;
        for (ResendEntry resendEntry : resendEntries) {
            resendEntry.encode(message, offset);
            offset += 10;
        }
        return message;
    }

    @Override
    public Type getMessageType() {
        return Message.Type.CLIENT_ACK;
    }

    @Override
    public String toString() {
        String r = "ClientAck; ackNumber: " + getAckNumber()
                + ", Version: " + getVersion()
                + ", Filenumber: " + fileNumber
                + ", status: " + status
                + ", maxTransmissionRate: " + maxTransmissionRate
                + ", offset: " + offset;
        for (ResendEntry re : resendEntries) {
            r += ", re: " + re.toString();
        }
        return r;
    }
}
