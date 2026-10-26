package com.example.redis.protocol;

import java.nio.charset.StandardCharsets;

/**
 * Builds replies in RESP format.
 */
public final class RespEncoder {

    private RespEncoder() {
    }

    // +OK\r\n
    public static String simpleString(String value) {
        return "+" + value + "\r\n";
    }

    // -ERR message\r\n
    public static String error(String message) {
        return "-" + message + "\r\n";
    }

    // $3\r\nhey\r\n
    public static String bulkString(String value) {
        int length = value.getBytes(StandardCharsets.UTF_8).length;
        return "$" + length + "\r\n" + value + "\r\n";
    }

    // $-1\r\n — "no value", e.g. GET on a missing key
    public static String nullBulkString() {
        return "$-1\r\n";
    }

}
