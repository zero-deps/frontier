# Setup

```sh
ulimit -n 65536
sbt 'project benchmark' run
sbt 'project ws' run

ulimit -n 65536
sdk use java 11.0.24-tem
sbt 'project it' GatlingIt/test

websocat ws://localhost:9011/wsecho
```
