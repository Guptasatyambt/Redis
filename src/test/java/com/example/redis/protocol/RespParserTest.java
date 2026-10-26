package com.example.redis.protocol;

import org.junit.jupiter.api.Test;

import java.net.ProtocolException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RespParserTest {

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void parsesArrayOfBulkStrings() throws Exception {
        byte[] data = bytes("*2\r\n$4\r\nECHO\r\n$3\r\nhey\r\n");
        RespParser.Result result = RespParser.parse(data, 0);

        assertEquals(List.of("ECHO", "hey"), result.args());
        assertEquals(data.length, result.bytesUsed());
    }

    @Test
    void returnsNullWhenCommandIsIncomplete() throws Exception {
        assertNull(RespParser.parse(bytes("*2\r\n$4\r\nECHO\r\n$3\r\nhe"), 0));
        assertNull(RespParser.parse(bytes("*2\r\n$4\r\nEC"), 0));
        assertNull(RespParser.parse(bytes("*2"), 0));
    }

    @Test
    void parsesSecondCommandFromOffset() throws Exception {
        byte[] data = bytes("*1\r\n$4\r\nPING\r\n*2\r\n$4\r\nECHO\r\n$2\r\nhi\r\n");
        RespParser.Result first = RespParser.parse(data, 0);
        RespParser.Result second = RespParser.parse(data, first.bytesUsed());

        assertEquals(List.of("PING"), first.args());
        assertEquals(List.of("ECHO", "hi"), second.args());
    }

    @Test
    void usesByteLengthForMultiByteCharacters() throws Exception {
        // "é" is 2 bytes in UTF-8
        RespParser.Result result = RespParser.parse(bytes("*1\r\n$2\r\né\r\n"), 0);
        assertEquals(List.of("é"), result.args());
    }

    @Test
    void rejectsInlineCommands() {
        assertThrows(ProtocolException.class, () -> RespParser.parse(bytes("PING\r\n"), 0));
    }

    @Test
    void rejectsNonBulkStringElements() {
        assertThrows(ProtocolException.class, () -> RespParser.parse(bytes("*1\r\n:4\r\n"), 0));
    }

    @Test
    void rejectsInvalidLength() {
        assertThrows(ProtocolException.class, () -> RespParser.parse(bytes("*x\r\n"), 0));
    }

}
