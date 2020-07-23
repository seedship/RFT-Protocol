package com.protocoldesigngroup2.xxx.server;

import com.protocoldesigngroup2.xxx.messages.ClientRequest;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static java.lang.Math.max;


public class ClientState {

    private static final int NUM_WAIT_BEFORE_RESEND = 4;
    private static final long INITIAL_TRANSMISSION_SPEED = 100;

    public final List<ClientRequest.FileDescriptor> files;
    private long maximumTransmissionSpeed;

    private int currentFile;
    private long currentOffset;

    private long lastReceivedAckNS;
    private int lastReceivedAckNum;

    private long transmissionSpeed;

    //For simplicity, we will keep track of each missing index rather than start and offset
    public final Map<Integer, Set<Long>> missingChunks;

    public final Map<Integer, Boolean> sentMetadata;

    private int receivedDups; // Counter to keep track of x4 ACKs

    public ClientState(List<ClientRequest.FileDescriptor> files, long maximumTransmissionSpeed,
                       long lastReceivedAckNS, int lastReceivedAckNum) {
        this.files = files;
        this.maximumTransmissionSpeed = maximumTransmissionSpeed;
        this.lastReceivedAckNS = lastReceivedAckNS;
        this.lastReceivedAckNum = lastReceivedAckNum;
        missingChunks = new ConcurrentHashMap<>();
        sentMetadata = new ConcurrentHashMap<>();
        for (int idx = 0; idx < files.size(); idx++) {
            sentMetadata.put(idx, false);
        }
        transmissionSpeed = INITIAL_TRANSMISSION_SPEED;
        receivedDups = 0;
        currentFile = 0;
        currentOffset = files.get(0).offset;
    }

    public long getLastReceivedAckNS() {
        return lastReceivedAckNS;
    }

    public void updateLastReceivedAck(int ackNum) {
        lastReceivedAckNS = java.time.LocalTime.now().toNanoOfDay();
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

    //Increments the current file and sets the offset if next file is valid
    public void incrementCurrentFile() {
        currentFile++;
        if (currentOffset < files.size()) {
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
    }

    public void decreaseRate() {
        transmissionSpeed = max(1, transmissionSpeed / 2);
    }

    public long getTransmissionSpeed() {
        return transmissionSpeed;
    }
}
