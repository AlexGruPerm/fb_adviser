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

import java.sql.Statement


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
  case class FbDownloaderImpl(console: Console, clock: Clock, client: SttpClient, conn: PgConnection) extends FbDownloader {

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

        //todo: we need combine result data and execute inserts
        // with using evs
        // construct c.c. as data structure to future use to inserts.

        pgc <- conn.connection
        //INSERT INTO pages VALUES(DEFAULT) RETURNING id;
        pstmt  = pgc.prepareStatement("insert into fba_load default values;",Statement.RETURN_GENERATED_KEYS)
        resInsert = pstmt.executeUpdate()
        keyset = pstmt.getGeneratedKeys()
        _ = keyset.next()
        idFbaLoad = keyset.getInt(1)
        _ <- console.printLine(s" insertion ${resInsert} row with ID = ${idFbaLoad}")
        pstmt = pgc.prepareStatement(
          s"insert into events(fba_load_id, skid, skname, team1Id,team1, team2Id,team2, startTimeTimestamp, eventName) values(?,?,?,?,?,?,?,?,?);")
        _ <- ZIO.foreach(evs){ev =>
              pstmt.setLong(1, idFbaLoad)
              pstmt.setLong(2, ev.skId)
              pstmt.setString(3, ev.skName)
              pstmt.setLong(4, ev.team1Id)
              pstmt.setString(5, ev.team1)
              pstmt.setLong(6, ev.team2Id)
              pstmt.setString(7, ev.team2)
              pstmt.setLong(8, ev.startTimeTimestamp)
              pstmt.setString(9, ev.eventName)
              ZIO.succeed(pstmt.executeUpdate())
        }

          /*
          ######################################
        //full output one event
        _ <- ZIO.foreach(evs.filter(e => e.markets.nonEmpty && e.timer.nonEmpty && e.markets.exists(mf => mf.ident == "Results"))){
          e => console.printLine(s" ${e.id} - ${e.skName} -[ ${e.team1} - ${e.team2} ] - ${e.place} - ${e.timer}") *>
            //console.printLine(s"  market_count = ${e.markets.size}") *>
            ZIO.foreach(e.markets.filter(mf => mf.ident == "Results" && mf.rows.nonEmpty && mf.rows.size >= 2)){
              m => console.printLine(s"   ${m.marketId} - ${m.caption} - ${m.ident} - ${m.sortOrder} - rows [${m.rows.size}]") *>
                //ZIO.foreach(m.rows) {r => console.printLine(s"  ROW isTitle = ${r.isTitle} - CELLS SIZE = ${r.cells.size}") *>
                (if (m.rows.nonEmpty &&
                  m.rows.size >= 2 &&
                  m.rows(0).cells.nonEmpty &&
                  m.rows(0).cells.size >= 4 &&
                  m.rows(1).cells.nonEmpty &&
                  m.rows(1).cells.size >= 4 //todo: add more filter.
                    ) {
                    val r0 = m.rows(0)
                    val r1 = m.rows(1)
                    console.printLine{s"${r0.cells(1).caption} - ${r1.cells(1).value} score (this team) : ${e.scores(0).head.c1}"} *>
                      console.printLine(s"${r0.cells(2).caption} - ${r1.cells(2).value}") *>
                      console.printLine(s"${r0.cells(3).caption} - ${r1.cells(3).value} score (this team) : ${e.scores(0).head.c1}")
                } else {
                  console.printLine("not interested!!!")
                })
                  }          *>
            console.printLine(" ") *>
            console.printLine("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~") *>
            console.printLine(" ") *>
            console.printLine("EXECUTE INSERT ")
                    ######################################
                    */

            /*
            *>
            ZIO.attempt{
              for {
                pgc <- conn.connection
                pstmt  = pgc.prepareStatement("insert into events(skid) values(?);")
                _ = pstmt.setInt(1,123)
                res = pstmt.executeUpdate()
                _ <- console.printLine(s" result of insertion is : [${res}]")
              } yield res
            }
                  */
            //conn.execute(s" insert into fba.events(skid) values(${e.skId});") *>
            /*
            conn.execute(s" insert into events(skid,skname,team1Id,team1,team2Id,team2) " +
              s"values(${e.skId},${e.skName},${e.team1Id},${e.team1},${e.team2Id},${e.team2});") *>
            */
            //conn.execute("commit;")

                //}



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
    val layer: ZLayer[SttpClient with PgConnection,Throwable,FbDownloader] =
      ZLayer {
        for {
          console <- ZIO.console
          clock <- ZIO.clock
          client <- ZIO.service[SttpClient]
          conn <- ZIO.service[PgConnection]
          c <- conn.connection
          _ <- console.printLine(s"connection isOpened = ${!c.isClosed}")
        } yield FbDownloaderImpl(console,clock,client,conn)
      }
  }



