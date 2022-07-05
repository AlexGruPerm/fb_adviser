package app

import services.{FbDownloader, FbDownloaderImpl}
import sttp.client3.HttpClientSyncBackend
import sttp.client3.asynchttpclient.zio.{AsyncHttpClientZioBackend, SttpClient}
import sttp.client3.httpclient.zio.HttpClientZioBackend
import zio.Clock.ClockLive
import zio.Console.ConsoleLive
import zio.logging._
import zio.{Clock, Console, Scope, ULayer, ZIO, ZIOAppArgs, ZIOAppDefault}

/**
 *
 * The 3 Laws of ZIO Environment:
  1. Methods inside service definitions (traits) should NEVER use the environment
  2. Service implementations (classes) should accept all dependencies in constructor
  3. All other code ('business logic') should use the environment to consume services https://t.co/iSWzMhotOv
 *
 */

 /**
  * GET and parse JSON using the ZIO async-http-client backend and circe
  * https://sttp.softwaremill.com/en/latest/examples.html
 */

/**
 * Independent services for:
 * 1) download json as String (source raw json)
 * 2) parse source String and return Seq[Event]
 * 3) save Seq[Event] into database - not implemented yet
 * 4) State of last parsing ZIO.Ref[timestamp + Seq[Event]]
 * 5) bot core implementation (not reference to any services)
*/
object MainApp extends ZIOAppDefault {

  val parserEffect :ZIO[SttpClient with FbDownloader, Throwable, Unit] =
    for {
      console <- ZIO.console
      fbdown <- ZIO.service[FbDownloader]
      result <- fbdown.getUrlContent(url =
         "https://line05w.bk6bba-resources.com/line/desktop/topEvents3?place=live&sysId=1&lang=ru&salt=33kcb6w4ydl56iud3p&supertop=4&scopeMarket=1600"
      )
      _ <- console.printLine(result)
  } yield ()

  val mainApp: ZIO[Any, Throwable, Unit] = parserEffect.provide(
    AsyncHttpClientZioBackend.layer(),
    FbDownloaderImpl.layer)


  /**
   * https://zio.github.io/zio-logging/docs/overview/overview_index.html#slf4j-bridge
   * import zio.logging.slf4j.Slf4jBridge
   * program.provideCustom(Slf4jBridge.initialize)
  */

  override def run: ZIO[Any with ZIOAppArgs with Scope, Any, Any] =
    mainApp.exitCode
}
