package com.example.redis.server;

import com.example.redis.protocol.RespParser;

import java.net.ProtocolException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

/**
 * Per-client state: holds bytes received so far until they form complete commands.
 */
public class ClientConnection {

    private final ByteBuffer readBuffer = ByteBuffer.allocate(1024);
    private byte[] pending = new byte[0];

    public ByteBuffer readBuffer() {
        return readBuffer;
    }

    // Adds the bytes just read into readBuffer to the pending data
    public void append(int bytesRead) {
        int oldLength = pending.length;
        pending = Arrays.copyOf(pending, oldLength + bytesRead);
        System.arraycopy(readBuffer.array(), 0, pending, oldLength, bytesRead);
    }

    // Returns the next complete command, or null if the rest of it hasn't arrived yet
    public List<String> nextCommand() throws ProtocolException {
        RespParser.Result result = RespParser.parse(pending, 0);
        if (result == null) {
            return null;
        }
        pending = Arrays.copyOfRange(pending, result.bytesUsed(), pending.length);
        return result.args();
    }

}
