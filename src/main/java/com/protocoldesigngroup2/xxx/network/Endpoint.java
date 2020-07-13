package com.protocoldesigngroup2.xxx.network;

import java.net.InetAddress;

public class Endpoint {
    private int port;
    private InetAddress addr;
    
    public int getPort() {
        return port;
    }

    public InetAddress getAddress() {
        return addr;
    }
}

