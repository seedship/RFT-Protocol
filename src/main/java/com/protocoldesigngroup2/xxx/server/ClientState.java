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

import static java.lang.Math.max;


public class ClientState {
    // Factor used to update rolling average RTT
    private static final double MOVING_AVERAGE_WEIGHT = 0.2;

    // Number of resend entries before decrementing transmission
    private static final int NUM_WAIT_BEFORE_RESEND = 4;

    // Initial transmission speed in packets/s
    private static final long INITIAL_TRANSMISSION_SPEED = 20;

    // Time to wait for first ack
    private static final long INITIAL_WAIT_TIME = 5000L;

    public final List<ClientRequest.FileDescriptor> files;
    private long maximumTransmissionSpeed;

    private int currentFile;
    private long currentOffset;

    private long lastReceivedAckMS;
    private int lastReceivedAckNum;

    private long transmissionSpeed;

    // Boolean flag used to check if calculated RTT should set estimated RTT or only used to average
    private boolean calculatedRTT;
    private long estimatedRttMs;

    // For simplicity, we will keep track of each missing index rather than start and offset
    public final Map<Integer, Set<Long>> missingChunks;

    public final Map<Integer, Boolean> sentMetadata;

    private int receivedDups; // Counter to keep track of x4 ACKs

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
        calculatedRTT = false;
        estimatedRttMs = INITIAL_WAIT_TIME;
        for (int idx = 0; idx < files.size(); idx++) {
            sentMetadata.put(idx, false);
        }
        transmissionSpeed = INITIAL_TRANSMISSION_SPEED;
        receivedDups = 0;
        currentFile = 0;
        currentOffset = files.get(0).offset;
        utils.printDebug("Adding client state with file hash: " + files.hashCode());
    }

    public RandomAccessFile getFileAccess(int index) {
        if(fileAccess.containsKey(index)) {
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

    public long getEstimatedRttMs() {
        return estimatedRttMs;
    }

    public void updateLastReceivedAck(int ackNum) {
        // According to spec, client should send ack every RTT/4
        long rtt = 4 * (System.currentTimeMillis() - lastReceivedAckMS);
        if (!calculatedRTT) {
            estimatedRttMs = rtt;
            calculatedRTT = true;
        } else {
            estimatedRttMs = (long) ((1 - MOVING_AVERAGE_WEIGHT) * estimatedRttMs + MOVING_AVERAGE_WEIGHT * rtt);
        }
        lastReceivedAckMS = System.currentTimeMillis();
        lastReceivedAckNum = ackNum;
    }

    public boolean checkResendAndDecrement() {
        if (receivedDups == 0) {
            receivedDups = NUM_WAIT_BEFORE_RESEND;
            return true;
        } else {
            receivedDups--;
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

    public void resetResendCounter() {
        receivedDups = NUM_WAIT_BEFORE_RESEND;
    }

    public void increaseRate() {
        transmissionSpeed++;
        if (maximumTransmissionSpeed > 0) {
            transmissionSpeed = max(maximumTransmissionSpeed, transmissionSpeed);
        }
        utils.printDebug("Increasing Transmission speed to: " + transmissionSpeed);
    }

    public void decreaseRate() {
        transmissionSpeed = max(1, transmissionSpeed / 2);
        utils.printDebug("Decreasing Transmission speed to: " + transmissionSpeed);
    }

    public long getTransmissionSpeed() {
        return transmissionSpeed;
    }
}
