package com.example.redis.command;

import com.example.redis.protocol.RespEncoder;
import com.example.redis.store.DataStore;

import java.util.List;

/**
 * GET key -> value, or null if the key doesn't exist or has expired
 */
public class GetCommand implements Command {

    private final DataStore store;

    public GetCommand(DataStore store) {
        this.store = store;
    }

    @Override
    public String execute(List<String> args) {
        if (args.size() != 2) {
            return Command.wrongNumberOfArguments("get");
        }
        String value = store.get(args.get(1));
        return value == null ? RespEncoder.nullBulkString() : RespEncoder.bulkString(value);
    }

}
