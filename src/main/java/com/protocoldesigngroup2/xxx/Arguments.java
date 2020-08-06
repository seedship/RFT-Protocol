package com.protocoldesigngroup2.xxx;

import java.util.List;
import java.util.ArrayList;


public class Arguments {
    
    public static final int DEFAULT_PORT = 1234;

    private boolean server;
    private boolean debug;
    private int port;
    private float p, q;
    private String hostname;
    private List<String> filenames;

    private Arguments() {
        server = true;
        debug = false;
        port = DEFAULT_PORT;
        p = -1.0f;
        q = -1.0f;
        hostname = "";
        filenames = new ArrayList<>();
    }

    public static Arguments parse(String[] args) {
        if (args.length < 1) {
            throw new RuntimeException("Must have at least 1 arguments");
        }
        Arguments args2 = new Arguments();
        if (!args[0].equals("-s")) {
            args2.server = false;
            args2.hostname = args[0];
        }
        for (int i = 1; i < args.length; i++) {
            if (args[i].equals("-p") && i + 1 < args.length) {
                args2.p = Math.min(1.0f, Math.max(0.0f, Float.parseFloat(args[i + 1])));
                i++;
                continue;
            } else if (args[i].equals("-q") && i + 1 < args.length) {
                args2.q = Math.min(1.0f, Math.max(0.0f, Float.parseFloat(args[i + 1])));
                i++;
                continue;
            } else if (args[i].equals("-t") && i + 1 < args.length) {
                args2.port = Math.min(65535, Math.max(1, Integer.parseInt(args[i + 1])));
                i++;
                continue;
            } else if (args[i].equals("-d")) {
                args2.debug = true;
            } else if (!args2.isServer()) {
                // it must be a file
                args2.filenames.add(args[i]);
            }
        }

        // Setting p and q
        if (args2.p < 0) {
            if (args2.q < 0) {
                // If both are not specified, no packets should get lost
                args2.p = 0;
                args2.q = 0;
            } else {
                // If only one is specified, the other one should be the same
                args2.p = args2.q;
            }
        } else if (args2.q < 0) {
            args2.q = args2.p;
        }

        return args2;
    }
    
    public boolean isServer() {
        return server;
    }
    
    public boolean isDebug() {
        return debug;
    }

    public int getPort() {
        return port;
    }

    public float getP() {
        return p;
    }

    public float getQ() {
        return q;
    }

    public String getHostname() {
        return hostname;
    }

    public List<String> getFilenames() {
        return filenames;
    }

    public static String getHelp() {
        return "Server: rft -s [-d] [-t <port>] [-p <p>] [-q <q>], Client: " +
               "rft <hostname> [-d] [-t <port>] [-p <p>] [-q <q>] <file> ...";
    }

    @Override
    public String toString() {
        String s = isServer() ? "Server, " : "Client, ";
        s += "hostname: " + hostname + ", port: " + port + ", p: " + p + ", q: " + q;
        for (String filename : filenames) {
            s += ", file: " + filename;
        }
        return s;
    }
}
