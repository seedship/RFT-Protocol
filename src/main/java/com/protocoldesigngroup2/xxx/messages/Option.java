package com.protocoldesigngroup2.xxx.messages;


public interface Option {
    public byte getOptionType();
    public int getLength();
    public void encodeOption(byte[] buffer, int offset);
}
