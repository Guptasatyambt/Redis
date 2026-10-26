package com.example.redis.command;

import com.example.redis.protocol.RespEncoder;

import java.util.List;

/**
 * ECHO message -> message
 */
public class EchoCommand implements Command {

    @Override
    public String execute(List<String> args) {
        if (args.size() != 2) {
            return Command.wrongNumberOfArguments("echo");
        }
        return RespEncoder.bulkString(args.get(1));
    }

}
