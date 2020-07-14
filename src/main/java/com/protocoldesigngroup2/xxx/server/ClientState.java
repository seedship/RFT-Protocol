package com.protocoldesigngroup2.xxx.server;

import java.util.List;

public class ClientState {

    public ClientState(List<String> files) {
        this.files = files;
        receivedDups = 0;
    }

    public boolean uniqueResend() {
        if(receivedDups == 0) {
            receivedDups = 4;
            return true;
        } else {
            receivedDups--;
            return false;
        }
    }

    public final List<String> files;

    public int currentFile;
    public long currentOffset;
    public int transmissionSpeed;
    public int maximumTransmissionSpeed;

    private int receivedDups; // Counter to keep track of x4 ACKs
}
