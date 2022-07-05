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
    def getUrlContent(url: String): Task[LiveEventsResponse]
  }

  //2. accessor method inside companion objects
  object FbDownloader {
    def download(url: String): ZIO[FbDownloader, Throwable, LiveEventsResponse] =
      ZIO.serviceWithZIO(_.getUrlContent(url))
  }


  //3. Service implementations (classes) should accept all dependencies in constructor
  case class FbDownloaderImpl(console: Console, clock: Clock, client: SttpClient) extends FbDownloader {

    val _LiveEventsResponse = root.value.result.string

    override def getUrlContent(url: String): Task[LiveEventsResponse] =
      for {
        time <- clock.currentDateTime
        _ <- console.printLine(s"$time - $url")
        _ <- console.printLine("Begin request")
        //basicReq  = basicRequest.post(uri"$url")
        basicReq  = basicRequest.post(uri"$url").response(asJson[LiveEventsResponse])
        response <- client.send(basicReq)
        _ <- console.printLine(s" response statusText    = ${response.statusText}")
        _ <- console.printLine(s" response code          = ${response.code}")


        //_ <- console.printLine(s" RES = ${response.body.right.get} ")
        _ <- console.printLine(" ")
        _ <- console.printLine("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~")
        _ <- console.printLine(s"   Events count = ${response.body.right.get.events.size}")
        _ <- console.printLine("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~")
        evs = response.body.right.get.events
        evh = evs.head

        //full output one event
        _ <- ZIO.foreach(evs.filter(e => e.markets.nonEmpty && e.markets.exists(mf => mf.ident == "Results"))){
          e => console.printLine(s" ${e.id} - ${e.skName} -[ ${e.team1} - ${e.team2} ] - ${e.place} - ${e.timer}") *>
            //console.printLine(s"  market_count = ${e.markets.size}") *>
            ZIO.foreach(e.markets.filter(mf => mf.ident == "Results" && mf.rows.nonEmpty && mf.rows.size == 2)){
              m => console.printLine(s"   ${m.marketId} - ${m.caption} - ${m.ident} - ${m.sortOrder} - rows [${m.rows.size}]") *>
                //ZIO.foreach(m.rows) {r => console.printLine(s"  ROW isTitle = ${r.isTitle} - CELLS SIZE = ${r.cells.size}") *>
                (if (m.rows.nonEmpty &&
                  m.rows.size == 2 &&
                  m.rows(0).cells.nonEmpty &&
                  m.rows(0).cells.size == 4 &&
                  m.rows(1).cells.nonEmpty &&
                  m.rows(1).cells.size == 4 //todo: add more filter.
                    ) {
                    val r0 = m.rows(0)
                    val r1 = m.rows(1)
                    console.printLine{s"${r0.cells(1).caption} - ${r1.cells(1).value} score (this team) : ${e.scores(0).head.c1}"} *>
                      console.printLine(s"${r0.cells(2).caption} - ${r1.cells(2).value}") *>
                      console.printLine(s"${r0.cells(3).caption} - ${r1.cells(3).value} score (this team) : ${e.scores(0).head.c1}")
                  /*
                     s" ${._1.isTitle} - [${zs._1.eventid} - ${zs._2.eventid}] - " +
                        s"[${zs._1.factorid} - ${zs._2.factorid}] - " +
                        s"[${zs._1.caption} - ${zs._2.caption}] - " +
                        s"[${zs._1.value} - ${zs._2.value}]"
                  */
                } else {
                  console.printLine("not interested!!!")
                })




                  /*
                  (if (r.cells.nonEmpty &&
                      r.cells.size==2 &&
                      r.cells(0).isTitle == Some(true) &&
                      r.cells(1).isTitle == None){
                    ZIO.foreach( Seq(r.cells(0)).zip(Seq(r.cells(1)))) {zs =>
                      console.printLine{
                        s" ${zs._1.isTitle} - [${zs._1.eventid} - ${zs._2.eventid}] - " +
                          s"[${zs._1.factorid} - ${zs._2.factorid}] - " +
                          s"[${zs._1.caption} - ${zs._2.caption}] - " +
                          s"[${zs._1.value} - ${zs._2.value}]"
                      }
                    }
                    } else {
                      console.printLine("not interested!!!")
                    })
                  */
                  }          *>
            console.printLine(" ") *>
            console.printLine("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~") *>
            console.printLine(" ")

                }



        /*
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
        _ <- ZIO.foreach(fm.rows){r => console.printLine(s"    row = ${r} ")}
        fc = fr.cells.head
        _ <- console.printLine("~~~~~~~~~~~~~~~ First cell in first row ~~~~~~~~~")
        _ <- console.printLine(s"                 first row.isTitle    = ${fc.isTitle}")
        _ <- console.printLine(s"                 first row.value      = ${fc.value}")
        _ <- console.printLine(s"                 first row.caption    = ${fc.caption}")
        _ <- console.printLine(s"                 first row.eventid    = ${fc.eventid}")
        _ <- console.printLine(s"                 first row.factorid   = ${fc.factorid}")
        _ <- console.printLine("~~~~~~~~~~~~~~~ First cell in first row ~~~~~~~~~")
        _ <- ZIO.foreach(fr.cells){c => console.printLine(s" cell = ${c.isTitle} - ${c.eventid} - ${c.factorid} - ${c.caption} - ${c.value} ")}
        */

        _ <- console.printLine("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~")
        res <- ZIO.succeed(response.body.right.get)
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



