package com.protocoldesigngroup2.xxx;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.util.ArrayList;

import com.protocoldesigngroup2.xxx.messages.ServerMetadata;
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
        ServerMetadata expected = new ServerMetadata(
            ackNumber,
            new ArrayList<>(),
            Status.ACCESS_DENIED,
            0,
            0,
            0);
        byte[] msg = expected.encode();

        ServerMetadata got = ServerMetadata.decode(msg, 3, msg.length, ackNumber, new ArrayList<>());
        assertEquals(expected, got);
    }

    public void testDecodeComplex() {
        int ackNumber = 0;
        ServerMetadata expected = new ServerMetadata(
            ackNumber,
            new ArrayList<>(),
            Status.FILE_IS_EMPTY,
            65535,
            8123456789L,
            5321654987L);
        byte[] msg = expected.encode();

        ServerMetadata got = ServerMetadata.decode(msg, 3, msg.length, ackNumber, new ArrayList<>());
        assertEquals(expected, got);
    }
}
