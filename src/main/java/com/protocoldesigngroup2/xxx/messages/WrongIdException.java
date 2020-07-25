package com.protocoldesigngroup2.xxx.messages;


public class WrongIdException extends Exception {
    private static final long serialVersionUID = -7402177213564454996L;

    public WrongIdException(String message) {
        super(message);
    }
}
