package ftier

import java.util.concurrent.TimeUnit
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import zio.*, clock.*

object tg {
  enum Update:
    case InlineQuery(id: Id, query: Query)
    case PrivateQuery(chatId: ChatId, query: Query)
    case ConnectedWebsite(chatId: ChatId)
    case OtherUpdate

  opaque type Id = String
  opaque type ChatId = Int
  opaque type Query = String
  opaque type QueryRes = String
  opaque type Hash = String

  extension (x: ChatId)
    def toBytes: Array[Byte] = math.BigInt(x).toByteArray

  object Query:
    def unapply(x: Query): Option[String] = Some(x)

  object QueryRes:
    def apply(x: String): QueryRes = x

  object Invalid

  def validate(hash: String, date: Long, data: String, token: String): ZIO[Clock, Invalid.type, Unit] = {
    for {
      sha256      <- IO.effectTotal(MessageDigest.getInstance("SHA-256").nn)
      hmac_sha256 <- IO.effectTotal(Mac.getInstance("HmacSHA256").nn)
      secret_key  <- IO.effectTotal(sha256.digest(token.getBytes("ascii").nn))
      skey        <- IO.effectTotal(SecretKeySpec(secret_key, "HmacSHA256"))
      _           <- IO.effect(hmac_sha256.init(skey)).orDie
      mac_res     <- IO.effect(hmac_sha256.doFinal(data.getBytes("utf8").nn).nn).orDie
      _           <- IO.when(mac_res._hex._utf8 != hash)(IO.fail(Invalid))
      now_sec     <- currentTime(TimeUnit.SECONDS)
      _           <- IO.when(now_sec - date > 86400)(IO.fail(Invalid))
    } yield ()
  }

  object push {
    import http.client.*
    import java.net.URLEncoder
    import zio.blocking.*
    def sendMessage(token: String, text: String, telegramId: Int, muted: Boolean): ZIO[Blocking, BadUri, Unit] = {
      for {
        url     <- IO.succeed(s"https://api.telegram.org/bot$token/sendMessage")
        payload <- IO.effect(s"chat_id=$telegramId&disable_notification=$muted&text="+URLEncoder.encode(text, "utf8")).orDie
        cp      <- connectionPool
        _       <- send(cp, http.Request("POST", url, Map("Content-Type" -> "application/x-www-form-urlencoded"), Chunk.fromArray(payload.getBytes("utf8").nn)))
      } yield ()
    }
  }

  object reader {
    def find[R, E](xs: Chunk[Byte])(f: Update => ZIO[R, E, Chunk[Byte]]): ZIO[R, E, Chunk[Byte]] = {
      for {
        obj <- json.readTree(xs)
        message = obj.get("message").nn
        r <- f {
          if message != null then
            if message.get("connected_website") != null then
              Update.ConnectedWebsite(message.get("chat").get("id").asInt.nn)
            else
              Update.PrivateQuery(message.get("chat").get("id").asInt.nn, message.get("text").asText.nn)
          else
            val iq = obj.get("inline_query").nn
            if iq != null then
              Update.InlineQuery(iq.get("id").asText.nn, iq.get("query").asText.nn)
            else
              Update.OtherUpdate
        }
      } yield r
    }
  }

  sealed trait ReplyMarkup
  case class ReplyKeyboardMarkup(keyboard: Seq[Seq[String]], resize_keyboard: Boolean=true) extends ReplyMarkup

  object writer {
    def hash(q: Query): UIO[Hash] = {
      md5(q.getBytes("utf8").nn).orDie
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

    def answerInlineQuery(id: Id, queryRes: QueryRes, hash: Hash, query: Query): UIO[IArray[Byte]] = {
      json.encode(AnswerInlineQuery(
        inline_query_id=id
      , results=Result(
          id=hash
        , input_message_content=InputMessageContent(s"$query = $queryRes")
        , title=queryRes
        ) :: Nil
      ))
    }

    case class AnswerPrivateQuery(
      method: String = "sendMessage"
    , chat_id: Int
    , text: String
    , disable_notification: Boolean
    , reply_markup: Option[ReplyMarkup]
    , parse_mode: String = "HTML"
    )

    def answerPrivateQuery(chatId: ChatId, queryRes: QueryRes, rm: Option[ReplyMarkup]=None): UIO[Chunk[Byte]] = {
      json.encode(AnswerPrivateQuery(
        chat_id=chatId
      , text=queryRes
      , disable_notification=true
      , reply_markup=rm
      )).map(bs => Chunk.fromArray(bs.toArray))
    }

    case class AnswerConnectedWebsite(
      method: String = "sendMessage"
    , chat_id: Int
    , text: String
    , disable_notification: Boolean
    )

    def answerConnectedWebsite(text: String, chatId: ChatId): UIO[IArray[Byte]] = {
      json.encode(AnswerConnectedWebsite(
        chat_id=chatId
      , text=text
      , disable_notification=true
      ))
    }
  }
}
