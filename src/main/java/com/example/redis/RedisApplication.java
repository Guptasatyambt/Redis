package com.example.redis;

import com.example.redis.command.CommandHandler;
import com.example.redis.server.RedisServer;
import com.example.redis.store.DataStore;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;

@SpringBootApplication
public class RedisApplication {

    private static final int PORT = 6379;

    public static void main(String[] args) {

//        SpringApplication.run(RedisApplication.class, args);

        DataStore store = new DataStore();
        CommandHandler commandHandler = new CommandHandler(store);
        RedisServer server = new RedisServer(PORT, commandHandler, store);
        try {
            server.start();
        }
        catch (IOException e) {
            System.out.println("IOException:" + e.getMessage());
        }
    }

}
