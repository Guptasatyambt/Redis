package com.example.redis.command;

import com.example.redis.protocol.RespEncoder;
import com.example.redis.store.DataStore;

import java.util.List;

/**
 * SET key value              -> +OK
 * SET key value EX seconds   -> +OK, key expires after the given seconds
 * SET key value PX millis    -> +OK, key expires after the given milliseconds
 */
public class SetCommand implements Command {

    private final DataStore store;

    public SetCommand(DataStore store) {
        this.store = store;
    }

    @Override
    public String execute(List<String> args) {
        if (args.size() < 3) {
            return Command.wrongNumberOfArguments("set");
        }
        String key = args.get(1);
        String value = args.get(2);

        Long ttlMillis = null;
        for (int i = 3; i < args.size(); i++) {
            String option = args.get(i).toUpperCase();
            boolean isExpiryOption = option.equals("EX") || option.equals("PX");
            // Only one expiry option allowed, and it needs a value after it
            if (!isExpiryOption || ttlMillis != null || i + 1 >= args.size()) {
                return RespEncoder.error("ERR syntax error");
            }

            long amount;
            try {
                amount = Long.parseLong(args.get(++i));
            }
            catch (NumberFormatException e) {
                return RespEncoder.error("ERR value is not an integer or out of range");
            }
            if (amount <= 0 || (option.equals("EX") && amount > Long.MAX_VALUE / 1000)) {
                return RespEncoder.error("ERR invalid expire time in 'set' command");
            }
            ttlMillis = option.equals("EX") ? amount * 1000 : amount;
        }

        // A plain SET also clears any expiry the key had before, like real Redis
        if (ttlMillis == null) {
            store.set(key, value);
        } else {
            store.set(key, value, ttlMillis);
        }
        return RespEncoder.simpleString("OK");
    }

}
