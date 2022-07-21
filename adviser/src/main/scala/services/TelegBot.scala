package services
import com.bot4s.telegram.api.TelegramApiException
import com.bot4s.telegram.models.UpdateType
import com.bot4s.telegram.models.Message
import com.typesafe.config.{Config, ConfigFactory}
import fb.{AppConfig, telegBotWH}
import zio._

trait TelegBot {
 def runBot :UIO[Unit]
}

case class TelegBotImpl(console: Console,config: AppConfig) extends TelegBot{
  //body as constructor of bot.
  val bot = new telegBotWH(config.botConfig)
  val eol = bot.run

  import com.bot4s.telegram.api.RequestHandler
  import scala.concurrent.ExecutionContext.Implicits.global
  import scala.concurrent.Future
  import com.bot4s.telegram.api.RequestHandler
  import com.bot4s.telegram.methods.{ParseMode, SendMessage, SendPhoto}

  override def runBot: UIO[Unit] =
    for {
    _ <- console.printLine("~~~~~~~~~~~~~~~ we are here ~~~~~~~~~~~~~~~~").orDie
    msgText = "URA!!!!!"
     // chat(id)        = 322134338
    _ <- ZIO.attempt(bot.sendMsgToGroup(533534191L,"Зая, я тебя люблю!!!")).catchAll{
      error =>
        error match {
          case tex: TelegramApiException =>
            for {
              _ <- console.printLine(s"Telegram exception = [${tex.message}] - [${tex.errorCode}] - [${tex.cause}]").orDie
            } yield ()
          case err: Throwable =>
            for {
            _ <- console.printLine(s"method runBot, error = [${error.getMessage} - ${error.getCause}]").orDie
          } yield ()
        }
    }
  } yield ()
}

object TelegBotImpl{
  val layer  = ZLayer{
    for {
      console <- ZIO.console
      conf <- ZIO.service[AppConfig]
    } yield TelegBotImpl(console,conf)
  }
}