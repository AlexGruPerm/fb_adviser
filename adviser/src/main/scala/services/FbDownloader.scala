package services

import fb.LiveEventsResponse
import io.circe.Json
import sttp.capabilities.WebSockets
import sttp.capabilities.zio.ZioStreams
import sttp.client3.{SttpBackend, UriContext, basicRequest}
import sttp.client3.httpclient.zio.HttpClientZioBackend
import zio.{Clock, Console, Scope, Task, TaskLayer, ULayer, URIO, URLayer, ZIO, ZLayer}
import sttp.client3._
import sttp.client3.asynchttpclient.zio._
import zio._
import zio.Console
import io.circe.generic.auto._
import io.circe.syntax.EncoderOps
import sttp.client3.circe._
import io.circe._
import io.circe.parser._
import io.circe.optics.JsonPath._
import io.circe.{Decoder, Encoder}
import io.circe.generic.auto._
import io.circe.optics.JsonPath
import io.circe.syntax._


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

    val _LiveEventsResponse = root.value.result.string

    override def getUrlContent(url: String): Task[String] =
      for {
        time <- clock.currentDateTime
        _ <- console.printLine(s"$time - $url")
        _ <- console.printLine("Begin request")
        //basicReq  = basicRequest.post(uri"$url")
        basicReq  = basicRequest.post(uri"$url").response(asJson[LiveEventsResponse])
        response <- client.send(basicReq)
        _ <- console.printLine(s" response statusText    = ${response.statusText}")
        _ <- console.printLine(s" response code          = ${response.code}")

        _ <- console.printLine("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~")
        _ <- console.printLine(s" RES = ${response.body.right.get} ")
        _ <- console.printLine(" ")
        _ <- console.printLine("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~")
        _ <- console.printLine(s"   Events count = ${response.body.right.get.events.size}")
        evh = response.body.right.get.events.head
        _ <- console.printLine(s"     First Event Id = ${evh.id}")
        _ <- console.printLine(s"     First Event skName        = ${evh.skName}")
        _ <- console.printLine(s"     First Event timer         = ${evh.timer}")
        _ <- console.printLine(s"     First Event timerSeconds  = ${evh.timerSeconds}")
        _ <- console.printLine(s"     First Event scoreFunction = ${evh.scoreFunction}")
        _ <- console.printLine("~~~~~~~~ First market in first event ~~~~~~~~~")
        fm = evh.markets.head
        _ <- console.printLine(s"       marketId  = ${fm.marketId}")
        _ <- console.printLine(s"       ident     = ${fm.ident}")
        _ <- console.printLine(s"       sortOrder = ${fm.sortOrder}")
        _ <- console.printLine(s"       caption   = ${fm.caption}")
        _ <- console.printLine(s"        rows count   = ${fm.rows.size}")
        _ <- console.printLine("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~")
        fr = fm.rows.head
        _ <- console.printLine("~~~~~~~~~~ First row in first market ~~~~~~~~~")
        _ <- console.printLine(s"           first row.isTitle    = ${fr.isTitle}")
        _ <- console.printLine(s"           first row.cells.size = ${fr.cells.size}")
        _ <- console.printLine("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~")
        fc = fr.cells.head
        _ <- console.printLine("~~~~~~~~~~~~~~~ First cell in first row ~~~~~~~~~")
        _ <- console.printLine(s"                 first row.isTitle    = ${fc.isTitle}")
        _ <- console.printLine(s"                 first row.value      = ${fc.value}")
        _ <- console.printLine(s"                 first row.caption    = ${fc.caption}")
        _ <- console.printLine(s"                 first row.eventid    = ${fc.eventid}")
        _ <- console.printLine(s"                 first row.factorid   = ${fc.factorid}")
        _ <- console.printLine("~~~~~~~~~~~~~~~ First cell in first row ~~~~~~~~~")

        /*
        _ <- ZIO.succeed(fm.rows match {
          case Some(Seq(rws)) =>  console.printLine(s"     ${rws.isTitle} - ${rws.cells.getOrElse(Seq.empty).size}")
          case None => console.printLine(s"         rows count  = NO ROWS ")
        })
       // _ <- console.printLine(s"         rows count  = ${rw.isTitle}")
        */
        _ <- console.printLine("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~")
        res <- ZIO.succeed(response.body.right.toString)
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



