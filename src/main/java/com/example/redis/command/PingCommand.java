package com.example.redis.command;

import com.example.redis.protocol.RespEncoder;

import java.util.List;

/**
 * PING           -> +PONG
 * PING message   -> message
 */
public class PingCommand implements Command {

    @Override
    public String execute(List<String> args) {
        if (args.size() == 1) {
            return RespEncoder.simpleString("PONG");
        }
        if (args.size() == 2) {
            return RespEncoder.bulkString(args.get(1));
        }
        return Command.wrongNumberOfArguments("ping");
    }

}
