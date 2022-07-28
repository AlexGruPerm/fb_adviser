package app

import com.bot4s.telegram.clients.SttpClient
import fb.{AppConfig, BotConfig, DbConfig}
import services.{FbDownloader, FbDownloaderImpl, PgConnection, PgConnectionImpl, TelegBot, TelegBotImpl}
import sttp.client3.{HttpClientSyncBackend, SttpBackend}
import sttp.client3.asynchttpclient.zio.AsyncHttpClientZioBackend
import sttp.client3.httpclient.zio.HttpClientZioBackend
import zio.Clock.ClockLive
import zio.Console.ConsoleLive
import zio.logging._
import zio.{Clock, Console, Layer, RLayer, Schedule, Scope, Task, ULayer, URLayer, ZIO, ZIOAppArgs, ZIOAppDefault, ZLayer, durationInt}
import com.typesafe.config.{Config, ConfigFactory}
import org.slf4j.LoggerFactory

import scala.reflect.io.File
import java.io
import java.time.Duration

/**
 *
 * The 3 Laws of ZIO Environment:
  1. Methods inside service definitions (traits) should NEVER use the environment
  2. Service implementations (classes) should accept all dependencies in constructor
  3. All other code ('business logic') should use the environment to consume services https://t.co/iSWzMhotOv
 *
 */

/**
 * Independent services for:
 * 1) download json as String (source raw json)
 * 2) parse source String and return Seq[Event]
 * 3) save Seq[Event] into database - not implemented yet
 * 4) State of last parsing ZIO.Ref[timestamp + Seq[Event]]
 * 5) bot core implementation (not reference to any services)
*/
object MainApp extends ZIOAppDefault{

  val parserEffect :ZIO[PgConnection with SttpBackend[Task, Any] with FbDownloader with TelegBot, Throwable, Unit] =
    for {
      //console <- ZIO.console
      fbdown <- ZIO.service[FbDownloader]
      bot <- ZIO.service[TelegBot]
      fbUrl =         //"https://line05w.bk6bba-resources.com/line/desktop/topEvents3?place=live&sysId=1&lang=ru&salt=33kcb6w4ydl56iud3p&supertop=4&scopeMarket=1600"
        //"https://line06w.bk6bba-resources.com/line/desktop/topEvents3?place=live&sysId=1&lang=ru&salt=1jy2797i10bl58mhxjm&supertop=4&scopeMarket=1600"
        //todo: here we need new parser with new c.c.
        //"https://line53w.bk6bba-resources.com/events/list?lang=ru&version=8639286626&scopeMarket=1600"
        //"https://line32w.bk6bba-resources.com/line/desktop/topEvents3?place=live&sysId=1&lang=ru&salt=10i7oc9ftkdl59yfmc1&supertop=4&scopeMarket=1600"
        "https://line06w.bk6bba-resources.com/line/desktop/topEvents3?place=live&sysId=1&lang=ru&salt=7u4qrf8pq08l5a08288&supertop=4&scopeMarket=1600"

      logicFb <- fbdown.getUrlContent(fbUrl).repeat(Schedule.spaced(60.seconds)).forkDaemon
      logicBot <- bot.runBot.repeat(Schedule.spaced(3.seconds)).forkDaemon

      _ <- logicFb.join
      _ <- logicBot.join
  } yield ()

  val log = LoggerFactory.getLogger(getClass.getName)

  val args :List[String] = List("adviser\\src\\main\\resources\\control.conf")

  val config :AppConfig = try {
    if (args.isEmpty) {
      log.info("There is no external config file.")
      //ConfigFactory.load()
      throw new Exception("There is no external config file.")
    } else {
      val configFilename :String = System.getProperty("user.dir")+File.separator+args.head
      log.info("There is external config file, path="+configFilename)
      val fileConfig :Config = ConfigFactory.parseFile(new io.File(configFilename))
      ConfigHelper.getConfig(fileConfig)
    }
  } catch {
    case e:Exception =>
      log.error("ConfigFactory.load - cause:"+e.getCause+" msg:"+e.getMessage)
      throw e
  }

  /*
  in run parameter
  adviser\src\main\resources\control.conf
  */
  val mainApp: ZIO[Any, Throwable, Unit] = parserEffect.provide(
    ZLayer.succeed(config),
    PgConnectionImpl.layer,
    AsyncHttpClientZioBackend.layer(),
    FbDownloaderImpl.layer,
    TelegBotImpl.layer
    //ZLayer.Debug.tree,
  )

  /**
   * https://zio.github.io/zio-logging/docs/overview/overview_index.html#slf4j-bridge
   * import zio.logging.slf4j.Slf4jBridge
   * program.provideCustom(Slf4jBridge.initialize)
  */
    def run: ZIO[Any with ZIOAppArgs with Scope, Any, Any] = {
    mainApp.exitCode
  }
}
