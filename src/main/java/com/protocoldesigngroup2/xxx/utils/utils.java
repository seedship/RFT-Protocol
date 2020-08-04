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

    public static boolean compareMD5(byte[] sig1, byte[] sig2) {
        // Check whether both lengths differ
        if (sig1.length != sig2.length) {
            return false;
        }
        // Check whether checksums differ in their values
        for (int i = 0; i < Math.min(sig1.length, sig2.length); i++) {
            if (sig1[i] != sig2[i]) {
                return false;
            }
        }

        return true;
    }
}
