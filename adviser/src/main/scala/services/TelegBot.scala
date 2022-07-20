package services
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

  override def runBot: UIO[Unit] =
    for {
    _ <- console.printLine("~~~~~~~~~~~~~~~ we are here ~~~~~~~~~~~~~~~~").orDie
  } yield ()
}

object TelegBotImpl{
  val layer /*:ZLayer[AppConfig,Throwable,TelegBotImpl]*/ = ZLayer{
    for {
      console <- ZIO.console
      conf <- ZIO.service[AppConfig]
    } yield TelegBotImpl(console,conf)
  }
}