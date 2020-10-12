package ftier

import zio._

object telegram {
  sealed trait Update
  final case class InlineQuery(id: Id, query: Query) extends Update
  final case class PrivateQuery(chatId: ChatId, query: Query) extends Update
  final case class ConnectedWebsite(chatId: ChatId) extends Update
  final case object OtherUpdate extends Update

  final class Id(val id: String) extends AnyVal
  final class ChatId(val chatId: Int) extends AnyVal
  final class Query(val query: String) extends AnyVal {
    def isEmpty: Boolean = query.isEmpty
  }

  object QueryRes { def apply(q: String): QueryRes = new QueryRes(q) }
  final class QueryRes(val queryRes: String) extends AnyVal

  object Hash { def apply(h: String): Hash = new Hash(h) }
  final class Hash(val hash: String) extends AnyVal

  object push {
    import httpClient._
    import java.net.URLEncoder
    import zio.blocking._
    def sendMessage(token: String, text: String, telegramId: Int, muted: Boolean): ZIO[Blocking, Err, Unit] = {
      for {
        url     <- IO.succeed(s"https://api.telegram.org/bot$token/sendMessage")
        payload <- IO.effect(s"chat_id=$telegramId&disable_notification=$muted&text="+URLEncoder.encode(text, "utf8")).mapError(Throwed)
        cp      <- connectionPool
        _       <- send(cp, http.Request("POST", url, Map("Content-Type" -> "application/x-www-form-urlencoded"), Chunk.fromArray(payload.getBytes("utf8"))))
      } yield ()
    }
  }

  object reader {
    def find(xs: Array[Byte])(f: Update => ZIO[Any, Err, Array[Byte]]): ZIO[Any, Err, Array[Byte]] = {
      val obj = json.readTree(xs)
      val message = obj.get("message")
      val x =
        if (message != null) {
          if (message.get("connected_website") != null) {
            ConnectedWebsite(new ChatId(message.get("chat").get("id").asInt))
          } else {
            PrivateQuery(new ChatId(message.get("chat").get("id").asInt), new Query(message.get("text").asText))
          }
        } else {
          val iq = obj.get("inline_query")
          if (iq != null) {
            InlineQuery(new Id(iq.get("id").asText), new Query(iq.get("query").asText))
          } else {
            OtherUpdate
          }
        }
      f(x)
    }
  }

  object writer {
    def hash(q: Query): UIO[Hash] = {
      md5(q.query.getBytes("utf8")).map(Hash(_)).orDie
    }

    case class AnswerInlineQuery(
      inline_query_id: String
    , method: String = "answerInlineQuery"
    , results: Seq[Result]
    )
    case class Result(
      id: String
    , input_message_content: InputMessageContent
    , title: String
    , `type`: String = "article"
    )
    case class InputMessageContent(
      message_text: String
    )

    def answerInlineQuery(id: Id, queryRes: QueryRes, hash: Hash, query: Query): Array[Byte] = {
      json.writeValueAsBytes(AnswerInlineQuery(
        inline_query_id=id.id
      , results=Result(
          id=hash.hash
        , input_message_content=InputMessageContent(s"${query.query} = ${queryRes.queryRes}")
        , title=queryRes.queryRes
        ) :: Nil
      ))
    }

    case class AnswerPrivateQuery(
      method: String = "sendMessage"
    , chat_id: Int
    , text: String
    , disable_notification: Boolean
    )

    def answerPrivateQuery(chatId: ChatId, queryRes: Seq[QueryRes], query: Query): Array[Byte] = {
      json.writeValueAsBytes(AnswerPrivateQuery(
        chat_id=chatId.chatId
      , text=s"${query.query} = ${queryRes.map(_.queryRes).mkString}"
      , disable_notification=true
      ))
    }

    case class AnswerConnectedWebsite(
      method: String = "sendMessage"
    , chat_id: Int
    , text: String
    , disable_notification: Boolean
    )

    def answerConnectedWebsite(chatId: ChatId): Array[Byte] = {
      json.writeValueAsBytes(AnswerConnectedWebsite(
        chat_id=chatId.chatId
      , text="Welcome!"
      , disable_notification=true
      ))
    }
  }
}