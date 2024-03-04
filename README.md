# frontier

Fast, efficient, pure-functional, effect-free WebSocket, HTTP and UDP server, HTTP client and Telegram bot.

## demo

```
sbt 'project demo' run
curl -X GET -d 'こんにちは' http://localhost:9012/echo
curl -X POST -d 'こんにちは' http://localhost:9012/echo
curl -X TRACE -d 'こんにちは' http://localhost:9012
```

## benchmark

|          | cnt/s | 99th pct |
| -------- | -----:| --------:|
| frontier |   750 |      206 |
| http4s   |   750 |      236 |

```sh
ulimit -n 65536
sbt 'project benchmark' run
ulimit -n 65536
sbt 'project benchmark' GatlingIt/test
```
