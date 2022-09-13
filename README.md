# frontier

![Deprecated](https://img.shields.io/badge/Project%20Stage-Deprecated-red.svg)

A lightweight HTTP/WebSocket server with a single dependency on `zio-stream`.

## demo

```
sbt 'project demo' run
curl -X GET -d 'こんにちは' http://localhost:9012/echo
curl -X POST -d 'こんにちは' http://localhost:9012/echo
curl -X TRACE -d 'こんにちは' http://localhost:9012
```

## benchmark

|          | users | req/s | score |
| -------- | -----:| -----:| -----:|
| ./ftier  |   600 |   900 |   65% |
| ./ws     |   400 |   600 |   43% |
| ziohttp  |   925 |  1387 |  100% |
| akkahttp |   150 |   225 |   16% |
