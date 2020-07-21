package com.protocoldesigngroup2.xxx.messages;

import java.util.List;
import java.util.ArrayList;
import java.nio.charset.StandardCharsets;


public class ClientRequest extends Message {

    public static final int FILE_DESCRIPTOR_HEADER_LENGTH = 9;
    public static final int CLIENT_REQUEST_HEADER_LENGTH = 6;

    public static class FileDescriptor {
        private long offset;
        private String filename;
        
        public FileDescriptor(long offset, String filename) {
            if (filename.length() > 65535) {
                throw new RuntimeException("Filename must be shorter than 65535 symbols");
            }
            this.offset = offset;
            this.filename = filename;
        }

        public int getSize() {
            return filename.length() + FILE_DESCRIPTOR_HEADER_LENGTH;
        }

        public void encode(byte[] buffer, int offset) {
            buffer[offset] = (byte)((this.offset >> 48) & 0xff);
            buffer[offset + 1] = (byte)((this.offset >> 40) & 0xff);
            buffer[offset + 2] = (byte)((this.offset >> 32) & 0xff);
            buffer[offset + 3] = (byte)((this.offset >> 24) & 0xff);
            buffer[offset + 4] = (byte)((this.offset >> 16) & 0xff);
            buffer[offset + 5] = (byte)((this.offset >> 8) & 0xff);
            buffer[offset + 6] = (byte)(this.offset & 0xff);
            buffer[offset + 7] = (byte)((filename.length() >> 8) & 0xff);
            buffer[offset + 8] = (byte)(filename.length() & 0xff);

            byte[] filenameUtf8 = filename.getBytes(StandardCharsets.UTF_8);
            System.arraycopy(
                filenameUtf8,
                0,
                buffer,
                offset + FILE_DESCRIPTOR_HEADER_LENGTH,
                filenameUtf8.length);
        }

        @Override
        public String toString() {
            return "FileDescriptor, offset: " + offset + ", filename: " + filename;
        }
    }

    private long maxTransmissionRate;
    private List<FileDescriptor> files;

    public ClientRequest(int ackNumber, List<Option> options, long maxTransmissionRate, List<FileDescriptor> files) {
        super(ackNumber, options);

        this.maxTransmissionRate = maxTransmissionRate;
        this.files = files;
    }
    
    public static ClientRequest decode(byte[] buffer, int offset, int length, int ackNumber, List<Option> options) {
        if (length < offset + CLIENT_REQUEST_HEADER_LENGTH) {
            System.out.println("ClientRequest too short");
            return null;
        }
        long maxTransmissionRate = 0;
        for (int i = 0; i < 4; i++) {
            maxTransmissionRate = (maxTransmissionRate << 8) + (buffer[offset + i] & 0xff);
        }
        int fileNumber = ((buffer[offset + 4] & 0xff) << 8) + (buffer[offset + 5] & 0xff);
        offset += CLIENT_REQUEST_HEADER_LENGTH;
        
        List<FileDescriptor> files = new ArrayList<>();
        for (int i = 0; i < fileNumber; i++) {
            long parsedOffset = 0;
            for (int j = 0; j < 7; j++) {
                parsedOffset = (parsedOffset << 8) + (buffer[offset + j] & 0xff);
            }
            int filenameLength = ((buffer[offset + 7] & 0xff) << 8) + (buffer[offset + 8] & 0xff);
            String filename = new String(buffer,
                                         offset + FILE_DESCRIPTOR_HEADER_LENGTH,
                                         filenameLength,
                                         StandardCharsets.UTF_8);

            files.add(new FileDescriptor(parsedOffset, filename));
            offset += filenameLength + FILE_DESCRIPTOR_HEADER_LENGTH;
        }

        return new ClientRequest(ackNumber, options, maxTransmissionRate, files);
    }

    public long getMaxTransmissionRate() {
        return maxTransmissionRate;
    }

    public List<FileDescriptor> getFiles() {
        return files;
    }

    @Override
    public byte[] encode() {
        int totalLength = getGlobalHeaderLength() + CLIENT_REQUEST_HEADER_LENGTH;
        for (FileDescriptor file : files) {
            totalLength += file.getSize();
        }
        byte[] message = new byte[totalLength];
        int offset = encodeGlobalHeader(message);

        message[offset] = (byte)((maxTransmissionRate >> 24) & 0xff);
        message[offset + 1] = (byte)((maxTransmissionRate >> 16) & 0xff);
        message[offset + 2] = (byte)((maxTransmissionRate >> 8) & 0xff);
        message[offset + 3] = (byte)(maxTransmissionRate & 0xff);
        message[offset + 4] = (byte)((files.size() >> 8) & 0xff);
        message[offset + 5] = (byte)(files.size() & 0xff);

        offset += CLIENT_REQUEST_HEADER_LENGTH;
        for (FileDescriptor file : files) {
            file.encode(message, offset);
            offset += file.getSize();
        }
        return message;
    }

    @Override
    public Type getMessageType() {
        return Message.Type.CLIENT_REQUEST;
    }

    @Override
    public String toString() {
        String r = "ClientRequest, ackNumber: " + getAckNumber()
                    + ", version: " + getVersion()
                    + ", maxTransmissionRate: " + maxTransmissionRate
                    + ", # files: " + files.size();
        for (FileDescriptor file : files) {
            r += ", " + file.toString();
        }
        return r;
    }
}
