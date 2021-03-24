package ftier

import java.util.concurrent.TimeUnit
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import zio._, clock._

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

  def validate(hash: String, date: Long, data: String, token: String): ZIO[Clock, Err, Unit] = {
    for {
      sha256      <- IO.effectTotal(MessageDigest.getInstance("SHA-256"))
      hmac_sha256 <- IO.effectTotal(Mac.getInstance("HmacSHA256"))
      secret_key  <- IO.effectTotal(sha256.digest(token.getBytes("ascii")))
      skey        <- IO.effectTotal(new SecretKeySpec(secret_key, "HmacSHA256"))
      _           <- IO.effect(hmac_sha256.init(skey)).mapError(Throwed)
      mac_res     <- IO.effect(hmac_sha256.doFinal(data.getBytes("utf8"))).mapError(Throwed)
      _           <- IO.when(hex(mac_res) != hash)(IO.fail(TgErr.BadHash))
      now_sec     <- currentTime(TimeUnit.SECONDS)
      _           <- IO.when(now_sec - date > 86400)(IO.fail(TgErr.Outdated))
    } yield ()
  }

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

    def answerConnectedWebsite(text: String, chatId: ChatId): Array[Byte] = {
      json.writeValueAsBytes(AnswerConnectedWebsite(
        chat_id=chatId.chatId
      , text=text
      , disable_notification=true
      ))
    }
  }
}
