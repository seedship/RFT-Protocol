package com.protocoldesigngroup2.xxx.messages;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.util.ArrayList;

import com.protocoldesigngroup2.xxx.messages.CloseConnection.Reason;

/**
 * Unit test for simple App.
 */
public class CloseConnectionTest 
    extends TestCase
{
    /**
     * Create the test case
     *
     * @param testName name of the test case
     */
    public CloseConnectionTest(String testName) {
        super(testName);
    }

    /**
     * @return the suite of tests being tested
     */
    public static Test suite() {
        return new TestSuite(CloseConnectionTest.class);
    }

    public void testDecodeSimple() {
        int ackNumber = 0;
        CloseConnection expected = new CloseConnection(ackNumber, new ArrayList<>(), Reason.UNSPECIFIED);
        byte[] msg = expected.encode();
        CloseConnection got = CloseConnection.decode(msg, 3, msg.length, ackNumber, new ArrayList<>());
        assertEquals(expected, got);
        
        ackNumber = 5;
        expected = new CloseConnection(ackNumber, new ArrayList<>(), Reason.APPLICATION_CLOSED);
        msg = expected.encode();
        got = CloseConnection.decode(msg, 3, msg.length, ackNumber, new ArrayList<>());
        assertEquals(expected, got);
        
        ackNumber = 23;
        expected = new CloseConnection(ackNumber, new ArrayList<>(), Reason.UNSUPPORTED_VERSION);
        msg = expected.encode();
        got = CloseConnection.decode(msg, 3, msg.length, ackNumber, new ArrayList<>());
        assertEquals(expected, got);
        
        ackNumber = 6;
        expected = new CloseConnection(ackNumber, new ArrayList<>(), Reason.UNKNOWN_REQUEST_ID);
        msg = expected.encode();
        got = CloseConnection.decode(msg, 3, msg.length, ackNumber, new ArrayList<>());
        assertEquals(expected, got);
        
        ackNumber = 101;
        expected = new CloseConnection(ackNumber, new ArrayList<>(), Reason.WRONG_CHECKSUM);
        msg = expected.encode();
        got = CloseConnection.decode(msg, 3, msg.length, ackNumber, new ArrayList<>());
        assertEquals(expected, got);
        
        ackNumber = 255;
        expected = new CloseConnection(ackNumber, new ArrayList<>(), Reason.DOWNLOAD_FINISHED);
        msg = expected.encode();
        got = CloseConnection.decode(msg, 3, msg.length, ackNumber, new ArrayList<>());
        assertEquals(expected, got);
        
        ackNumber = 30;
        expected = new CloseConnection(ackNumber, new ArrayList<>(), Reason.TIMEOUT);
        msg = expected.encode();
        got = CloseConnection.decode(msg, 3, msg.length, ackNumber, new ArrayList<>());
        assertEquals(expected, got);
        
        ackNumber = 154;
        expected = new CloseConnection(ackNumber, new ArrayList<>(), Reason.FILE_TOO_SMALL);
        msg = expected.encode();
        got = CloseConnection.decode(msg, 3, msg.length, ackNumber, new ArrayList<>());
        assertEquals(expected, got);
    }
}
