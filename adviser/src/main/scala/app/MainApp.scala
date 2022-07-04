package app


import services.{FbDownloader, FbDownloaderImpl}
import zio.Clock.ClockLive
import zio.Console.ConsoleLive
import zio.{Clock, Console, Scope, ULayer, ZIO, ZIOAppArgs, ZIOAppDefault}

/**
 * Independent services for:
 * 1) download json as String (source raw json)
 * 2) parse source String and return Seq[Event]
 * 3) save Seq[Event] into database - not implemented yet
 * 4) State of last parsing ZIO.Ref[timestamp + Seq[Event]]
 * 5) bot core implementation (not reference to any services)

*/
object MainApp extends ZIOAppDefault {

  val parserEffect :ZIO[FbDownloader, Throwable, Unit] =
    for {
     fbdown <- ZIO.service[FbDownloader]
    _ <- fbdown.download("http://xz.ru")
  } yield ()

  val mainApp: ZIO[Any, Throwable, Unit] = parserEffect.provide(FbDownloaderImpl.layer)

  override def run: ZIO[Any with ZIOAppArgs with Scope, Any, Any] =
    mainApp.exitCode
}
