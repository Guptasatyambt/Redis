package com.example.redis.server;

import com.example.redis.command.CommandHandler;
import com.example.redis.protocol.RespEncoder;
import com.example.redis.store.DataStore;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ProtocolException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;

/**
 * Single-threaded event loop: one Selector watches every connection and handles whichever is ready.
 */
public class RedisServer {

    // How long select() waits before giving the loop a chance to run background work
    private static final long SELECT_TIMEOUT_MILLIS = 100;
    private static final long EXPIRED_KEY_CLEANUP_INTERVAL_MILLIS = 1000;

    private final int port;
    private final CommandHandler commandHandler;
    private final DataStore store;
    private long lastCleanup = System.currentTimeMillis();

    public RedisServer(int port, CommandHandler commandHandler, DataStore store) {
        this.port = port;
        this.commandHandler = commandHandler;
        this.store = store;
    }

    public void start() throws IOException {
        try (Selector selector = Selector.open();
             ServerSocketChannel server = ServerSocketChannel.open()) {

            server.socket().setReuseAddress(true);
            server.bind(new InetSocketAddress(port));
            server.configureBlocking(false);
            server.register(selector, SelectionKey.OP_ACCEPT);
            System.out.println("Listening on port " + port);

            while (true) {
                selector.select(SELECT_TIMEOUT_MILLIS);
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
                removeExpiredKeysIfDue();
            }
        }
    }

    private void accept(Selector selector, ServerSocketChannel server) throws IOException {
        SocketChannel channel = server.accept();
        if (channel == null) {
            return;
        }
        channel.configureBlocking(false);
        channel.register(selector, SelectionKey.OP_READ, new ClientConnection());
        System.out.println("Client connected: " + channel.getRemoteAddress());
    }

    private void read(SelectionKey key) {
        SocketChannel channel = (SocketChannel) key.channel();
        ClientConnection client = (ClientConnection) key.attachment();
        try {
            ByteBuffer readBuffer = client.readBuffer();
            readBuffer.clear();
            int bytesRead = channel.read(readBuffer);
            if (bytesRead == -1) {
                System.out.println("Client disconnected: " + channel.getRemoteAddress());
                close(key);
                return;
            }
            client.append(bytesRead);

            // A single read can hold several commands, or only part of one
            List<String> args;
            while ((args = client.nextCommand()) != null) {
                if (!args.isEmpty()) {
                    write(channel, commandHandler.handle(args));
                }
            }
        }
        catch (ProtocolException e) {
            // Like real Redis: send the error back as RESP, then drop the connection
            System.out.println(e.getMessage());
            try {
                write(channel, RespEncoder.error("ERR " + e.getMessage()));
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

    // Keys are also removed lazily on GET; this catches keys that expire and are never read again
    private void removeExpiredKeysIfDue() {
        long now = System.currentTimeMillis();
        if (now - lastCleanup >= EXPIRED_KEY_CLEANUP_INTERVAL_MILLIS) {
            store.removeExpired();
            lastCleanup = now;
        }
    }

    private void write(SocketChannel channel, String response) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(response.getBytes(StandardCharsets.UTF_8));
        while (buffer.hasRemaining()) {
            channel.write(buffer);
        }
    }

    private void close(SelectionKey key) {
        key.cancel();
        try {
            key.channel().close();
        }
        catch (IOException ignored) {
        }
    }

}
