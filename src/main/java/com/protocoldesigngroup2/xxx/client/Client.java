package com.protocoldesigngroup2.xxx.client;

public class Client {
    String downloadPath;
    
    public Client(String downloadPath) {
        this.downloadPath = downloadPath;
    }

    public void download(String address, int port, String... names) {
        System.out.println("Downloading file from " + address + ":" + port + " to " + downloadPath);
    }
}