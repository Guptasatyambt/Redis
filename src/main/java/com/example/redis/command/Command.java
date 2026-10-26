package com.example.redis.command;

import com.example.redis.protocol.RespEncoder;

import java.util.List;

/**
 * A Redis command. args.get(0) is the command name, the rest are its arguments.
 * Returns the reply already encoded in RESP.
 */
public interface Command {

    String execute(List<String> args);

    static String wrongNumberOfArguments(String commandName) {
        return RespEncoder.error("ERR wrong number of arguments for '" + commandName + "' command");
    }

}
