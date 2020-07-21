package com.protocoldesigngroup2.xxx;


import com.protocoldesigngroup2.xxx.client.Client;

public class Main {
    public static void main(String[] args) {
        Arguments arg;
        try {
            arg = Arguments.parse(args);
        } catch (Exception e) {
            System.out.println(Arguments.getHelp());
            return;
        }
        if (arg.isServer()) {
            // TODO: Add server init here
        } else {
            Client client = new Client(arg.getHostname(),arg.getPort(), arg.getP(), arg.getQ());
            client.download(arg.getFilenames());
        }
    }
}
