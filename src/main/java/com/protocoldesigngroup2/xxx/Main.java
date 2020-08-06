package com.protocoldesigngroup2.xxx;

import com.protocoldesigngroup2.xxx.client.Client;
import com.protocoldesigngroup2.xxx.server.Server;
import com.protocoldesigngroup2.xxx.utils.utils;

public class Main {
    public static void main(String[] args) {
        Arguments arg;
        try {
            arg = Arguments.parse(args);
        } catch (Exception e) {
            System.out.println(Arguments.getHelp());
            return;
        }
        utils.setDebug(arg.isDebug());
        if (arg.isServer()) {
            Server server = new Server(arg.getP(), arg.getQ(), arg.getPort());
            server.start();
        } else {
            Client client = new Client(arg.getHostname(), arg.getPort(), arg.getP(), arg.getQ());
            client.download(arg.getFilenames());
        }
    }
}
