package com.protocoldesigngroup2.xxx.network;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Objects;


public class Endpoint {
    private InetAddress addr;
    private int port;

    public Endpoint(String hostname, int port) throws UnknownHostException {
        this.addr = InetAddress.getByName(hostname);
        this.port = port;
    }

    public Endpoint(InetAddress addr, int port) {
        this.addr = addr;
        this.port = port;
    }
    
    public int getPort() {
        return port;
    }

    public InetAddress getAddress() {
        return addr;
    }

    public int hashCode() {
        return Objects.hash(port);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Endpoint)) {
            return false;
        }
        Endpoint endpoint = (Endpoint) o;
        boolean result = endpoint.addr.hashCode() == endpoint.addr.hashCode();
        return result && endpoint.port == this.port;
    }
}

