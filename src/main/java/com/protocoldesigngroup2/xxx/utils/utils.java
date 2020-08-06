package com.protocoldesigngroup2.xxx.utils;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;

public class utils {
    public static final int KB = 1024;

    // https://stackoverflow.com/questions/304268/getting-a-files-md5-checksum-in-java
    public static byte[] generateMD5(String filePath) {
        File f = new File(filePath);
        if (!f.exists()) {
            return new byte[16];
        }
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] b = Files.readAllBytes(Paths.get(filePath));
            return md.digest(b);
        } catch (Exception ex) {
            System.out.println("Unexpected exception!");
            ex.printStackTrace();
            return null;
        }
    }

    private static boolean debug = false;

    public static void setDebug(boolean d) {
        debug = d;
    }

    public static void printDebug(String s) {
        if (debug) {
            System.out.println(s);
        }
    }
}
