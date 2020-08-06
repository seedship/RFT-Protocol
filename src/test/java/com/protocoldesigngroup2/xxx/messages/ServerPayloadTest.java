package com.protocoldesigngroup2.xxx.messages;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Unit test for simple App.
 */
public class ServerPayloadTest 
    extends TestCase
{
    /**
     * Create the test case
     *
     * @param testName name of the test case
     */
    public ServerPayloadTest(String testName) {
        super(testName);
    }

    /**
     * @return the suite of tests being tested
     */
    public static Test suite() {
        return new TestSuite(ServerPayloadTest.class);
    }

    public void testDecodeSimple() {
        int ackNumber = 0;
        ServerPayload expected = new ServerPayload(
            ackNumber,
            new ArrayList<>(),
            0,
            0,
            new byte[0],
            0);
        byte[] msg = expected.encode();

        ServerPayload got = ServerPayload.decode(msg, 3, msg.length, ackNumber, new ArrayList<>());
        assertEquals(expected, got);
    }

    public void testDecodeComplex() {
        int ackNumber = 35;
        byte[] pl = {(byte)0x12, (byte)0x5f, (byte)0x13, (byte)0x00, (byte)0x64, (byte)0x99};
        ServerPayload expected = new ServerPayload(
            ackNumber,
            new ArrayList<>(),
            12345,
            5789456123L,
            pl,
            pl.length);
        byte[] msg = expected.encode();

        ServerPayload got = ServerPayload.decode(msg, 3, msg.length, ackNumber, new ArrayList<>());
        assertEquals(expected, got);
    }

    public void testDecodeComplexDifferentPayloadLength() {
        int ackNumber = 35;
        byte[] pl = {(byte)0x12, (byte)0x5f, (byte)0x13, (byte)0x00, (byte)0x64, (byte)0x99};
        ServerPayload expected = new ServerPayload(
            ackNumber,
            new ArrayList<>(),
            12345,
            5789456123L,
            pl,
            2);
        byte[] msg = expected.encode();

        ServerPayload got = ServerPayload.decode(msg, 3, msg.length, ackNumber, new ArrayList<>());
        byte[] e = {(byte)0x12, (byte)0x5f};
        Arrays.equals(got.payload, e);
    }
}
