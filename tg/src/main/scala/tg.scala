package ftier

import ftier.ext.{*, given}
import ftier.http
import ftier.http.client.*
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import zio.*, clock.*, json.*, blocking.*

object tg:
  enum Update:
    case InlineQuery(id: Id, query: Query)
    case PrivateQuery(chatId: ChatId, query: Query)
    case ConnectedWebsite(chatId: ChatId)
    case OtherUpdate

  opaque type Id = String
  opaque type ChatId = Long
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

  def validate(hash: String, date: Long, data: String, token: String): ZIO[Clock, Invalid.type, Unit] =
    for
      sha256 <- IO.effectTotal(MessageDigest.getInstance("SHA-256").nn)
      hmac_sha256 <- IO.effectTotal(Mac.getInstance("HmacSHA256").nn)
      secret_key <- IO.effectTotal(sha256.digest(token.getBytes("ascii").nn))
      skey <- IO.effectTotal(SecretKeySpec(secret_key, "HmacSHA256"))
      _ <- IO.effect(hmac_sha256.init(skey)).orDie
      mac_res <- IO.effect(hmac_sha256.doFinal(data.getBytes("utf8").nn).nn).orDie
      _ <- IO.when(mac_res.hex.utf8 != hash)(IO.fail(Invalid))
      now_sec <- currentTime(TimeUnit.SECONDS)
      _ <- IO.when(now_sec - date > 86400)(IO.fail(Invalid))
    yield ()

  object push:
    def sendMessage(token: String, text: String, telegramId: ChatId, muted: Boolean): URIO[Blocking, Unit] =
      (for
        url <- IO.succeed(s"https://api.telegram.org/bot$token/sendMessage")
        payload <- IO.effect(s"chat_id=$telegramId&disable_notification=$muted&text="+URLEncoder.encode(text, "utf8")).orDie
        cp <- connectionPool
        _ <- send(cp, http.Request("POST", url, Map("Content-Type" -> "application/x-www-form-urlencoded"), payload))
      yield ()).catchAll{
        case http.client.Timeout => IO.unit
      }
  end push

  object reader:
    /** @see https://core.telegram.org/bots/api#chat */
    case class Chat(id: Long)
    /** @see https://core.telegram.org/bots/api#message */
    case class Message(connected_website: Option[String], chat: Chat, text: Option[String])
    /** @see https://core.telegram.org/bots/api#inlinequery */
    case class InlineQuery(id: String, query: String)
    /** @see https://core.telegram.org/bots/api#update */
    case class UpdateSchema(message: Option[Message], inline_query: Option[InlineQuery])
    given JsonDecoder[Chat] = DeriveJsonDecoder.gen
    given JsonDecoder[Message] = DeriveJsonDecoder.gen
    given JsonDecoder[InlineQuery] = DeriveJsonDecoder.gen
    given JsonDecoder[UpdateSchema] = DeriveJsonDecoder.gen

    def find[R, E](xs: Chunk[Byte])(f: Update => ZIO[R, E, Chunk[Byte]]): ZIO[R, E | String, Chunk[Byte]] =
      for
        schema <- ZIO.fromEither(String(xs.toArray).fromJson[UpdateSchema])
        update =
          schema match
            case UpdateSchema(Some(Message(Some(_), Chat(id), _)), None) =>
              Update.ConnectedWebsite(id)
            case UpdateSchema(Some(Message(None, Chat(id), Some(text))), None) =>
              Update.PrivateQuery(id, text)
            case UpdateSchema(None, Some(InlineQuery(id, query))) =>
              Update.InlineQuery(id, query)
            case _ =>
              Update.OtherUpdate
        r <- f(update)
      yield r
  end reader

  sealed trait ReplyMarkup
  case class ReplyKeyboardMarkup(keyboard: Seq[Seq[String]], resize_keyboard: Boolean=true) extends ReplyMarkup

  object writer:
    def hash(q: Query): UIO[Hash] =
      md5(q.getBytes("utf8").nn).orDie

    /** @see https://core.telegram.org/bots/api#answerinlinequery */
    case class AnswerInlineQuery
      ( inline_query_id: String
      , method: String = "answerInlineQuery"
      , results: Seq[InlineQueryResult]
      )
    /** @see https://core.telegram.org/bots/api#inlinequeryresult */
    sealed trait InlineQueryResult
    /** @see https://core.telegram.org/bots/api#inlinequeryresultarticle */
    case class InlineQueryResultArticle
      ( id: String
      , input_message_content: InputMessageContent
      , title: String
      , `type`: String = "article"
      ) extends InlineQueryResult
    /** @see https://core.telegram.org/bots/api#inputmessagecontent */
    sealed trait InputMessageContent
    /** @see https://core.telegram.org/bots/api#inputtextmessagecontent */
    case class InputTextMessageContent
      ( message_text: String
      ) extends InputMessageContent
    given JsonEncoder[InputTextMessageContent] = DeriveJsonEncoder.gen
    given JsonEncoder[InputMessageContent] = DeriveJsonEncoder.gen
    given JsonEncoder[InlineQueryResultArticle] = DeriveJsonEncoder.gen
    given JsonEncoder[InlineQueryResult] = DeriveJsonEncoder.gen
    given JsonEncoder[AnswerInlineQuery] = DeriveJsonEncoder.gen

    def answerInlineQuery(id: Id, queryRes: QueryRes, hash: Hash, query: Query): String =
      AnswerInlineQuery
        ( inline_query_id = id
        , results =
            InlineQueryResultArticle
              ( id = hash
              , input_message_content = InputTextMessageContent(s"$query = $queryRes")
              , title = queryRes
              )
            :: Nil
        )
        .toJson

    case class AnswerPrivateQuery
      ( method: String = "sendMessage"
      , chat_id: Long
      , text: String
      , disable_notification: Boolean
      , reply_markup: Option[ReplyMarkup]
      , parse_mode: String = "HTML"
      )
    given JsonEncoder[ReplyKeyboardMarkup] = DeriveJsonEncoder.gen
    given JsonEncoder[ReplyMarkup] = DeriveJsonEncoder.gen
    given JsonEncoder[AnswerPrivateQuery] = DeriveJsonEncoder.gen

    def answerPrivateQuery(chatId: ChatId, queryRes: QueryRes, rm: Option[ReplyMarkup]=None): String =
      AnswerPrivateQuery
        ( chat_id=chatId
        , text=queryRes
        , disable_notification=true
        , reply_markup=rm
        )
        .toJson

    case class AnswerConnectedWebsite
      ( method: String = "sendMessage"
      , chat_id: Long
      , text: String
      , disable_notification: Boolean
      )
    given JsonEncoder[AnswerConnectedWebsite] = DeriveJsonEncoder.gen

    def answerConnectedWebsite(text: String, chatId: ChatId): String =
      AnswerConnectedWebsite
        ( chat_id=chatId
        , text=text
        , disable_notification=true
        )
        .toJson
  end writer
end tg
