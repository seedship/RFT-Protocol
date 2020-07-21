package com.protocoldesigngroup2.xxx.messages;

import java.util.List;


public class ServerMetadata extends Message {
    
    public static final int SERVER_METADATA_HEADER_LENGTH = 20;

    public static final int DOWNLOAD_NORMAL_ID = 0;
    public static final int FILE_DOES_NOT_EXIST_ID = 1;
    public static final int FILE_IS_EMPTY_ID = 2;
    public static final int ACCESS_DENIED_ID = 3;

    public static enum Status {
        DOWNLOAD_NORMAL(DOWNLOAD_NORMAL_ID),
        FILE_DOES_NOT_EXIST(FILE_DOES_NOT_EXIST_ID),
        FILE_IS_EMPTY(FILE_IS_EMPTY_ID),
        ACCESS_DENIED(ACCESS_DENIED_ID);

        public final int id;
        private Status(int id) {
            this.id = id;
        }
        public static Status fromId(int id) throws WrongIdException {
            switch (id) {
            case DOWNLOAD_NORMAL_ID:
                return DOWNLOAD_NORMAL;
            case FILE_DOES_NOT_EXIST_ID:
                return FILE_DOES_NOT_EXIST;
            case FILE_IS_EMPTY_ID:
                return FILE_IS_EMPTY;
            case ACCESS_DENIED_ID:
                return ACCESS_DENIED;
            default:
                throw new WrongIdException("Wrong status (Metadata): " + id);
            }
        }
    }

    public final Status status;
    public final int fileNumber;
    public final long fileSize;
    public final long checksum;

    public ServerMetadata(int ackNumber, List<Option> options, Status status, int fileNumber, long fileSize, long /* for now */ checksum) {
        super(ackNumber, options);

        this.status = status;
        this.fileNumber = fileNumber;
        this.fileSize = fileSize;
        this.checksum = checksum;
    }

    public static ServerMetadata decode(byte[] buffer, int offset, int length, int ackNumber, List<Option> options) {
        if (length != offset + SERVER_METADATA_HEADER_LENGTH) {
            System.out.println("Wrong length for Metadata");
            return null;
        }
        Status status;
        try {
            status = Status.fromId(buffer[offset + 1] & 0xff);
        } catch (WrongIdException e) {
            e.printStackTrace();
            return null;
        }
        int fileNumber = ((buffer[offset + 2] & 0xff) << 8) + (buffer[offset + 3] & 0xff);
        long fileSize = 0;
        for (int i = 0; i < 8; i++) {
            fileSize = (fileSize << 8) + (buffer[offset + 4 + i] & 0xff);
        }
        long checksum = 0;
        for (int i = 0; i < 8; i++) {
            checksum = (checksum << 8) + (buffer[offset + 12 + i] & 0xff);
        }
        return new ServerMetadata(ackNumber, options, status, fileNumber, fileSize, checksum);
    }

    @Override
    public byte[] encode() {
        int totalLength = getGlobalHeaderLength() + SERVER_METADATA_HEADER_LENGTH;
        byte[] message = new byte[totalLength];
        int offset = encodeGlobalHeader(message);

        // Not using loops to (hopefully) improve readability
        // Status & FileNumber
        message[offset] = (byte)(0x00);
        message[offset + 1] = (byte)(status.id & 0xff);
        message[offset + 2] = (byte)((fileNumber >> 8) & 0xff);
        message[offset + 3] = (byte)(fileNumber & 0xff);
        // FileSize
        message[offset + 4] = (byte)((fileSize >> 56) & 0xff);
        message[offset + 5] = (byte)((fileSize >> 48) & 0xff);
        message[offset + 6] = (byte)((fileSize >> 40) & 0xff);
        message[offset + 7] = (byte)((fileSize >> 32) & 0xff);
        message[offset + 8] = (byte)((fileSize >> 24) & 0xff);
        message[offset + 9] = (byte)((fileSize >> 16) & 0xff);
        message[offset + 10] = (byte)((fileSize >> 8) & 0xff);
        message[offset + 11] = (byte)(fileSize & 0xff);
        // Checksum
        message[offset + 12] = (byte)((checksum >> 56) & 0xff);
        message[offset + 13] = (byte)((checksum >> 48) & 0xff);
        message[offset + 14] = (byte)((checksum >> 40) & 0xff);
        message[offset + 15] = (byte)((checksum >> 32) & 0xff);
        message[offset + 16] = (byte)((checksum >> 24) & 0xff);
        message[offset + 17] = (byte)((checksum >> 16) & 0xff);
        message[offset + 18] = (byte)((checksum >> 8) & 0xff);
        message[offset + 19] = (byte)(checksum & 0xff);
        
        return message;
    }

    @Override
    public Type getMessageType() {
        return Message.Type.SERVER_METADATA;
    }

    @Override
    public String toString() {
        return "ServerMetadata, ackNumber: " + ackNumber
                + ", version: " + version
                + ", status: " + status
                + ", fileNumber: " + fileNumber
                + ", fileSize: " + fileSize
                + ", checksum: " + checksum;
    }
}
