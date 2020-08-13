package com.protocoldesigngroup2.xxx;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * Unit test for simple App.
 */
public class ArgumentsTest 
    extends TestCase
{
    /**
     * Create the test case
     *
     * @param testName name of the test case
     */
    public ArgumentsTest(String testName) {
        super(testName);
    }

    /**
     * @return the suite of tests being tested
     */
    public static Test suite() {
        return new TestSuite(ArgumentsTest.class);
    }

    public void testDefaultServer() {
        String[] args = {"-s"};
        Arguments args2 = Arguments.parse(args);
        assertEquals("", args2.getHostname());
        assertEquals(true, args2.isServer());
        assertEquals(false, args2.isDebug());
        assertEquals(0.0f, args2.getP());
        assertEquals(0.0f, args2.getQ());
        assertEquals(Arguments.DEFAULT_PORT, args2.getPort());
        assertEquals(0, args2.getFilenames().size());
    }

    public void testComplexServer() {
        String[] args = {"-s", "-p", "0.2", "-v", "-t", "9501", "text.txt"};
        Arguments args2 = Arguments.parse(args);
        assertEquals("", args2.getHostname());
        assertEquals(true, args2.isServer());
        assertEquals(true, args2.isDebug());
        assertEquals(0.2f, args2.getP());
        assertEquals(0.2f, args2.getQ());
        assertEquals(9501, args2.getPort());
        assertEquals(0, args2.getFilenames().size());
    }

    public void testDefaultClient() {
        String[] args = {"server.com", "test1.txt"};
        Arguments args2 = Arguments.parse(args);
        assertEquals("server.com", args2.getHostname());
        assertEquals(false, args2.isServer());
        assertEquals(false, args2.isDebug());
        assertEquals(0.0f, args2.getP());
        assertEquals(0.0f, args2.getQ());
        assertEquals(Arguments.DEFAULT_PORT, args2.getPort());
        assertEquals(1, args2.getFilenames().size());
        assertEquals("test1.txt", args2.getFilenames().get(0));
    }

    public void testComplexClient() {
        String[] args = {"server.com", "-p", "0.5", "-q", "0.1", "-t", "1296", "-v", "abc.xyz", "test1.txt"};
        Arguments args2 = Arguments.parse(args);
        assertEquals("server.com", args2.getHostname());
        assertEquals(false, args2.isServer());
        assertEquals(true, args2.isDebug());
        assertEquals(0.5f, args2.getP());
        assertEquals(0.1f, args2.getQ());
        assertEquals(1296, args2.getPort());
        assertEquals(2, args2.getFilenames().size());
        assertEquals("abc.xyz", args2.getFilenames().get(0));
        assertEquals("test1.txt", args2.getFilenames().get(1));
    }
}
