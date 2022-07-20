package services
import zio._

trait TelegBot {
 def run :UIO[Unit]
}

case class TelegBotImpl(console: Console) extends TelegBot{
  override def run: UIO[Unit] =
    for {
    _ <- console.printLine("~~~~~~~~~~~~~~~ we are here ~~~~~~~~~~~~~~~~").orDie
  } yield ()
}

object TelegBotImpl{
  val layer = ZLayer{
    for {
      console <- ZIO.console
    } yield TelegBotImpl(console)
  }
}