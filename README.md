# frontier

[![CI](https://img.shields.io/github/workflow/status/zero-deps/frontier/ci)](https://github.com/zero-deps/frontier/actions/workflows/test.yml)
[![License](https://img.shields.io/badge/license-DHARMA-green)](LICENSE)
[![LoC](https://img.shields.io/tokei/lines/github/zero-deps/frontier)](#)

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
| frontier |   300 |      132 |
| http4s   |   300 |      176 |
