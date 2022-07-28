package services

import com.bot4s.telegram.cats.TelegramBot
import org.asynchttpclient.Dsl.asyncHttpClient
import zio.{Task, URIO, _}
import zio.interop.catz._
import sttp.client3.asynchttpclient.zio.AsyncHttpClientZioBackend
import com.bot4s.telegram.api.declarative.Commands

import scala.runtime.Nothing$
//import com.bot4s.telegram.methods._
import zhttp.http._
import com.bot4s.telegram.methods.SetWebhook
import zhttp.service.Server
import zhttp.service.server.ServerChannelFactory
import zhttp.service.EventLoopGroup
import com.bot4s.telegram.models.Update

abstract class FbBot(val token: String)
  extends TelegramBot[Task](token, AsyncHttpClientZioBackend.usingClient(zio.Runtime.default, asyncHttpClient()))

class telegBotZio(token: String, private val started: Ref.Synchronized[Boolean])
  extends FbBot(token)
    with Commands[Task] {

  import com.bot4s.telegram.marshalling._
  val webhookUrl = "https://XXXX.eu.ngrok.io"

  private def callback: Http[Any,Throwable,Request,Response] = Http.collectZIO[Request]{
    case req @ Method.POST -> !! =>
    for {
      body    <- req.bodyAsString
      update  <- ZIO.attempt(fromJson[Update](body))
      /*handler*/_ <- receiveUpdate(update, None)
    } yield Response.ok
  }

  private def server = Server.port(22/*8081*/) ++ Server.app(callback)

  override def run() =
    started.updateZIO { isStarted =>
      for {
        _ <- ZIO.when(isStarted)(ZIO.fail(new Exception("Bot already started")))
        response <-
          request(SetWebhook(url = webhookUrl, certificate = None, allowedUpdates = None)).flatMap {
            case true => ZIO.succeed(true)
            case false =>
              ZIO.logError("Failed to set webhook")
              throw new RuntimeException("Failed to set webhook")
          }
      } yield response
    } *>
      server.make
        .flatMap(start => ZIO.logInfo(s"Server started on ${start.port}") *> ZIO.never)
        .provide(ServerChannelFactory.auto, EventLoopGroup.auto(1), Scope.default)

  // String commands.
  onCommand("/hello") { implicit msg =>
    reply("Hello America!").ignore
  }
  /*
  onCommand("command") { implicit msg =>
    withArgs { args =>
      for {
        _ <- reply(s"Timer set: 3 second(s)")
        _ <- ZIO.sleep(3.seconds)
        _ <- reply(args.mkString(" "))
      } yield ()
    }
  }
  */

}

/*

  //1. trait
  trait FbBotZio{
    def runBot(token: String) :URIO[Console,Unit]/*ZIO[Any,Throwable,Nothing]*/ =
    Ref.Synchronized.make(false).flatMap { started =>
      new telegBotZio(token, started).run()
    }.catchAll{
      case ex: Throwable => console.printLine(s" Exception FbBotZio.runBot ${ex.getMessage} - ${ex.getCause} ").orDie
    }


    //3. Service implementations (classes) should accept all dependencies in constructor
    case class FbBotZioImpl(console: Console, clock: Clock, token: String, conn: PgConnection) extends FbBotZio {

      override def runBot(token: String): URIO[Console, Unit] =
        for {
          _ <- console.printLine("~~~~~~~~~~~~~~~~~~~~~~ FbBotZioImpl ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~").orDie
        } yield ()

    }

    //4. converting service implementation into ZLayer
    object FbBotZioImpl {
      val layer: ZLayer[PgConnection,Throwable,FbBotZio] =
        ZLayer {
          for {
            console <- ZIO.console
            clock <- ZIO.clock
            conn <- ZIO.service[PgConnection]
            c <- conn.connection
            _ <- console.printLine(s"[FbDownloaderImpl] connection isOpened = ${!c.isClosed}")
          } yield FbBotZioImpl(console,clock,"xxxxxxxxx",conn)
        }
    }

}
*/