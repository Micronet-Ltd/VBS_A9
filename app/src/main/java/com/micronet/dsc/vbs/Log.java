/*
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE.txt', which is part of this source code package.
 */

package com.micronet.dsc.vbs;

import java.util.Arrays;

/**
 * Log allows for additional logging functionality.
 */
public class Log {
    // set which type of entries you want to record to the log
    public static boolean LOGLEVEL_VERBOSE_VERBOSE = false;
    public static boolean LOGLEVEL_VERBOSE = true;
    public static boolean LOGLEVEL_DEBUG = true;

    // Info, Warnings, and Errors are always recorded.

    //public static String logTags[] = {"ATS-Service", "ATS-Power", "ATS-Queue", "ATS-Engine", "ATS-J1939", "ATS-CAN", "ATS-J1587"};
    public static String logTags[] = {"*"};

    public static LogCallbackInterface callbackInterface;
    public static String callbackTags[] = {"*"};
    public static String callbackLevels[] = {"i", "w", "e"};

    public interface LogCallbackInterface {
        public void show(String tag, String text);
    }

    public static void vv(final String TAG, final String TEXT) { // Extra verbose
        if (LOGLEVEL_VERBOSE_VERBOSE) {
            if (!allowTag(TAG)) return;
            android.util.Log.v(TAG, TEXT);

            if ((callbackInterface != null) &&
                    (Arrays.asList(callbackLevels).contains("vv"))) {
                callbackInterface.show(TAG, TEXT);
            }
        }
    }


    public static synchronized void v(final String TAG, final String TEXT) {
        if (LOGLEVEL_VERBOSE) {
            if (!allowTag(TAG)) return;
            android.util.Log.v(TAG, TEXT);

            if ((callbackInterface != null) &&
                    (Arrays.asList(callbackLevels).contains("v"))) {
                callbackInterface.show(TAG, TEXT);
            }
        }
    }


    public static void d(final String TAG, final String TEXT) {
        if (LOGLEVEL_DEBUG) {
            if (!allowTag(TAG)) return;
            android.util.Log.d(TAG, TEXT);
            if ((callbackInterface != null) &&
                    (Arrays.asList(callbackLevels).contains("d"))) {
                callbackInterface.show(TAG, TEXT);
            }
        }
    }

    public static void i(final String TAG, final String TEXT) {
        if (!allowTag(TAG)) return;
        android.util.Log.i(TAG, TEXT);
        if ((callbackInterface != null) &&
                (Arrays.asList(callbackLevels).contains("i"))) {
            callbackInterface.show(TAG, TEXT);
        }
    }

    public static void w(final String TAG, final String TEXT) {
        if (!allowTag(TAG)) return;
        android.util.Log.w(TAG, TEXT);
        if ((callbackInterface != null) &&
                (Arrays.asList(callbackLevels).contains("w"))) {
            callbackInterface.show(TAG, TEXT);
        }
    }


    public static void e(final String TAG, final String TEXT) {
        if (!allowTag(TAG)) return;
        android.util.Log.e(TAG, TEXT);
        if ((callbackInterface != null) &&
                (Arrays.asList(callbackLevels).contains("e"))) {
            callbackInterface.show(TAG, TEXT);
        }
    }

    public static void e(final String TAG, final String TEXT, final Exception e) {
        if (!allowTag(TAG)) return;
        android.util.Log.e(TAG, TEXT, e);
        if ((callbackInterface != null) &&
                (Arrays.asList(callbackLevels).contains("e"))) {
            callbackInterface.show(TAG, TEXT);
        }
    }

    public static boolean allowTag(String tag) {
        for (int i = 0; i < logTags.length; i++) {
            if (logTags[i].equals("*")) return true; // asterisk means accept all
            if (logTags[i].equalsIgnoreCase(tag)) return true;
        }

        return false;
    }

    final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();

    public static String bytesToHex(byte[] bytes, int start_index, int length) {
        char[] hexChars = new char[length * 2];
        int h = 0;
        for (int j = start_index; j < (start_index + length); j++) {
            int v = bytes[j] & 0xFF;
            hexChars[h * 2] = hexArray[v >>> 4];
            hexChars[h * 2 + 1] = hexArray[v & 0x0F];
            h++;
        }
        return new String(hexChars);
    }

    public static String bytesToHex(byte[] bytes, int length) {
        return bytesToHex(bytes, 0, length);
    }


    public static byte[] hexToBytes(String hex) {
        byte[] bytes = new byte[hex.length() / 2];
        int i;
        for (i = 0; i < hex.length() - 1; i += 2) {
            int cp = (int) hex.codePointAt(i);
            cp &= 0xFF; // no funny business
            if (cp >= 0x41) cp -= 7;
            cp -= 0x30;
            cp &= 0x0F;
            bytes[(i >> 1)] = (byte) (cp << 4);

            cp = hex.codePointAt(i + 1);
            cp &= 0xFF; // no funny business
            if (cp >= 0x41) cp -= 7;
            cp -= 0x30;
            cp &= 0x0F;
            bytes[(i >> 1)] |= cp;
        }

        return bytes;
    }
}
