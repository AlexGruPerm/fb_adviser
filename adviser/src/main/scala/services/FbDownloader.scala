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
          s"insert into events(fba_load_id, event_id,event_number,competitionName, skid, skname, timerSeconds, team1Id,team1, team2Id,team2, startTimeTimestamp, eventName) values(?,?,?,?,?,?,?,?,?,?,?,?,?);"
          ,Statement.RETURN_GENERATED_KEYS)
        _ <- ZIO.foreach(evs.filter(ei => ei.markets.nonEmpty && ei.timer.nonEmpty && ei.markets.exists(mf => mf.ident == "Results"))){ev =>
              pstmt.setLong(1, idFbaLoad)
              pstmt.setLong(2, ev.id)
              pstmt.setLong(3, ev.number)
              pstmt.setString(4, ev.competitionName)
              pstmt.setLong(5, ev.skId)
              pstmt.setString(6, ev.skName)
              pstmt.setLong(7, ev.timerSeconds.getOrElse(0L))
              pstmt.setLong(8, ev.team1Id)
              pstmt.setString(9, ev.team1)
              pstmt.setLong(10, ev.team2Id)
              pstmt.setString(11, ev.team2)
              pstmt.setLong(12, ev.startTimeTimestamp)
              pstmt.setString(13, ev.eventName)
              val resInsertEvent = pstmt.executeUpdate()
              val keysetEvnts = pstmt.getGeneratedKeys()
              keysetEvnts.next()
              val idFbaEvent = keysetEvnts.getInt(1)
              console.printLine(s" Event insertion ${resInsertEvent} row with Event ID = ${idFbaEvent}")
              //scores insert
              ZIO.foreach(ev.markets.filter(mf => mf.ident == "Results" && mf.rows.nonEmpty && mf.rows.size >= 2)){m =>
                (if (m.rows.nonEmpty &&
                  m.rows.size >= 2 &&
                  m.rows(0).cells.nonEmpty &&
                  m.rows(0).cells.size >= 4 &&
                  m.rows(1).cells.nonEmpty &&
                  m.rows(1).cells.size >= 4 //todo: add more filter.
                ) {
                  val r0 = m.rows(0)
                  val r1 = m.rows(1)

                  val pstmtS = pgc.prepareStatement(s"insert into score(events_id,team1,team1Coeff, team1score, draw, draw_coeff, team2Coeff, team2, team2score) values(?,?,?,?,?,?,?,?,?);")

                  pstmtS.setLong(1, idFbaEvent)
                  pstmtS.setString(2,r0.cells(1).caption.getOrElse("*"))
                  pstmtS.setDouble(3,r1.cells(1).value.getOrElse(0.0))
                  pstmtS.setString(4,ev.scores(0).head.c1)
                  pstmtS.setString(5,r0.cells(2).caption.getOrElse("*"))
                  pstmtS.setDouble(6,r1.cells(2).value.getOrElse(0.0))
                  pstmtS.setDouble(7,r1.cells(3).value.getOrElse(0.0))
                  pstmtS.setString(8,r0.cells(3).caption.getOrElse("*"))
                  pstmtS.setString(9,ev.scores(0).head.c2)

                  val resInsertEventScore = pstmtS.executeUpdate()
                  console.printLine(s" Scores inserted : ${resInsertEventScore}")
                    //console.printLine{s"${r0.cells(1).caption} - ${r1.cells(1).value} score (this team) : ${ev.scores(0).head.c1}"} *>
                    //console.printLine(s"${r0.cells(2).caption} - ${r1.cells(2).value}") *>
                    //console.printLine(s"${r0.cells(3).caption} - ${r1.cells(3).value} score (this team) : ${ev.scores(0).head.c1}")
                } else {
                  console.printLine("not interested!!!")
                })
              }
        }

        //full output one event
        _ <- ZIO.foreach(evs.filter(ei => ei.markets.nonEmpty && ei.timer.nonEmpty && ei.markets.exists(mf => mf.ident == "Results"))){
          e => console.printLine(s" ${e.id} - ${e.skName} -[ ${e.team1} - ${e.team2} ] - ${e.place} - ${e.timer}") *>
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
                  }
        }

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
          _ <- console.printLine(s"[FbDownloaderImpl] connection isOpened = ${!c.isClosed}")
        } yield FbDownloaderImpl(console,clock,client,conn)
      }
  }



