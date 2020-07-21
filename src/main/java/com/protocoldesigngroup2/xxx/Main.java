package com.protocoldesigngroup2.xxx;


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
            // TODO: Add client init here
        }
    }
}
