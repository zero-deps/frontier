# frontier

![ci](https://github.com/zero-deps/frontier/workflows/ci/badge.svg)

Fast, efficient, pure-functional, effect-free WebSocket, HTTP and UDP server, HTTP client and Telegram bot.

## demo

```
sbt 'project demo' run
curl -X POST -d 'hi' http://localhost:9012/echo
```

## benchmark

|          | cnt/s | 99th pct |
| -------- | -----:| --------:|
| frontier |   300 |      132 |
| http4s   |   300 |      176 |
