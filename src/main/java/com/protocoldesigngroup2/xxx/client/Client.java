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

public class Client {
    private final long TIMEOUT_INTERVAL = 3000;
    private final int TRANSMISSION_RATE = 0;
    private final int RANDOM_FILENUMBER_UPPERBOUND = 5000;

    private class FileEntry {
        File file;
        String name;
        int fileNumber;
        long maxBufferOffset;
        long size;
        long checksum;
        Map<Long,byte[]> buffer;

        public FileEntry(File file, String name, int fileNumber) {
            this.file = file;
            this.name = name;
            this.fileNumber = fileNumber;
            this.maxBufferOffset = 0L;
            this.size = 0;
            this.checksum = 0;
            this.buffer = new HashMap<Long,byte[]>();
        }
    }

    private class AckThread extends Thread {
        boolean stop = false;

        public void run() {
            while(!stop) {
                try {
                    sleep(ACK_INTERVAL);
                } catch(InterruptedException ie) {
                    ie.printStackTrace();
                }
                sendAck();
            }
        }

        public void stopRunning() {
            stop = true;
        }  
    }

    private class TimeoutThread extends Thread {
        boolean stop = false;

        long lastIncomingDataTime;

        public void reset() {
            lastIncomingDataTime = System.currentTimeMillis();
        }

        public void run() {
            reset();
            try {
                sleep(TIMEOUT_INTERVAL);
            } catch(InterruptedException ie) {
                ie.printStackTrace();
            }
            while(!stop) {
                long duration = System.currentTimeMillis() - lastIncomingDataTime;
                reset();
                if (duration > TIMEOUT_INTERVAL) {
                    try {
                        network.sendMessage(new CloseConnection(getAckNumber(),null, CloseConnection.Reason.fromId(6)), endpoint);
                    } catch (WrongIdException wie) {
                        wie.printStackTrace();
                    }
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
            stop = true;
        }
    }

    private String destinationPath;
    private Endpoint endpoint;
    private Network network;
    private AckThread ackThread;
    private TimeoutThread timeoutThread;
    private Map<Integer,FileEntry> pendingFiles = new HashMap<Integer,FileEntry>();
    private long ACK_INTERVAL = 250;
    private int currentAckNumber;
    private long rttStart;
    
    public Client(String address, int port, float p, float q) {
        this.destinationPath = "./";
        try {
            // Create an endpoint
            InetAddress inetAddress = InetAddress.getByName(address);
            this.endpoint = new Endpoint(inetAddress, port);

            this.network = Network.createClient(p,q);

            // Register callbacks
            this.network.addCallbackMethod(Type.SERVER_PAYLOAD, new ServerPayloadMessageHandler(this));
            this.network.addCallbackMethod(Type.SERVER_METADATA, new ServerMetadataMessageHandler(this));
            this.network.addCallbackMethod(Type.CLOSE_CONNECTION, new CloseConnectionMessageHandler(this));
            
            // Start the network
            this.network.listen(port);

            // Start sending acknowledgements in a predefined interval
            this.ackThread = new AckThread();
            this.ackThread.start();
            this.timeoutThread = new TimeoutThread();
            this.timeoutThread.start();
        } catch (SocketException se) {
            se.printStackTrace();
        } catch (UnknownHostException uhe) {
            uhe.printStackTrace();
        }
    }

    public void shutdown() {
        try {
            network.sendMessage(new CloseConnection(getAckNumber(),null, CloseConnection.Reason.fromId(1)), endpoint);
        } catch (WrongIdException wie) {
            wie.printStackTrace();
        }
        network.stopListening();
        deletePendingFiles();
    }

    //int fileCount = 0;
    private int generateFileNumber() {
        Random rand = new Random();
        return rand.nextInt(RANDOM_FILENUMBER_UPPERBOUND);
        //return fileCount++;
    }

    //int ackNumberCount = 0;
    private void generateAckNumber() {
        rttStart = System.currentTimeMillis();
        Random rand = new Random();
        this.currentAckNumber = rand.nextInt(RANDOM_FILENUMBER_UPPERBOUND);
        //this.currentAckNumber = ackNumberCount++;
    }

    public void receiveAckNumber(int receivedAckNumber) {
        if (receivedAckNumber == currentAckNumber) {
            long rtt = System.currentTimeMillis() - rttStart;
            ACK_INTERVAL = rtt / 4;
        }
    }

    public void download(List<String> fileInfos) {
        List<FileDescriptor> descriptors = new ArrayList<FileDescriptor>();
        for (String fileInfo : fileInfos) {
            System.out.println("Downloading file \"" + fileInfos.get(i) + "\" to " + destinationPath);

            String fileName = fileInfos.get(i);
            int fileNumber = generateFileNumber();
            // Add file entry to pending files
            pendingFiles.put(fileNumber, new FileEntry(new File(destinationPath + fileName),fileName,fileNumber));

            // Create a descriptor for the Client Request message
            FileDescriptor descriptor = new FileDescriptor(0,fileName);
            descriptors.add(descriptor);
        }
        
        // Sent the Client Request Message over the network
        network.sendMessage(new ClientRequest(getAckNumber(), new ArrayList<Option>(), TRANSMISSION_RATE, descriptors), endpoint);
    }

    private void sendAck() {
        for (Map.Entry<Integer,FileEntry> entry : pendingFiles.entrySet()) {
            FileEntry fileEntry = entry.getValue();

            if (fileEntry.buffer.isEmpty()) continue;

            // Collect all resend entries for the respecting file
            List<ResendEntry> resendEntries = new ArrayList<ResendEntry>();
            short numberOfChunks = 0;
            long resendOffset = 0;
            for (long i = fileEntry.file.length(); i < fileEntry.maxBufferOffset; i += 1024) {
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
                    System.out.println("Add resend entry:\tOffset: " + resendOffset + "\tNumber of Chunks: " + numberOfChunks);
                    
                    if (numberOfChunks > 0) {
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
            try {
                network.sendMessage(new ClientAck(getAckNumber(), null, entry.getKey(), ClientAck.Status.fromId(0), TRANSMISSION_RATE, fileEntry.maxBufferOffset + 1024, resendEntries), endpoint);
            } catch (WrongIdException wie) {
                wie.printStackTrace();
            }
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
        System.out.println("Finish download of " + fileNumber);
        // Remove the file from the pending downloads
        pendingFiles.remove(fileNumber);

        if (pendingFiles.isEmpty()) {
            // Send an Finish Download Close Connection Message and stop sending Client Ack messages
            // if all downloads are finished
            try {
                network.sendMessage(
                    new CloseConnection(
                        getAckNumber(),
                        new ArrayList<Option>(),
                        CloseConnection.Reason.DOWNLOAD_FINISHED),
                    endpoint);
            } catch (WrongIdException wie) {
                wie.printStackTrace();
            }
            ackThread.stopRunning();
            timeoutThread.stopRunning();
        }
    }

    private void writeBufferToDisk(FileEntry fileEntry) {
        // Check whether the metadata message has already been received
        if (fileEntry.size == 0) {
            return;
        }

        // Check if the file has been fully downloaded
        if (fileEntry.file.length() == fileEntry.size) {
            finishDownload(fileEntry.fileNumber);
            return;
        }

        // Abort if buffer is empty, 
        // file does not exist or
        // the buffer does not contain the chunk at the next offset
        if (fileEntry.buffer.isEmpty() 
            || !fileEntry.file.exists()
            || !fileEntry.buffer.containsKey(fileEntry.file.length())) {
            return;
        }

        try {
            System.out.println("Write chunk for offset " + fileEntry.file.length() + " to file");
            // Open output stream and write chunk to disk and remove it from the buffer
            FileOutputStream fileOut = new FileOutputStream(fileEntry.file, true);
            long offset = fileEntry.file.length();
            fileOut.write(fileEntry.buffer.get(offset));
            fileOut.flush();
            fileOut.close();
            fileEntry.buffer.remove(offset);
            // Continue with the next offset
            writeBufferToDisk(fileEntry);
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    public void writeChunkToFile(int fileNumber, long offset, byte[] data) {
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

        System.out.println("Add chunk for offset " + offset + " to buffer");
        // Write chunk to buffer and update maxBufferOffset if needed
        fileEntry.buffer.put(offset, Arrays.copyOf(data,data.length));
        fileEntry.maxBufferOffset = Math.max(fileEntry.maxBufferOffset, offset);
        
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
        FileDescriptor descriptor = new FileDescriptor(fileEntry.file.length(), fileEntry.name);
        descriptors.add(descriptor);

        // Send a new Client Request for the respecting file
        network.sendMessage(new ClientRequest(getAckNumber(), new ArrayList<Option>(), TRANSMISSION_RATE,descriptors), endpoint);
    }

    public void restartDownloads() {
        // Restart the downloads of all pending files
        pendingFiles.forEach((key,value) -> restartDownload(key));
    }

    public void setFileMetadata(int fileNumber, long size, long checksum) {
        if (!pendingFiles.containsKey(fileNumber)) {
            return;
        }
        FileEntry fileEntry = pendingFiles.get(fileNumber);

        System.out.println("Receive metadata for file " + fileEntry.name + "\tsize: " + size);

        // Set the file size in the file entry
        fileEntry.size = size;
        // Compare the recently received checksum with the stored checksum if existing
        if (fileEntry.checksum != 0) {
            // Check whether both lengths differ
            if (fileEntry.checksum != checksum) {
                // Send Wrong Checksum Close Connection Message
                try {
                    network.sendMessage(
                        new CloseConnection(
                            getAckNumber(),
                            new ArrayList<Option>(),
                            CloseConnection.Reason.WRONG_CHECKSUM),
                        endpoint);
                } catch (WrongIdException wie) {
                    wie.printStackTrace();
                }
                // Delete all data of the file which has been already received
                fileEntry.file.delete();
                fileEntry.size = 0;
                fileEntry.checksum = 0;
                // Restart the download from scratch
                restartDownload(fileNumber);
                return;
            }
        }
        fileEntry.checksum = checksum;
    }

    public int getAckNumber() {
        return currentAckNumber;
    }

    public Network getNetwork() {
        return network;
    }

    public Endpoint getEntpoint() {
        return endpoint;
    }
}