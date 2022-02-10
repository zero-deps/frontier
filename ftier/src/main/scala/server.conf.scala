package ftier

case class ServerConf(workers: Int)

object ServerConf:
    def default: ServerConf = ServerConf(workers = 1)
