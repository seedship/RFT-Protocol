package com.protocoldesigngroup2.xxx.messages;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.util.ArrayList;

import com.protocoldesigngroup2.xxx.messages.ServerMetadata.Status;

/**
 * Unit test for simple App.
 */
public class ServerMetadataTest 
    extends TestCase
{
    /**
     * Create the test case
     *
     * @param testName name of the test case
     */
    public ServerMetadataTest(String testName) {
        super(testName);
    }

    /**
     * @return the suite of tests being tested
     */
    public static Test suite() {
        return new TestSuite(ServerMetadataTest.class);
    }

    public void testDecodeSimple() {
        int ackNumber = 0;
        byte[] checksum = new byte[16];
        ServerMetadata expected = new ServerMetadata(
            ackNumber,
            new ArrayList<>(),
            Status.ACCESS_DENIED,
            0,
            0,
            checksum);
        byte[] msg = expected.encode();

        ServerMetadata got = ServerMetadata.decode(msg, 3, msg.length, ackNumber, new ArrayList<>());
        assertEquals(expected, got);
    }

    public void testDecodeComplex() {
        int ackNumber = 0;
        byte[] checksum = {
            (byte)12, (byte)77, (byte)-23, (byte)1,
            (byte)125, (byte)-2, (byte)-128, (byte)67,
            (byte)3, (byte)-53, (byte)-34, (byte)-99,
            (byte)63, (byte)29, (byte)2, (byte)76};
        ServerMetadata expected = new ServerMetadata(
            ackNumber,
            new ArrayList<>(),
            Status.FILE_IS_EMPTY,
            65535,
            8123456789L,
            checksum);
        byte[] msg = expected.encode();

        ServerMetadata got = ServerMetadata.decode(msg, 3, msg.length, ackNumber, new ArrayList<>());
        assertEquals(expected, got);
    }
}
