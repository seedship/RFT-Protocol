package com.protocoldesigngroup2.xxx.messages;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.util.List;
import java.util.ArrayList;

import com.protocoldesigngroup2.xxx.messages.ClientAck.ResendEntry;

/**
 * Unit test for simple App.
 */
public class ClientAckTest 
    extends TestCase
{
    /**
     * Create the test case
     *
     * @param testName name of the test case
     */
    public ClientAckTest(String testName) {
        super(testName);
    }

    /**
     * @return the suite of tests being tested
     */
    public static Test suite() {
        return new TestSuite(ClientAckTest.class);
    }

    public void testDecodeSimple() {
        int ackNumber = 0;
        ClientAck expected = new ClientAck(
            ackNumber,
            new ArrayList<>(),
            0,
            ClientAck.Status.NOTHING,
            0,
            0,
            new ArrayList<>());
        byte[] msg = expected.encode();

        ClientAck got = ClientAck.decode(msg, 3, msg.length, ackNumber, new ArrayList<>());
        assertEquals(expected, got);
    }

    public void testDecodeComplex() {
        int ackNumber = 255;
        List<ResendEntry> re = new ArrayList<>();
        re.add(new ResendEntry(1, 2, (short)3));
        re.add(new ResendEntry(321, 45681, (short)101));
        re.add(new ResendEntry(65535, 5123456789L, (short)255));
        ClientAck expected = new ClientAck(
            ackNumber,
            new ArrayList<>(),
            21345,
            ClientAck.Status.NO_METADATA_RECEIVED,
            3123456789L,
            6321654987L,
            re);
        byte[] msg = expected.encode();

        ClientAck got = ClientAck.decode(msg, 3, msg.length, ackNumber, new ArrayList<>());
        assertEquals(expected, got);
    }
}
