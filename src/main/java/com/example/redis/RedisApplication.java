package com.example.redis;

import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ProtocolException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

@SpringBootApplication
public class RedisApplication {

    // Per-client state: bytes received so far that don't yet form a full command
    private static class Client {
        final ByteBuffer readBuffer = ByteBuffer.allocate(1024);
        byte[] pending = new byte[0];
    }

    // A parsed command and how many bytes of input it used
    private record Command(List<String> args, int bytesUsed) {
    }

    public static void main(String[] args) {

//        SpringApplication.run(RedisApplication.class, args);

        int port = 6379;
        try (Selector selector = Selector.open();
             ServerSocketChannel server = ServerSocketChannel.open()) {

            server.socket().setReuseAddress(true);
            server.bind(new InetSocketAddress(port));
            server.configureBlocking(false);
            server.register(selector, SelectionKey.OP_ACCEPT);
            System.out.println("Listening on port " + port);

            // Event loop: one thread waits for any client to be ready, then handles it
            while (true) {
                selector.select();
                Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
                while (keys.hasNext()) {
                    SelectionKey key = keys.next();
                    keys.remove();

                    if (key.isAcceptable()) {
                        accept(selector, server);
                    } else if (key.isReadable()) {
                        read(key);
                    }
                }
            }
        }
        catch (IOException e) {
            System.out.println("IOException:" + e.getMessage());
        }
    }

    private static void accept(Selector selector, ServerSocketChannel server) throws IOException {
        SocketChannel client = server.accept();
        if (client == null) {
            return;
        }
        client.configureBlocking(false);
        client.register(selector, SelectionKey.OP_READ, new Client());
        System.out.println("Client connected: " + client.getRemoteAddress());
    }

    private static void read(SelectionKey key) {
        SocketChannel channel = (SocketChannel) key.channel();
        Client client = (Client) key.attachment();
        try {
            client.readBuffer.clear();
            int bytesRead = channel.read(client.readBuffer);
            if (bytesRead == -1) {
                System.out.println("Client disconnected: " + channel.getRemoteAddress());
                close(key);
                return;
            }

            // Add the new bytes to whatever was left over from earlier reads
            byte[] data = Arrays.copyOf(client.pending, client.pending.length + bytesRead);
            System.arraycopy(client.readBuffer.array(), 0, data, client.pending.length, bytesRead);

            // A single read can hold several commands, or only part of one
            int offset = 0;
            Command command;
            while ((command = parse(data, offset)) != null) {
                offset += command.bytesUsed();
                if (!command.args().isEmpty()) {
                    write(channel, handle(command.args()));
                }
            }
            client.pending = Arrays.copyOfRange(data, offset, data.length);
        }
        catch (ProtocolException e) {
            // Like real Redis: send the error back as RESP, then drop the connection
            System.out.println(e.getMessage());
            try {
                write(channel, "-ERR " + e.getMessage() + "\r\n");
            }
            catch (IOException ignored) {
            }
            close(key);
        }
        catch (IOException e) {
            System.out.println("IOException:" + e.getMessage());
            close(key);
        }
    }

    private static String handle(List<String> args) {
        String name = args.get(0).toUpperCase();
        switch (name) {
            case "PING":
                if (args.size() == 1) {
                    return "+PONG\r\n";
                }
                if (args.size() == 2) {
                    return bulkString(args.get(1));
                }
                return wrongArgs("ping");
            case "ECHO":
                if (args.size() != 2) {
                    return wrongArgs("echo");
                }
                return bulkString(args.get(1));
            default:
                return "-ERR unknown command '" + args.get(0) + "'\r\n";
        }
    }

    private static String bulkString(String value) {
        int length = value.getBytes(StandardCharsets.UTF_8).length;
        return "$" + length + "\r\n" + value + "\r\n";
    }

    private static String wrongArgs(String command) {
        return "-ERR wrong number of arguments for '" + command + "' command\r\n";
    }

    /**
     * Parses one RESP command starting at offset. Returns null if the full command hasn't arrived yet.
     * Commands must be RESP arrays of bulk strings, e.g. *2\r\n$4\r\nECHO\r\n$3\r\nhey\r\n
     */
    private static Command parse(byte[] data, int offset) throws IOException {
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
        return new Command(args, pos - offset);
    }

    private static int indexOfCrlf(byte[] data, int from) {
        for (int i = from; i < data.length - 1; i++) {
            if (data[i] == '\r' && data[i + 1] == '\n') {
                return i;
            }
        }
        return -1;
    }

    private static int parseNumber(byte[] data, int start, int end) throws IOException {
        try {
            return Integer.parseInt(new String(data, start, end - start, StandardCharsets.UTF_8));
        }
        catch (NumberFormatException e) {
            throw new ProtocolException("Protocol error: invalid length");
        }
    }

    private static void write(SocketChannel channel, String response) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(response.getBytes(StandardCharsets.UTF_8));
        while (buffer.hasRemaining()) {
            channel.write(buffer);
        }
    }

    private static void close(SelectionKey key) {
        key.cancel();
        try {
            key.channel().close();
        }
        catch (IOException ignored) {
        }
    }

}
