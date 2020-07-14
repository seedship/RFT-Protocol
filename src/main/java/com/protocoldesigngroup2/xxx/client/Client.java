package com.protocoldesigngroup2.xxx.client;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.protocoldesigngroup2.xxx.messages.ClientAck;
import com.protocoldesigngroup2.xxx.messages.ClientRequest;
import com.protocoldesigngroup2.xxx.messages.CloseConnection;
import com.protocoldesigngroup2.xxx.messages.ClientAck.ResendEntry;
import com.protocoldesigngroup2.xxx.messages.ClientRequest.Descriptor;
import com.protocoldesigngroup2.xxx.messages.Message.Type;
import com.protocoldesigngroup2.xxx.network.Endpoint;
import com.protocoldesigngroup2.xxx.network.Network;

public class Client {
    final long ACK_INTERVAL = 3000;
    final int TRANSMISSION_RATE = 5;
    final int RANDOM_FILENUMBER_UPPERBOUND = 5000;

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
    
    String destinationPath;
    Endpoint endpoint;
    Network network;
    AckThread ackThread;
    Map<Integer,FileEntry> pendingFiles = new HashMap<Integer,FileEntry>();
    
    public Client(String destinationPath, String address, int port) {
        this.destinationPath = destinationPath;
        try {
            // Create an endpoint
            InetAddress inetAddress = InetAddress.getByName(address);
            this.endpoint = new Endpoint(inetAddress, port);

            this.network = new Network();

            // Register callbacks
            this.network.addCallbackMethod(Type.SERVER_PAYLOAD, new ServerPayloadMessageHandler(this));
            this.network.addCallbackMethod(Type.SERVER_METADATA, new ServerMetadataMessageHandler(this));
            this.network.addCallbackMethod(Type.CLOSE_CONNECTION, new CloseConnectionMessageHandler(this));
            
            // Start the network
            //this.network.start();

            // Start sending acknowledgements in a predefined interval
            this.ackThread = new AckThread();
            this.ackThread.start();
        } catch (SocketException se) {
            se.printStackTrace();
        } catch (UnknownHostException uhe) {
            uhe.printStackTrace();
        }
    }

    int fileCount = 0;
    private int generateFileNumber() {
        //Random rand = new Random();
        //return rand.nextInt(RANDOM_FILENUMBER_UPPERBOUND); 
        return fileCount++;
    }

    public void download(String... fileInfos) {
        Descriptor[] descriptors = new Descriptor[fileInfos.length];
        for (int i = 0; i<fileInfos.length; i++) {
            System.out.println("Downloading file \"" + fileInfos[i] + "\" to " + destinationPath);

            String fileName = fileInfos[i];
            int fileNumber = generateFileNumber();
            // Add file entry to pending files
            pendingFiles.put(fileNumber, new FileEntry(new File(destinationPath + fileName),fileName,fileNumber));

            // Create a descriptor for the Client Request message
            Descriptor descriptor = new Descriptor(fileName,0);
            descriptors[i] = descriptor;
        }
        
        // Sent the Client Request Message over the network
        network.sendMessage(new ClientRequest(5,descriptors), endpoint);
    }

    private void sendAck() {
        for (Map.Entry<Integer,FileEntry> entry : pendingFiles.entrySet()) {
            FileEntry fileEntry = entry.getValue();

            if (fileEntry.buffer.isEmpty()) continue;

            // Collect all resend entries for the respecting file
            List<ResendEntry> resendEntries = new ArrayList<ResendEntry>();
            int numberOfChunks = 0;
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
            network.sendMessage(new ClientAck(entry.getKey(), 5, fileEntry.maxBufferOffset + 1024, resendEntries.toArray(new ResendEntry[0])), endpoint);
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
            network.sendMessage(new CloseConnection(5), endpoint);
            ackThread.stopRunning();
        }
    }

    private void writeBufferToDisk(FileEntry fileEntry, FileOutputStream fileOut) {
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
            fileOut.write(fileEntry.buffer.get(fileEntry.file.length()));
            fileOut.flush();
            fileEntry.buffer.remove(fileEntry.file.length());
            // Continue with the next offset
            writeBufferToDisk(fileEntry,fileOut);
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    public void writeChunkToFile(int fileNumber, long offset, byte[] data) {
        if (!pendingFiles.containsKey(fileNumber)) {
            return;
        }
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
        fileEntry.buffer.put(offset, data);
        fileEntry.maxBufferOffset = Math.max(fileEntry.maxBufferOffset, offset);
        
        // Check if a continous range of chunks has been received at current offset
        // and write them to disk if so
        try {
            FileOutputStream fileOut = new FileOutputStream(fileEntry.file,true);
            writeBufferToDisk(fileEntry,fileOut);
            fileOut.close();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    private void restartDownload(int fileNumber) {
        if (!pendingFiles.containsKey(fileNumber)) {
            return;
        }
        FileEntry fileEntry = pendingFiles.get(fileNumber);

        System.out.println("Restart download of " + fileEntry.name);

        // Clear the buffer
        pendingFiles.get(fileNumber).buffer = new HashMap<Long,byte[]>();
        Descriptor[] descriptors = new Descriptor[1];
        descriptors[0] = new Descriptor(fileEntry.name,fileEntry.file.length());

        // Send a new Client Request for the respecting file
        network.sendMessage(new ClientRequest(5,descriptors), endpoint);
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
            if (fileEntry.checksum.length != checksum.length) {
                // Send Wrong Checksum Close Connection Message
                network.sendMessage(new CloseConnection(4), endpoint);
                // Delete all data of the file which has been already received
                fileEntry.file.delete();
                fileEntry.size = 0;
                fileEntry.checksum = new byte[0];
                // Restart the download from scratch
                restartDownload(fileNumber);
                return;
            }
            // Check whether checksums differ in their values
            for (int i=0; i<Math.min(fileEntry.checksum.length,checksum.length);i++) {
                if (fileEntry.checksum[i] != checksum[i]) {
                    // Send Wrong Checksum Close Connection Message
                    network.sendMessage(new CloseConnection(4), endpoint);
                    // Delete all data of the file which has been already received
                    fileEntry.file.delete();
                    fileEntry.size = 0;
                    fileEntry.checksum = new byte[0];
                    // Restart the download from scratch
                    restartDownload(fileNumber);
                    return;
                }
            }
        }
        fileEntry.checksum = checksum;
    }

    public Network getNetwork() {
        return network;
    }

    public Endpoint getEntpoint() {
        return endpoint;
    }
}