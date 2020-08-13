package com.protocoldesigngroup2.xxx.server;

import com.protocoldesigngroup2.xxx.messages.ClientRequest;
import com.protocoldesigngroup2.xxx.utils.utils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;


public class ClientState {
    // Initial transmission speed in packets/s
    private static final long INITIAL_TRANSMISSION_SPEED = 1000;

    public final List<ClientRequest.FileDescriptor> files;
    private long maximumTransmissionSpeed;

    private int currentFile;
    private long currentOffset;

    private long currentAckOffset;
    private long lastAckOffset;

    private long lastReceivedAckMS;
    private int lastReceivedAckNum;

    private long transmissionSpeed;

    // For simplicity, we will keep track of each missing index rather than start and offset
    public final Map<Integer, Set<Long>> missingChunks;

    public final Map<Integer, Boolean> sentMetadata;

    private final Map<Integer, Optional<RandomAccessFile>> fileAccess;

    public ClientState(List<ClientRequest.FileDescriptor> files, long maximumTransmissionSpeed,
                       long lastReceivedAckMS, int lastReceivedAckNum) {
        this.files = files;
        this.maximumTransmissionSpeed = maximumTransmissionSpeed;
        this.lastReceivedAckMS = lastReceivedAckMS;
        this.lastReceivedAckNum = lastReceivedAckNum;
        missingChunks = new ConcurrentHashMap<>();
        sentMetadata = new ConcurrentHashMap<>();
        fileAccess = new ConcurrentHashMap<>();
        for (int idx = 0; idx < files.size(); idx++) {
            sentMetadata.put(idx, false);
        }
        transmissionSpeed = INITIAL_TRANSMISSION_SPEED;
        currentFile = 0;
        currentOffset = files.get(0).offset;

        currentAckOffset = 0;
        lastAckOffset = 0;

        utils.printDebug("Adding client state with file hash: " + files.hashCode());
    }

    public RandomAccessFile getFileAccess(int index) {
        if (fileAccess.containsKey(index)) {
            Optional<RandomAccessFile> f = fileAccess.get(index);
            return f.orElse(null);
        }
        try {
            RandomAccessFile f = new RandomAccessFile(files.get(index).filename, "r");
            fileAccess.put(index, Optional.of(f));
            return f;
        } catch (FileNotFoundException ex) {
            // File does not exist or is directory
            fileAccess.put(index, Optional.empty());
            return null;
        }
    }

    public void closeAllFiles() {
        utils.printDebug("Closing all files in client state with file hash: " + files.hashCode());
        for (Optional<RandomAccessFile> f : fileAccess.values()) {
            if (f.isPresent()) {
                try {
                    f.get().close();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        }
        fileAccess.clear();
    }

    public long getLastReceivedAckMS() {
        return lastReceivedAckMS;
    }

    public void updateLastReceivedAck(int ackNum, long offset) {
        lastAckOffset = currentAckOffset;
        currentAckOffset = offset;
        lastReceivedAckMS = System.currentTimeMillis();
        lastReceivedAckNum = ackNum;
    }

    // returns true if offset was moved back
    public boolean backtraceClient() {
        if (lastAckOffset == currentAckOffset) {
            currentOffset = currentAckOffset;
            return true;
        } else {
            return false;
        }
    }

    public void incrementCurrentFileOffset() {
        currentOffset++;
    }

    // Increments the current file and sets the offset if next file is valid
    public void incrementCurrentFile() {
        currentFile++;
        if (currentFile < files.size()) {
            currentOffset = files.get(currentFile).offset;
        }
    }

    public int getCurrentFile() {
        return currentFile;
    }

    public long getCurrentOffset() {
        return currentOffset;
    }

    public int getLastReceivedAckNum() {
        return lastReceivedAckNum;
    }

    public void updateClientMaxTransmissionSpeed(long speed) {
        maximumTransmissionSpeed = speed;
    }

    public void increaseRate() {
        transmissionSpeed = Math.min(((long) (Integer.MAX_VALUE) << 1) + 1, (long) (transmissionSpeed * 1.5));
        if (maximumTransmissionSpeed > 0) {
            transmissionSpeed = Math.min(maximumTransmissionSpeed, transmissionSpeed);
        }
        utils.printDebug("Increasing Transmission speed to: " + transmissionSpeed);
    }

    public void decreaseRate() {
        transmissionSpeed = Math.max(2L, transmissionSpeed / 2);
        utils.printDebug("Decreasing Transmission speed to: " + transmissionSpeed);
    }

    public long getTransmissionSpeed() {
        return transmissionSpeed;
    }
}
