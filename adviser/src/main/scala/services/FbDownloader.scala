package services

import sttp.capabilities.WebSockets
import sttp.capabilities.zio.ZioStreams
import sttp.client3.{SttpBackend, UriContext, basicRequest}
import sttp.client3.httpclient.zio.HttpClientZioBackend
import zio.{Clock, Console, Scope, Task, TaskLayer, ULayer, URIO, URLayer, ZIO, ZLayer}
import sttp.client3._
import sttp.client3.circe._
import sttp.client3.asynchttpclient.zio._
import io.circe.generic.auto._
import zio._
import zio.Console
/**
 *
 * The 3 Laws of ZIO Environment:
  1. Methods inside service definitions (traits) should NEVER use the environment
  2. Service implementations (classes) should accept all dependencies in constructor
  3. All other code ('business logic') should use the environment to consume services https://t.co/iSWzMhotOv
 *
 */
/**
 * Service pattern 2:
 * 1. define interface
 * 2. accessor method inside companion objects
 * 3. implementation of service interface
 * 4. converting service implementation into ZLayer
 */
  //1. service interface - read Json string from given url.
  trait FbDownloader {
    def getUrlContent(url: String): Task[String]
  }

  //2. accessor method inside companion objects
  object FbDownloader {
    def download(url: String): ZIO[FbDownloader, Throwable, String] =
      ZIO.serviceWithZIO(_.getUrlContent(url))
  }

  //3. Service implementations (classes) should accept all dependencies in constructor
  case class FbDownloaderImpl(console: Console, clock: Clock, client: SttpClient) extends FbDownloader {
    override def getUrlContent(url: String): Task[String] =
      for {
        time <- clock.currentDateTime
        _ <- console.printLine(s"$time - $url")
        _ <- console.printLine("Begin request")
        basicReq = basicRequest.post(uri"$url")
        response <- client.send(basicReq)
        _ <- console.printLine(s" response statusText    = ${response.statusText}")
        _ <- console.printLine(s" response code          = ${response.code}")
        respText = response.body.toString
        _ <- console.printLine(s" response.body.toString = ${respText}")
        _ <- console.printLine(s" response.body.isLeft   =  ${response.body.isLeft}")
        _ <- console.printLine(s" response.body.isRight  =  ${response.body.isRight}")
        res <- ZIO.succeed(response.body.toString)
      } yield res
  }

  //4. converting service implementation into ZLayer
  object FbDownloaderImpl {
    val layer: ZLayer[SttpClient,Throwable,FbDownloader] =
      ZLayer {
        for {
          console <- ZIO.console
          clock <- ZIO.clock
          client <- ZIO.service[SttpClient]
        } yield FbDownloaderImpl(console,clock,client)
      }
  }



