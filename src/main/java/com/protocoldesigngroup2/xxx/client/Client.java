package com.protocoldesigngroup2.xxx.client;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.protocoldesigngroup2.xxx.messages.*;
import com.protocoldesigngroup2.xxx.messages.ClientAck.ResendEntry;
import com.protocoldesigngroup2.xxx.messages.ClientRequest.FileDescriptor;
import com.protocoldesigngroup2.xxx.messages.Message.Type;
import com.protocoldesigngroup2.xxx.network.*;
import com.protocoldesigngroup2.xxx.utils.utils;

public class Client {
    private final long TIMEOUT_INTERVAL = 3000;
    private final int TRANSMISSION_RATE = 0;
    private final int RANDOM_FILENUMBER_UPPERBOUND = 255;

    private class FileEntry {
        File file;
        String name;
        int fileNumber;
        long maxBufferOffset;
        long size;
        byte[] checksum;
        Map<Long,byte[]> buffer;

        public FileEntry(File file, String name, int fileNumber) {
            this.file = file;
            this.name = name;
            this.fileNumber = fileNumber;
            this.maxBufferOffset = 0L;
            this.size = 0;
            this.checksum = new byte[0];
            this.buffer = new HashMap<Long,byte[]>();
        }
    }

    private class AckThread extends Thread {
        private boolean listening = true;

        public void run() {
            if (network == null) {
                return;
            }
            while(listening) {
                try {
                    sleep(ACK_INTERVAL);
                } catch(InterruptedException ie) {
                    ie.printStackTrace();
                }
                sendAck();
            }
        }

        public void stopRunning() {
            listening = false;
        }  
    }

    private class TimeoutThread extends Thread {
        private boolean running = true;

        long lastIncomingDataTime;

        public void reset() {
            lastIncomingDataTime = System.currentTimeMillis();
        }

        public void run() {
            if (network == null) {
                return;
            }
            reset();
            try {
                sleep(TIMEOUT_INTERVAL);
            } catch(InterruptedException ie) {
                ie.printStackTrace();
            }
            while(running) {
                long duration = System.currentTimeMillis() - lastIncomingDataTime;
                reset();
                if (duration > TIMEOUT_INTERVAL) {
                    System.out.println("TIMEOUT");
                    network.sendMessage(
                            new CloseConnection(
                                    getAckNumber(),
                                    new ArrayList<Option>(),
                                    CloseConnection.Reason.TIMEOUT),
                            endpoint);
                    restartDownloads();
                    continue;
                }
                try {
                    sleep(TIMEOUT_INTERVAL - duration);
                } catch(InterruptedException ie) {
                    ie.printStackTrace();
                }
            }
        }

        public void stopRunning() {
            running = false;
        }
    }

    private String destinationPath;
    private Endpoint endpoint;
    private Network network;
    private AckThread ackThread;
    private TimeoutThread timeoutThread;
    private Map<Integer,FileEntry> pendingFiles;
    private long ACK_INTERVAL = 250;
    private int currentAckNumber;
    private long rttStart;
    
    public Client(String address, int port, float p, float q) {
        this.destinationPath = "./download/";
        new File(this.destinationPath).mkdirs();
        this.pendingFiles = new HashMap<Integer,FileEntry>();
        try {
            // Create an endpoint
            this.endpoint = new Endpoint(address, port);

            this.network = Network.createClient(p, q);

            if (network == null) {
                return;
            }

            // Register callbacks
            this.network.addCallbackMethod(Type.SERVER_PAYLOAD, new ServerPayloadMessageHandler(this));
            this.network.addCallbackMethod(Type.SERVER_METADATA, new ServerMetadataMessageHandler(this));
            this.network.addCallbackMethod(Type.CLOSE_CONNECTION, new CloseConnectionMessageHandler(this));
            
            // Start the network
            new Thread(() -> {
                this.network.listen();
            }).start();

            // Start sending acknowledgements in a predefined interval
            this.ackThread = new AckThread();
            this.ackThread.start();
            this.timeoutThread = new TimeoutThread();
            this.timeoutThread.start();
        } catch (UnknownHostException uhe) {
            uhe.printStackTrace();
        }
    }

    public void shutdown() {
        if (network != null) {
            network.sendMessage(
                    new CloseConnection(
                            getAckNumber(),
                            new ArrayList<Option>(),
                            CloseConnection.Reason.UNSPECIFIED),
                    endpoint);
            network.stopListening();
        }
        deletePendingFiles();
    }

    int fileCount = 0;
    private int generateFileNumber() {
        //Random rand = new Random();
        //return rand.nextInt(RANDOM_FILENUMBER_UPPERBOUND);
        return fileCount++;
    }

    //int ackNumberCount = 0;
    private void generateAckNumber() {
        rttStart = System.currentTimeMillis();
        Random rand = new Random();
        this.currentAckNumber = rand.nextInt(RANDOM_FILENUMBER_UPPERBOUND);
        //this.currentAckNumber = ackNumberCount++;
        isNewAckNumberNeeded = false;
    }

    boolean isNewAckNumberNeeded = true;
    public void receiveAckNumber(int receivedAckNumber) {
        if (receivedAckNumber == currentAckNumber) {
            long rtt = System.currentTimeMillis() - rttStart;
            ACK_INTERVAL = rtt / 4;
            isNewAckNumberNeeded = true;
        }
    }

    public void download(List<String> fileInfos) {
        if (network == null) {
            return;
        }
        List<FileDescriptor> descriptors = new ArrayList<FileDescriptor>();
        for (String fileInfo : fileInfos) {
            System.out.println("Downloading file \"" + fileInfo + "\" to " + destinationPath);

            String fileName = fileInfo;
            int fileNumber = generateFileNumber();
            // Add file entry to pending files
            File file = new File(destinationPath + fileName);
            file.delete();
            pendingFiles.put(fileNumber, new FileEntry(file, fileName, fileNumber));

            // Create a descriptor for the Client Request message
            FileDescriptor descriptor = new FileDescriptor(0,fileName);
            descriptors.add(descriptor);
        }
        
        // Sent the Client Request Message over the network
        network.sendMessage(new ClientRequest(getAckNumber(), new ArrayList<Option>(), TRANSMISSION_RATE, descriptors), endpoint);
    }

    private void sendAck() {
        for (Map.Entry<Integer, FileEntry> entry : pendingFiles.entrySet()) {
            FileEntry fileEntry = entry.getValue();

            if (fileEntry.buffer.isEmpty()) continue;

            // Collect all resend entries for the respecting file
            List<ResendEntry> resendEntries = new ArrayList<ResendEntry>();
            short numberOfChunks = 0;
            long resendOffset = 0;
            for (long i = fileEntry.file.length() / utils.KB; i < fileEntry.maxBufferOffset; i++) {
                // Check whether the chunk for the offset exists in the buffer
                if (!fileEntry.buffer.containsKey(i)) {
                    // If it is the first chunk in the continous sequence of unreceived offsets
                    // set the this offset as the resendOffset of the resend entry
                    if (resendOffset == 0) {
                        resendOffset = i;
                    }
                    // Increment the length of the continous sequence of unreceived offsets
                    numberOfChunks++;
                } else {
                    
                    if (numberOfChunks > 0) {
                        System.out.println("Add resend entry:\tOffset: " + resendOffset + "\tNumber of Chunks: " + numberOfChunks);
                        // If there is a chunk for the offset build a resend entry for the preceiding sequence of unreceived offsets
                        resendEntries.add(new ResendEntry(entry.getKey(), resendOffset, numberOfChunks));
                    }
                    
                    // Reset the sequence collection
                    resendOffset = 0;
                    numberOfChunks = 0;
                }
            }

            System.out.println("Send Client Ack message");
            // Send the Client Ack Message over the network
            network.sendMessage(
                    new ClientAck(
                            getAckNumber(),
                            new ArrayList<Option>(),
                            entry.getKey(),
                            ClientAck.Status.NOTHING,
                            TRANSMISSION_RATE,
                            fileEntry.maxBufferOffset + 1,
                            resendEntries),
                    endpoint);
        }
    }

    public void deletePendingFiles() {
        // Delete the data of all pending files
        pendingFiles.forEach((key,value) -> deletePendingFile(key));
    }

    public void deletePendingFile(int fileNumber) {
        if (!pendingFiles.containsKey(fileNumber)) {
            return;
        }
        FileEntry fileEntry = pendingFiles.get(fileNumber);

        System.out.println("Delete pending file " + fileEntry.name);
        // Delete the file and remove the file entry from the pending files
        fileEntry.file.delete();
        pendingFiles.remove(fileNumber);
    }

    private void finishDownload(int fileNumber) {
        FileEntry fileEntry = pendingFiles.get(fileNumber);
        if (!utils.compareMD5(fileEntry.checksum, utils.generateMD5(destinationPath + fileEntry.name))) {
            restartDownload(fileNumber);
        }
        System.out.println("Finish download of " + fileNumber);
        // Remove the file from the pending downloads
        pendingFiles.remove(fileNumber);

        if (pendingFiles.isEmpty()) {
            // Send an Finish Download Close Connection Message and stop sending Client Ack messages
            // if all downloads are finished
            network.sendMessage(
                    new CloseConnection(
                            getAckNumber(),
                            new ArrayList<Option>(),
                            CloseConnection.Reason.DOWNLOAD_FINISHED),
                    endpoint);
            ackThread.stopRunning();
            timeoutThread.stopRunning();
            network.stopListening();
        }
    }

    private void writeBufferToDisk(FileEntry fileEntry) {
        // Check whether the metadata message has already been received
        if (fileEntry.size == 0) {
            return;
        }

        // Check if the file has been fully downloaded
        if (fileEntry.file.length() >= fileEntry.size) {
            finishDownload(fileEntry.fileNumber);
            return;
        }

        long chunkOffset = fileEntry.file.length() / utils.KB;

        // Abort if buffer is empty, 
        // file does not exist or
        // the buffer does not contain the chunk at the next offset
        if (fileEntry.buffer.isEmpty() 
            || !fileEntry.file.exists()
            || !fileEntry.buffer.containsKey(chunkOffset)) {
            return;
        }

        try {
            System.out.println("Write chunk for offset " + chunkOffset + " to file");
            // Open output stream and write chunk to disk and remove it from the buffer
            FileOutputStream fileOut = new FileOutputStream(fileEntry.file, true);
            fileOut.write(fileEntry.buffer.get(chunkOffset));
            fileOut.flush();
            fileOut.close();
            fileEntry.buffer.remove(chunkOffset);
            // Continue with the next offset
            writeBufferToDisk(fileEntry);
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    public void writeChunkToFile(int fileNumber, long chunkOffset, byte[] data) {
        if (!pendingFiles.containsKey(fileNumber)) {
            return;
        }
        timeoutThread.reset();
        FileEntry fileEntry = pendingFiles.get(fileNumber);

        // If file does not exist yet, create it
        if (!fileEntry.file.exists()) {
            try {
                System.out.println("Create new file " + fileEntry.name);
                fileEntry.file.createNewFile();
            } catch (IOException ioe) {
                ioe.printStackTrace();
            } catch (SecurityException se) {
                se.printStackTrace();
            }
        }

        System.out.println("Add chunk for offset " + chunkOffset + " to buffer");
        // Write chunk to buffer and update maxBufferOffset if needed
        long bytesToWrite = utils.KB;
        if (fileEntry.size != 0) {
            bytesToWrite = Math.min(bytesToWrite,fileEntry.size - chunkOffset);
        }
        fileEntry.buffer.put(chunkOffset, Arrays.copyOf(data,data.length));
        fileEntry.maxBufferOffset = Math.max(fileEntry.maxBufferOffset, chunkOffset);
        
        // Check if a continous range of chunks has been received at current offset
        // and write them to disk if so
        writeBufferToDisk(fileEntry);
    }

    private void restartDownload(int fileNumber) {
        if (!pendingFiles.containsKey(fileNumber)) {
            return;
        }
        FileEntry fileEntry = pendingFiles.get(fileNumber);

        System.out.println("Restart download of " + fileEntry.name);

        // Clear the buffer
        pendingFiles.get(fileNumber).buffer.clear();
        pendingFiles.get(fileNumber).maxBufferOffset = 0;
        List<FileDescriptor> descriptors = new ArrayList<FileDescriptor>();
        FileDescriptor descriptor = new FileDescriptor(fileEntry.file.length() / utils.KB, fileEntry.name);
        descriptors.add(descriptor);

        // Send a new Client Request for the respecting file
        network.sendMessage(new ClientRequest(getAckNumber(), new ArrayList<Option>(), TRANSMISSION_RATE,descriptors), endpoint);
    }

    public void restartDownloads() {
        // Restart the downloads of all pending files
        pendingFiles.forEach((key,value) -> restartDownload(key));
    }

    public void setFileMetadata(int fileNumber, long size, byte[] checksum) {
        if (!pendingFiles.containsKey(fileNumber)) {
            return;
        }
        FileEntry fileEntry = pendingFiles.get(fileNumber);

        System.out.println("Receive metadata for file " + fileEntry.name + "\tsize: " + size);

        // Set the file size in the file entry
        fileEntry.size = size;
        // Compare the recently received checksum with the stored checksum if existing
        if (fileEntry.checksum.length != 0) {
            // Check whether both lengths differ
            if (!utils.compareMD5(fileEntry.checksum,checksum)) {
                // Send Wrong Checksum Close Connection Message
                network.sendMessage(
                        new CloseConnection(
                                getAckNumber(),
                                new ArrayList<Option>(),
                                CloseConnection.Reason.WRONG_CHECKSUM),
                        endpoint);
                // Delete all data of the file which has been already received
                fileEntry.file.delete();
                fileEntry.size = 0;
                fileEntry.checksum = new byte[0];
                // Restart the download from scratch
                restartDownload(fileNumber);
                return;
            }
        }
        fileEntry.checksum = checksum;
    }

    public int getAckNumber() {
        if (isNewAckNumberNeeded) generateAckNumber();
        return currentAckNumber;
    }

    public Network getNetwork() {
        return network;
    }

    public Endpoint getEndpoint() {
        return endpoint;
    }
}