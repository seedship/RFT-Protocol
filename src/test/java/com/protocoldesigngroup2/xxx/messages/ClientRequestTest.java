package com.protocoldesigngroup2.xxx;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.util.List;
import java.util.ArrayList;

import com.protocoldesigngroup2.xxx.messages.ClientRequest;
import com.protocoldesigngroup2.xxx.messages.ClientRequest.FileDescriptor;

/**
 * Unit test for simple App.
 */
public class ClientRequestTest 
    extends TestCase
{
    /**
     * Create the test case
     *
     * @param testName name of the test case
     */
    public ClientRequestTest(String testName) {
        super(testName);
    }

    /**
     * @return the suite of tests being tested
     */
    public static Test suite() {
        return new TestSuite(ClientRequestTest.class);
    }

    public void testDecodeSimple() {
        int ackNumber = 0;
        ClientRequest expected = new ClientRequest(
            ackNumber,
            new ArrayList<>(),
            0,
            new ArrayList<>());
        byte[] msg = expected.encode();

        ClientRequest got = ClientRequest.decode(msg, 3, msg.length, ackNumber, new ArrayList<>());
        assertEquals(expected, got);
    }

    public void testDecodeComplex() {
        int ackNumber = 255;
        List<FileDescriptor> fd = new ArrayList<>();
        fd.add(new FileDescriptor(2, "myPrettyName"));
        fd.add(new FileDescriptor(45681, "BliBlaBlub :)"));
        fd.add(new FileDescriptor(5123456789L, "\");DROP TABLE STUDENTS;"));
        ClientRequest expected = new ClientRequest(
            ackNumber,
            new ArrayList<>(),
            3123456789L,
            fd);
        byte[] msg = expected.encode();

        ClientRequest got = ClientRequest.decode(msg, 3, msg.length, ackNumber, new ArrayList<>());
        assertEquals(expected, got);
    }

    public void testDecodeEncoding() {
        int ackNumber = 0;
        List<FileDescriptor> fd = new ArrayList<>();
        fd.add(new FileDescriptor(0, "😋_😅"));
        fd.add(new FileDescriptor(0, "ÄnötherßWeirdÖne"));
        ClientRequest expected = new ClientRequest(
            ackNumber,
            new ArrayList<>(),
            0,
            fd);
        byte[] msg = expected.encode();

        ClientRequest got = ClientRequest.decode(msg, 3, msg.length, ackNumber, new ArrayList<>());
        assertEquals(expected, got);
    }
}
