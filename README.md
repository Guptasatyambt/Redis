# Redis Clone in Java

A small Redis-compatible server written in Java, built step by step to learn how Redis works.
It speaks the Redis protocol (RESP), so you can talk to it with the real `redis-cli`.

## Features

- Listens on port **6379** (the default Redis port)
- Handles **many clients at once** with a single-threaded **event loop** (Java NIO `Selector`)
- Reads and writes the **RESP** protocol only
- Commands:

| Command | Example | Reply |
|---|---|---|
| `PING` | `PING` | `+PONG` |
| `PING <message>` | `PING hello` | `$5\r\nhello\r\n` |
| `ECHO <message>` | `ECHO hey` | `$3\r\nhey\r\n` |
| `SET <key> <value>` | `SET name tushar` | `+OK` |
| `GET <key>` | `GET name` | `$6\r\ntushar\r\n`, or `$-1\r\n` if the key doesn't exist |

Data is kept in memory only, shared by all clients, and lost when the server stops.

## Requirements

- Java 17 or newer
- Gradle (the included wrapper `gradlew` works, no install needed)

## Running

From the project folder:

```
.\gradlew bootRun
```

or open `src/main/java/com/example/redis/RedisApplication.java` in IntelliJ and click ▶ next to `main`.

You should see:

```
Listening on port 6379
```

> If you get `Address already in use`, something else is already using port 6379
> (a real Redis server or an earlier run of this one). Stop it and try again.

## Testing

### With redis-cli

```
redis-cli -p 6379 ping
PONG

redis-cli -p 6379 echo "hello world"
"hello world"

redis-cli -p 6379 set name tushar
OK

redis-cli -p 6379 get name
"tushar"

redis-cli -p 6379 get missing
(nil)
```

### With PowerShell (no install needed)

```powershell
$c = New-Object System.Net.Sockets.TcpClient('localhost', 6379)
$s = $c.GetStream()
$w = New-Object System.IO.StreamWriter($s); $r = New-Object System.IO.StreamReader($s)

$w.Write("*1`r`n`$4`r`nPING`r`n"); $w.Flush()
$r.ReadLine()                  # +PONG

$w.Write("*2`r`n`$4`r`nECHO`r`n`$3`r`nhey`r`n"); $w.Flush()
$r.ReadLine(); $r.ReadLine()   # $3, then hey

$c.Close()
```

Open two terminals and run it in both to see several clients served at the same time.

## How it works

### Event loop

Instead of one thread per client, a single thread uses a `Selector` to wait until any connection is ready:

1. **Accept** – a new client connects; it is registered with the selector.
2. **Read** – a client sent data; the bytes are added to that client's buffer.
3. **Parse** – every complete command in the buffer is parsed and handled. Any incomplete
   command stays in the buffer until the rest arrives.
4. **Reply** – the response is written back in RESP format.
5. **Close** – when a client disconnects, its connection is cleaned up; other clients keep working.

### RESP protocol

Clients send each command as an **array of bulk strings**. `ECHO hey` is sent as:

```
*2\r\n        array with 2 items
$4\r\n        bulk string, 4 bytes
ECHO\r\n
$3\r\n        bulk string, 3 bytes
hey\r\n
```

The server replies with these RESP types:

| Type | Prefix | Example |
|---|---|---|
| Simple string | `+` | `+PONG\r\n` |
| Bulk string | `$` | `$3\r\nhey\r\n` |
| Null bulk string | `$-1` | `$-1\r\n` (GET on a missing key) |
| Error | `-` | `-ERR unknown command 'FOO'\r\n` |

### Errors

| Situation | Reply | Connection |
|---|---|---|
| Unknown command | `-ERR unknown command '<name>'` | stays open |
| Wrong number of arguments | `-ERR wrong number of arguments for '<cmd>' command` | stays open |
| Input that isn't valid RESP (e.g. plain text `PING`) | `-ERR Protocol error: ...` | closed |

## Project structure

```
src/main/java/com/example/redis/RedisApplication.java   server: event loop, RESP parser, commands, key-value store
build.gradle                                             build settings (Java 17)
```

## Adding a new command

Add a `case` to the `switch` in `handle()` in `RedisApplication.java`.
It receives the command as a list of strings (`["ECHO", "hey"]`) and returns the RESP reply as a string.

## Next steps

- Key expiry (`SET key value PX 100`)
- Move the RESP parser and reply helpers into their own class
