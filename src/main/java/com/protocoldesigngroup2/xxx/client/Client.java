package com.protocoldesigngroup2.xxx.client;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import com.protocoldesigngroup2.xxx.messages.*;
import com.protocoldesigngroup2.xxx.messages.ClientAck.ResendEntry;
import com.protocoldesigngroup2.xxx.messages.ClientRequest.FileDescriptor;
import com.protocoldesigngroup2.xxx.messages.Message.Type;
import com.protocoldesigngroup2.xxx.network.*;
import com.protocoldesigngroup2.xxx.utils.utils;

public class Client {
    private final int TRANSMISSION_RATE = 0;
    // Send every rtt one client ack message
    private final int RTT_DIVIDER = 1;
    private final String DESTINATION_PATH = "./download/";

    private class FileEntry {
        File file;
        String name;
        int fileNumber;
        long maxBufferOffset;
        long size;
        byte[] checksum;
        Map<Long, byte[]> buffer;

        public FileEntry(File file, String name, int fileNumber) {
            this.file = file;
            this.name = name;
            this.fileNumber = fileNumber;
            this.maxBufferOffset = 0L;
            this.size = 0;
            this.checksum = new byte[0];
            this.buffer = new ConcurrentHashMap<Long, byte[]>();
        }
    }

    private class AckThread extends Thread {
        private boolean running;

        public void run() {
            running = true;
            while(running) {
                try {
                    sleep(ackInterval);
                } catch(InterruptedException ie) {
                    utils.printDebug("AckThread interrupted");
                }
                sendAck();
            }
        }

        public void stopRunning() {
            running = false;
            this.interrupt();
        }
    }

    private class TimeoutThread extends Thread {
        private boolean running;
        private long lastIncomingDataTime;

        public void reset() {
            lastIncomingDataTime = System.currentTimeMillis();
        }

        public void run() {
            reset();
            running = true;
            while (running) {
                long durationWithoutData = System.currentTimeMillis() - lastIncomingDataTime;
                if (durationWithoutData > timeoutInterval) {
                    utils.printDebug("Timeout");

                    network.sendMessage(
                            new CloseConnection(
                                getAckNumber(),
                                new ArrayList<>(),
                                CloseConnection.Reason.TIMEOUT),
                            endpoint);
                    restartDownloads();
                    reset();
                    continue;
                }
                try {
                    sleep(timeoutInterval - durationWithoutData);
                } catch (InterruptedException ie) {
                    utils.printDebug("TimeoutThread interrupted");
                }
            }
        }

        public void stopRunning() {
            running = false;
            this.interrupt();
        }
    }

    private String destinationPath;
    private Endpoint endpoint;
    private Network network;
    private AckThread ackThread;
    private TimeoutThread timeoutThread;
    private Map<Integer, FileEntry> pendingFiles;
    private long ackInterval;
    private long timeoutInterval;
    private int currentAckNumber;
    private long rttStart;
    private int rttAckNumber;
    private int fileCount;
    private boolean isNewAckNumberNeeded;
    private boolean isServerResponding;
    private float p;
    private float q;

    public Client(String address, int port, float p, float q) {
        this.destinationPath = DESTINATION_PATH;
        this.pendingFiles = new ConcurrentHashMap<Integer, FileEntry>();
        this.isNewAckNumberNeeded = true;
        this.fileCount = 0;
        this.ackInterval = 250;
        this.timeoutInterval = 3000;
        this.currentAckNumber = 0;
        this.p = p;
        this.q = q;
        this.isServerResponding = false;

        // Creates download directory if needed
        new File(this.destinationPath).mkdirs();

        try {
            // Create an endpoint
            this.endpoint = new Endpoint(address, port);
            createNetwork();

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

    private void createNetwork() {
        this.network = Network.createClient(p, q);

        if (network == null) {
            return;
        }

        // Register callbacks
        this.network.addCallbackMethod(Type.SERVER_PAYLOAD, new ServerPayloadMessageHandler(this));
        this.network.addCallbackMethod(Type.SERVER_METADATA, new ServerMetadataMessageHandler(this));
        this.network.addCallbackMethod(Type.CLOSE_CONNECTION, new CloseConnectionMessageHandler(this));
    }

    public void serverResponds() {
        this.isServerResponding = true;
    }

    private void stopThreads() {
        ackThread.stopRunning();
        timeoutThread.stopRunning();
        network.stopListening();
        utils.printDebug("Done");
    }

    public void shutdown() {
        if (network != null) {
            network.sendMessage(
                    new CloseConnection(
                            getAckNumber(),
                            new ArrayList<>(),
                            CloseConnection.Reason.UNSPECIFIED),
                    endpoint);
            network.stopListening();
        }
        deletePendingFiles();
    }

    private int generateFileNumber() {
        return fileCount++;
    }

    private void rememberAckNumber() {
        rttStart = System.currentTimeMillis();
        this.rttAckNumber = currentAckNumber;
        isNewAckNumberNeeded = false;
    }

    public void receiveAckNumber(int receivedAckNumber) {
        if (receivedAckNumber == rttAckNumber) {
            long rtt = System.currentTimeMillis() - rttStart;
            ackInterval = rtt / RTT_DIVIDER;
            ackInterval = Math.max(Math.min(ackInterval, 500), 1);
            isNewAckNumberNeeded = true;
        }
    }

    public void download(List<String> fileInfos) {
        if (network == null) {
            return;
        }
        List<FileDescriptor> descriptors = new ArrayList<>();
        for (String fileInfo : fileInfos) {
            System.out.println("Downloading file \"" + fileInfo + "\" to " + destinationPath);

            String fileName = fileInfo;
            int fileNumber = generateFileNumber();
            // Add file entry to pending files
            File file = new File(destinationPath + fileName);
            file.delete();
            pendingFiles.put(fileNumber, new FileEntry(file, fileName, fileNumber));

            // Create a descriptor for the Client Request message
            FileDescriptor descriptor = new FileDescriptor(0, fileName);
            descriptors.add(descriptor);
        }

        // Sent the Client Request Message over the network
        network.sendMessage(new ClientRequest(getAckNumber(), new ArrayList<>(), TRANSMISSION_RATE, descriptors), endpoint);
    }

    private void sendAck() {
        int highestFileNumber = 0;
        long highestOffset = 0;
        boolean metadataMissing = false;

        // Collect all resend entries for the respecting file
        List<ResendEntry> resendEntries = new ArrayList<>();

        // If the client did not received the metadata of a file but already receives
        // the payload of a file with larger file number it should add a resend entry
        // which specifies the highest of set of the file with 0 length
        ResendEntry lastResendEntry = null;

        for (Map.Entry<Integer, FileEntry> entry : pendingFiles.entrySet()) {
            FileEntry fileEntry = entry.getValue();

            // If the metadata of one file is missing then set the corresponding status bit in the client ack message later on
            metadataMissing = metadataMissing || fileEntry.size == 0;

            if (fileEntry.maxBufferOffset > 0) {
                // Add the resend Entry which indicates a pending download of the last visited file to the resendEntries
                if (lastResendEntry != null) resendEntries.add(lastResendEntry);
                lastResendEntry = null;
                // Update highest file number and highest offset to current file status
                highestFileNumber = entry.getKey();
                highestOffset = fileEntry.maxBufferOffset + 1;
            }

            short numberOfChunks = 0;
            long resendOffset = 0;
            for (long i = fileEntry.file.length() / utils.KB; i < fileEntry.maxBufferOffset; i++) {
                // Check whether the chunk for the offset exists in the buffer
                if (!fileEntry.buffer.containsKey(i)) {
                    // If it is the first chunk in the continuous sequence of unreceived offsets
                    // set the this offset as the resendOffset of the resend entry
                    if (resendOffset == 0) {
                        resendOffset = i;
                    }
                    // Increment the length of the continuous sequence of unreceived offsets
                    numberOfChunks++;
                } else {
                    if (numberOfChunks > 0) {
                        if (numberOfChunks >= 256) {
                            numberOfChunks = 255;
                        }
                        utils.printDebug("Add resend entry:\tOffset: " + resendOffset + "\tNumber of Chunks: " + numberOfChunks);
                        // If there is a chunk for the offset build a resend entry for the preceding sequence of unreceived offsets
                        resendEntries.add(new ResendEntry(entry.getKey(), resendOffset, numberOfChunks));
                    }

                    // Reset the sequence collection
                    resendOffset = 0;
                    numberOfChunks = 0;
                }
            }
            if (fileEntry.size == 0) {
                lastResendEntry = new ResendEntry(entry.getKey(), fileEntry.maxBufferOffset + 1, (short) 0);
            }
        }

        utils.printDebug("Send Client Ack message");
        // Send the Client Ack Message over the network
        network.sendMessage(
                new ClientAck(
                        getAckNumber(),
                        new ArrayList<>(),
                        highestFileNumber,
                        (metadataMissing) ?
                                ClientAck.Status.NO_METADATA_RECEIVED :
                                ClientAck.Status.NOTHING,
                        TRANSMISSION_RATE,
                        highestOffset,
                        resendEntries),
                endpoint);
    }

    public void deletePendingFiles() {
        // Delete the data of all pending files
        pendingFiles.forEach((key, value) -> deletePendingFile(key));
    }

    public void deletePendingFile(int fileNumber) {
        if (!pendingFiles.containsKey(fileNumber)) {
            return;
        }
        FileEntry fileEntry = pendingFiles.get(fileNumber);

        utils.printDebug("Delete pending file " + fileEntry.name);
        // Delete the file and remove the file entry from the pending files
        fileEntry.file.delete();
        pendingFiles.remove(fileNumber);

        if (pendingFiles.size() <= 0) {
            stopThreads();
        }
    }

    private void finishDownload(int fileNumber) {
        FileEntry fileEntry = pendingFiles.get(fileNumber);
        if (!Arrays.equals(fileEntry.checksum, utils.generateMD5(destinationPath + fileEntry.name))) {
            System.out.println("Wrong Checksum!");
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
                            new ArrayList<>(),
                            CloseConnection.Reason.DOWNLOAD_FINISHED),
                    endpoint);
            stopThreads();
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
            utils.printDebug("Write chunk for offset " + chunkOffset + " to file");
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
                utils.printDebug("Create new file " + fileEntry.name);
                fileEntry.file.createNewFile();
            } catch (IOException ioe) {
                ioe.printStackTrace();
            } catch (SecurityException se) {
                se.printStackTrace();
            }
        }

        utils.printDebug("Add chunk for offset " + chunkOffset + " to buffer");
        // Write chunk to buffer and update maxBufferOffset if needed
        long bytesToWrite = utils.KB;
        if (fileEntry.size != 0) {
            bytesToWrite = Math.min(bytesToWrite, fileEntry.size - chunkOffset);
        }
        fileEntry.buffer.put(chunkOffset, Arrays.copyOf(data, data.length));
        fileEntry.maxBufferOffset = Math.max(fileEntry.maxBufferOffset, chunkOffset);

        // Check if a continuous range of chunks has been received at current offset
        // and write them to disk if so
        writeBufferToDisk(fileEntry);
    }

    private void restartDownload(int fileNumber) {
        if (!pendingFiles.containsKey(fileNumber)) {
            return;
        }
        FileEntry fileEntry = pendingFiles.get(fileNumber);

        utils.printDebug("Restart download of " + fileEntry.name);

        // Clear the buffer
        pendingFiles.get(fileNumber).buffer.clear();
        pendingFiles.get(fileNumber).maxBufferOffset = 0;
        List<FileDescriptor> descriptors = new ArrayList<>();
        FileDescriptor descriptor = new FileDescriptor(fileEntry.file.length() / utils.KB, fileEntry.name);
        descriptors.add(descriptor);

        // Send a new Client Request for the respecting file
        network.sendMessage(new ClientRequest(getAckNumber(), new ArrayList<>(), TRANSMISSION_RATE, descriptors), endpoint);
    }

    public void restartDownloads() {
        if (!isServerResponding) {
            timeoutInterval *= 2;
            createNetwork();
        }
        // Restart the downloads of all pending files
        pendingFiles.forEach((key, value) -> restartDownload(key));
    }

    public void setFileMetadata(int fileNumber, long size, byte[] checksum) {
        if (!pendingFiles.containsKey(fileNumber)) {
            return;
        }
        FileEntry fileEntry = pendingFiles.get(fileNumber);

        utils.printDebug("Receive metadata for file " + fileEntry.name + "\tsize: " + size);

        // Set the file size in the file entry
        fileEntry.size = size;
        // Compare the recently received checksum with the stored checksum if existing
        if (fileEntry.checksum.length != 0) {
            // Check whether both lengths differ
            if (!Arrays.equals(fileEntry.checksum, checksum)) {
                // Send Wrong Checksum Close Connection Message
                network.sendMessage(
                        new CloseConnection(
                                getAckNumber(),
                                new ArrayList<>(),
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

        writeBufferToDisk(fileEntry);
    }

    public int getAckNumber() {
        currentAckNumber = (currentAckNumber + 1) % 256;
        if (isNewAckNumberNeeded) rememberAckNumber();
        return currentAckNumber;
    }

    public Network getNetwork() {
        return network;
    }

    public Endpoint getEndpoint() {
        return endpoint;
    }
}