package com.example.redis.protocol;

import java.net.ProtocolException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses commands sent by clients. Commands must be RESP arrays of bulk strings,
 * e.g. *2\r\n$4\r\nECHO\r\n$3\r\nhey\r\n
 */
public final class RespParser {

    // A parsed command and how many bytes of input it used
    public record Result(List<String> args, int bytesUsed) {
    }

    private RespParser() {
    }

    /**
     * Parses one command starting at offset. Returns null if the full command hasn't arrived yet.
     */
    public static Result parse(byte[] data, int offset) throws ProtocolException {
        if (offset >= data.length) {
            return null;
        }
        if (data[offset] != '*') {
            throw new ProtocolException("Protocol error: expected '*', got '" + (char) data[offset] + "'");
        }

        int end = indexOfCrlf(data, offset);
        if (end == -1) {
            return null;
        }
        int count = parseNumber(data, offset + 1, end);
        int pos = end + 2;

        List<String> args = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            end = indexOfCrlf(data, pos);
            if (end == -1) {
                return null;
            }
            if (data[pos] != '$') {
                throw new ProtocolException("Protocol error: expected '$', got '" + (char) data[pos] + "'");
            }
            int length = parseNumber(data, pos + 1, end);
            pos = end + 2;
            if (pos + length + 2 > data.length) {
                return null;
            }
            args.add(new String(data, pos, length, StandardCharsets.UTF_8));
            pos += length + 2;
        }
        return new Result(args, pos - offset);
    }

    private static int indexOfCrlf(byte[] data, int from) {
        for (int i = from; i < data.length - 1; i++) {
            if (data[i] == '\r' && data[i + 1] == '\n') {
                return i;
            }
        }
        return -1;
    }

    private static int parseNumber(byte[] data, int start, int end) throws ProtocolException {
        try {
            return Integer.parseInt(new String(data, start, end - start, StandardCharsets.UTF_8));
        }
        catch (NumberFormatException e) {
            throw new ProtocolException("Protocol error: invalid length");
        }
    }

}
