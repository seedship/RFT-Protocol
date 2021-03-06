package com.protocoldesigngroup2.xxx.messages;

import java.util.List;
import java.util.Arrays;


public class ServerMetadata extends Message {

    public static final int SERVER_METADATA_HEADER_LENGTH = 28;

    public static final int DOWNLOAD_NORMAL_ID = 0;
    public static final int FILE_DOES_NOT_EXIST_ID = 1;
    public static final int FILE_IS_EMPTY_ID = 2;
    public static final int ACCESS_DENIED_ID = 3;
    public static final int OFFSET_BIGGER_THAN_FILESIZE_ID = 4;

    public static enum Status {
        DOWNLOAD_NORMAL(DOWNLOAD_NORMAL_ID),
        FILE_DOES_NOT_EXIST(FILE_DOES_NOT_EXIST_ID),
        FILE_IS_EMPTY(FILE_IS_EMPTY_ID),
        ACCESS_DENIED(ACCESS_DENIED_ID),
        OFFSET_BIGGER_THAN_FILESIZE(OFFSET_BIGGER_THAN_FILESIZE_ID);

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
            case OFFSET_BIGGER_THAN_FILESIZE_ID:
                return OFFSET_BIGGER_THAN_FILESIZE;
            default:
                throw new WrongIdException("Wrong status (Metadata): " + id);
            }
        }
    }

    public final Status status;
    public final int fileNumber;
    public final long fileSize;
    public final byte[] checksum;

    public ServerMetadata(int ackNumber, List<Option> options, Status status, int fileNumber, long fileSize, byte[] checksum) {
        super(ackNumber, options);

        if (checksum.length != 16) {
            throw new RuntimeException("Checksum must be 16 bytes long (MD5).");
        }

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
        byte[] checksum = new byte[16];
        System.arraycopy(buffer, offset + 12, checksum, 0, checksum.length);
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
        System.arraycopy(checksum, 0, message, offset + 12, checksum.length);

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
                + ", checksum length: " + checksum.length;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;

        if (!(o instanceof ServerMetadata))
            return false;

        ServerMetadata serverMetadata = (ServerMetadata)o;

        return version == serverMetadata.version
            && ackNumber == serverMetadata.ackNumber
            && options.equals(serverMetadata.options)
            && status == serverMetadata.status
            && fileNumber == serverMetadata.fileNumber
            && fileSize == serverMetadata.fileSize
            && Arrays.equals(checksum, serverMetadata.checksum);
    }
}
