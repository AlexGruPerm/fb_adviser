package services
import com.bot4s.telegram.api.TelegramApiException
import com.bot4s.telegram.methods.{ApproveChatJoinRequest, DeclineChatJoinRequest}
import com.bot4s.telegram.models.UpdateType
import com.bot4s.telegram.models.Message
import com.typesafe.config.{Config, ConfigFactory}
import fb.{AppConfig, telegBotWH}
import fb.Group
import zio._

import java.sql.ResultSet

trait TelegBot {
 def runBot :UIO[Unit]
}

case class TelegBotImpl(console: Console, config: AppConfig, conn: PgConnection) extends TelegBot{
  //body as constructor of bot.
  val bot = new telegBotWH(config.botConfig,conn)
  val eol = bot.run

  import com.bot4s.telegram.api.RequestHandler
  import scala.concurrent.ExecutionContext.Implicits.global
  import scala.concurrent.Future
  import com.bot4s.telegram.api.RequestHandler
  import com.bot4s.telegram.methods.{ParseMode, SendMessage, SendPhoto}


  def getActiveGroups: ZIO[Any,Nothing,List[Group]] =
    (for {
      pgConn <- conn.connection
      stm = pgConn.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)
      rs = stm.executeQuery("select t.groupid,t.firstname,t.lastname from tgroup t where t.is_blck_by_user_dt is null")
      //columns: IndexedSeq[(String,String)] = (1 to rs.getMetaData.getColumnCount)
      //  .map(cnum => (rs.getMetaData.getColumnName(cnum),rs.getMetaData.getColumnTypeName(cnum)))
      results/*: Iterator[IndexedSeq[Group]]*/ =
        Iterator.continually(rs).takeWhile(_.next()).map{
        rsi => Group(
          rsi.getLong("groupid"),
          rsi.getString("firstname"),
          rsi.getString("lastname")
        )
          //columns.map(cname => rsi.getString(cname._1))
      }.toList
      _ <- console.printLine(s"THERE IS ${results.size} ACTIVE GROUPS.")
    } yield results).catchAll{
      errPg => errPg match{
        case ex: Throwable =>
          for {
            _ <- console.printLine(s"FBAE-03 Can't get active groups. [${ex.getMessage}] [${ex.getCause}]")
          } yield ()
        ZIO.succeed(List.empty[Group])
    }}


  /*
sqlBarsAgrStats = "select t.groupid,t.firstname,t.lastname from tgroup t where t.is_blck_by_user_dt is null;"
val prepBarsAgrStats: BoundStatement = prepareSql(sess, sqlBarsAgrStats)
  def getBarsDdateStats: Seq[BarDateStat] = conn.execute(prepBarsAgrStats).all().iterator.asScala.toSeq
  .map(rowToBarDateStat(_, getTickersDict)).toList
  */


  override def runBot: UIO[Unit] =
    for {
    _ <- console.printLine("~~~~~~~~~~~~~~~ we are here ~~~~~~~~~~~~~~~~").orDie
    msgText = "URA!!!!!"
    //testGroupId: Long = 322134338L

    listGroups <- getActiveGroups

    _ <- ZIO.foreach(listGroups){
      thisGroup =>
        ZIO.fromFuture{implicit ec =>bot.sendMsgToGroup(thisGroup.groupid,s"Новое сообщение для ${thisGroup.firstname} ${thisGroup.lastname}.")}
          .catchAll{
            error =>
              error match {
                case tex: TelegramApiException =>
                  (for {
                    //todo: here we need block user in table chat_status and don't send new messages to him.
                    _ <- console.printLine(s"Telegram exception = [${tex.message}] - [${tex.errorCode}] - [${tex.cause}]").orDie
                    //---------------------------------------------
                    // Save information that bot is blocked by user.
                    pgConn <- conn.connection
                    stmt = pgConn.prepareStatement("update tgroup set is_blck_by_user_dt = timeofday()::TIMESTAMP where groupId = ?;")
                    _ <- ZIO.attempt{
                      stmt.setLong(1, thisGroup.groupid)
                      stmt.executeUpdate()
                    }.catchAll{
                      errPg => errPg match{
                        case ex: Throwable =>
                          for {
                            _ <- console.printLine(s"FBAE-01 Can't save information about blocking by user. [${ex.getMessage}] [${ex.getCause}]")
                          } yield ()
                      }}
                    //---------------------------------------------
                  } yield ()).catchAll{
                    case ex : Throwable =>
                      console.printLine(s"FBAE-02 Can't save information about blocking by user. [${ex.getMessage}] [${ex.getCause}]").orDie
                  }
                case err: Throwable =>
                  for {
                    _ <- console.printLine(s"method runBot, error = [${err.getMessage} - ${err.getCause}]").orDie
                  } yield ()
              }
          }
    }




/*
    _ <- console.printLine("~~~~~~~~~~~~~~~ we are here ~~~~~~~~~~~~~~~~").orDie
    msgText = "URA!!!!!"
    testGroupId: Long = 322134338L
    _ <- ZIO.fromFuture{implicit ec =>bot.sendMsgToGroup(testGroupId,"Новое сообщение от бота - 1.")}
      .catchAll{
        error =>
          error match {
            case tex: TelegramApiException =>
              (for {
                //todo: here we need block user in table chat_status and don't send new messages to him.
                _ <- console.printLine(s"Telegram exception = [${tex.message}] - [${tex.errorCode}] - [${tex.cause}]").orDie
                //---------------------------------------------
                // Save information that bot is blocked by user.
                pgConn <- conn.connection
                stmt = pgConn.prepareStatement("update tgroup set is_blck_by_user_dt = timeofday()::TIMESTAMP where groupId = ?;")
                _ <- ZIO.attempt{
                  stmt.setLong(1, testGroupId)
                  stmt.executeUpdate()
                }.catchAll{
                  errPg => errPg match{
                case ex: Throwable =>
                  for {
                       _ <- console.printLine(s"FBAE-01 Can't save information about blocking by user. [${ex.getMessage}] [${ex.getCause}]")
                      } yield ()
                  }}
                //---------------------------------------------
              } yield ()).catchAll{
                case ex : Throwable =>
                  console.printLine(s"FBAE-02 Can't save information about blocking by user. [${ex.getMessage}] [${ex.getCause}]").orDie
              }
            case err: Throwable =>
              for {
                _ <- console.printLine(s"method runBot, error = [${err.getMessage} - ${err.getCause}]").orDie
              } yield ()
          }
      }
*/
  } yield ()
}

object TelegBotImpl{
  val layer  = ZLayer{
    for {
      console <- ZIO.console
      conf <- ZIO.service[AppConfig]
      conn <- ZIO.service[PgConnection]
      c <- conn.connection
      _ <- console.printLine(s"[TelegBotImpl] connection isOpened = ${!c.isClosed}")
    } yield TelegBotImpl(console,conf,conn)
  }
}