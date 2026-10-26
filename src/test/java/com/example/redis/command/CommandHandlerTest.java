package com.example.redis.command;

import com.example.redis.store.DataStore;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CommandHandlerTest {

    private final AtomicLong now = new AtomicLong(1_000);
    private final CommandHandler handler = new CommandHandler(new DataStore(now::get));

    private String run(String... args) {
        return handler.handle(List.of(args));
    }

    @Test
    void pingAndEcho() {
        assertEquals("+PONG\r\n", run("PING"));
        assertEquals("$5\r\nhello\r\n", run("ping", "hello"));
        assertEquals("$3\r\nhey\r\n", run("ECHO", "hey"));
    }

    @Test
    void unknownCommand() {
        assertEquals("-ERR unknown command 'FOO'\r\n", run("FOO"));
    }

    @Test
    void setThenGet() {
        assertEquals("$-1\r\n", run("GET", "name"));
        assertEquals("+OK\r\n", run("SET", "name", "tushar"));
        assertEquals("$6\r\ntushar\r\n", run("GET", "name"));
    }

    @Test
    void setWithPxExpires() {
        assertEquals("+OK\r\n", run("SET", "name", "tushar", "PX", "100"));
        now.addAndGet(99);
        assertEquals("$6\r\ntushar\r\n", run("GET", "name"));
        now.addAndGet(1);
        assertEquals("$-1\r\n", run("GET", "name"));
    }

    @Test
    void setWithExExpiresInSeconds() {
        assertEquals("+OK\r\n", run("SET", "name", "tushar", "ex", "2"));
        now.addAndGet(1_999);
        assertEquals("$6\r\ntushar\r\n", run("GET", "name"));
        now.addAndGet(1);
        assertEquals("$-1\r\n", run("GET", "name"));
    }

    @Test
    void setRejectsBadExpiry() {
        assertEquals("-ERR value is not an integer or out of range\r\n", run("SET", "k", "v", "PX", "abc"));
        assertEquals("-ERR invalid expire time in 'set' command\r\n", run("SET", "k", "v", "PX", "0"));
        assertEquals("-ERR invalid expire time in 'set' command\r\n", run("SET", "k", "v", "EX", "-5"));
        assertEquals("-ERR syntax error\r\n", run("SET", "k", "v", "PX"));
        assertEquals("-ERR syntax error\r\n", run("SET", "k", "v", "FOO", "1"));
        assertEquals("-ERR syntax error\r\n", run("SET", "k", "v", "EX", "1", "PX", "100"));
        // A rejected SET must not store anything
        assertEquals("$-1\r\n", run("GET", "k"));
    }

    @Test
    void wrongNumberOfArguments() {
        assertEquals("-ERR wrong number of arguments for 'get' command\r\n", run("GET"));
        assertEquals("-ERR wrong number of arguments for 'set' command\r\n", run("SET", "k"));
        assertEquals("-ERR wrong number of arguments for 'echo' command\r\n", run("ECHO"));
    }

}
