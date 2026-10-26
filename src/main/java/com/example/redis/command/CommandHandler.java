package com.example.redis.command;

import com.example.redis.protocol.RespEncoder;
import com.example.redis.store.DataStore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Looks up the command by name and runs it.
 */
public class CommandHandler {

    private final Map<String, Command> commands = new HashMap<>();

    public CommandHandler(DataStore store) {
        commands.put("PING", new PingCommand());
        commands.put("ECHO", new EchoCommand());
        commands.put("SET", new SetCommand(store));
        commands.put("GET", new GetCommand(store));
    }

    public String handle(List<String> args) {
        Command command = commands.get(args.get(0).toUpperCase());
        if (command == null) {
            return RespEncoder.error("ERR unknown command '" + args.get(0) + "'");
        }
        return command.execute(args);
    }

}
