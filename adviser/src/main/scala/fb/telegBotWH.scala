package fb


import org.slf4j.{Logger, LoggerFactory}

import java.io.{File, FileInputStream, InputStream}
import java.security.{KeyStore, SecureRandom}
import java.time.LocalDate
import akka.http.scaladsl.{ConnectionContext, Http, HttpsConnectionContext}
import com.bot4s.telegram.api.declarative.{Action, Commands, JoinRequests, RegexCommands}
import com.bot4s.telegram.api.{AkkaTelegramBot, RequestHandler, Webhook}
import com.bot4s.telegram.clients.AkkaHttpClient
import com.bot4s.telegram.future.Polling
import com.bot4s.telegram.methods.{ApproveChatJoinRequest, CreateChatInviteLink, DeclineChatJoinRequest, GetChatMenuButton, ParseMode, SendMessage, SendPhoto, SetChatMenuButton, SetMyCommands}
import com.bot4s.telegram.models.{BotCommand, Chat, ChatId, InputFile, KeyboardButton, MenuButton, MenuButtonCommands, MenuButtonDefault, MenuButtonWebApp, Message, ReplyKeyboardMarkup, Update, User}
import com.typesafe.config.Config
import com.bot4s.telegram.models.UpdateType.Filters._

import javax.net.ssl.{KeyManagerFactory, SSLContext, TrustManagerFactory}
import scala.concurrent.{ExecutionContext, Future}
import scala.compat.Platform.EOL
import scala.util.Try
import cats.instances.future._
import cats.syntax.functor._
import com.bot4s.telegram.models.UpdateType.UpdateType
import services.PgConnection
import zio.ZIO
import zio._

import java.sql.Statement


class telegBotWH(config :BotConfig, conn: PgConnection)
  extends AkkaTelegramBot
    with Webhook
    with CommonFuncs
    with Commands[Future]
    with JoinRequests[Future]
    with RegexCommands[Future]
{

  val log = LoggerFactory.getLogger(getClass.getName)

  // Extractor
  object Int {
    def unapply(s: String): Option[Int] = Try(s.toInt).toOption
  }

  val exCtx: ExecutionContext = executionContext

  val port :Int = config.webhook_port
  val webhookUrl = config.webhookUrl
  log.info(" webhookUrl="+webhookUrl+" port="+port)

  val certPathStr :String = config.pubcertpath
  log.info("Certificate Path ="+certPathStr)

  override def certificate: Option[InputFile] = Some(
    InputFile(new File(certPathStr).toPath)
  )

  override def receiveMessage(msg: Message): Future[Unit] = {
    log.info("receiveMessage method!!!")
    msg.text.fold(Future.successful(())) {
      text =>
        log.info(s"receiveMessage text OK =$text")
        Future.successful[Unit](Unit)
    }
  }

  val keystorePassword :Array[Char] = config.keyStorePassword.toCharArray
  override val interfaceIp: String = "0.0.0.0"

  override def allowedUpdates: Option[Seq[UpdateType]] =
    Some(MessageUpdates ++ InlineUpdates)

  override val httpsContext: Option[akka.http.scaladsl.HttpsConnectionContext] = Some(getHttpsContext(keystorePassword))

  def getHttpsContext(keystorePassword : Array[Char]): HttpsConnectionContext = {
    // Manual HTTPS configuration
    val password: Array[Char] = keystorePassword

    val ks: KeyStore = KeyStore.getInstance("PKCS12")
    val keystore: InputStream = new FileInputStream(config.p12certpath)

    require(keystore != null, " - Keystore required!")
    ks.load(keystore, password)

    val keyManagerFactory: KeyManagerFactory = KeyManagerFactory.getInstance("SunX509")
    keyManagerFactory.init(ks, password)

    val tmf: TrustManagerFactory = TrustManagerFactory.getInstance("SunX509")
    tmf.init(ks)

    val sslContext: SSLContext = SSLContext.getInstance("TLS")
    sslContext.init(keyManagerFactory.getKeyManagers, tmf.getTrustManagers, new SecureRandom)
    val https: HttpsConnectionContext = ConnectionContext.httpsServer(sslContext)

    https
  }

  log.info("AkkaHttpClient - " + config.token )
  val client = new AkkaHttpClient(config.token)

  onCommand("begin") { implicit msg =>
    onCommandLog(msg)
    replyMd(s"begin...........................".stripMargin)
    Future.successful[Unit](Unit)
  }

  onCommand("catch") { implicit msg =>
    onCommandLog(msg)
    replyMd(s"catch...........................".stripMargin)
    Future.successful[Unit](Unit)
  }

  onCommand("start") { implicit msg =>
    onCommandLog(msg)
    val resFuture :ZIO[Any,Throwable,Future[Unit]] =
      for {
        pgConn <- conn.connection
        console <- ZIO.console
        _ <- console.printLine(s"INSERT WITH groupid = ${msg.chat.id} conn.isAutoCommit = ${pgConn.getAutoCommit}")
        stmt = pgConn.prepareStatement("   INSERT INTO tgroup(groupid,firstname,lastname,username,lang,loc_latitude,loc_longitude)\n    " +
          "VALUES(?,?,?,?,?,?,?)\n    " +
          "ON CONFLICT (groupid)\n    " +
          "do update set is_blck_by_user_dt = null, last_cmd_start_dt = timeofday()::TIMESTAMP;")
        _ <- ZIO.attempt{
          stmt.setLong(1, msg.chat.id)
          stmt.setString(2,msg.from.map(u => u.firstName).getOrElse(" "))
          stmt.setString(3,msg.from.map(u => u.lastName.getOrElse(" ")).getOrElse(" "))
          stmt.setString(4,msg.from.map(u => u.username.getOrElse(" ")).getOrElse(" "))
          stmt.setString(5,msg.from.map(u => u.languageCode.getOrElse(" ")).getOrElse(" "))
          stmt.setDouble(6,msg.location.map(l => l.latitude).getOrElse(0.0))
          stmt.setDouble(7,msg.location.map(l => l.longitude).getOrElse(0.0))
          val resInsertStartCmd = stmt.executeUpdate()
          resInsertStartCmd.toString
        }
      } yield Future.successful[Unit](Unit)
    val r :Unit = Runtime.default.unsafeRunAsync(
      resFuture
    )
    replyMd(s"AFTER start command ...........................".stripMargin)
    Future.successful[Unit](Unit)
  }


  val accept: Boolean = false //accept = false for all requests for a while
  // join not ot bot, join to chat (group).
  onJoinRequest { joinRequest =>
    if (accept) {
      log.info(s"onJoinRequest from user = ${joinRequest.from.id} ApproveChatJoinRequest")
      request(ApproveChatJoinRequest(joinRequest.chat.chatId, joinRequest.from.id)).void
    } else {
      log.info(s"onJoinRequest from user = ${joinRequest.from.id} DeclineChatJoinRequest")
      request(DeclineChatJoinRequest(joinRequest.chat.chatId, joinRequest.from.id)).void
    }
  }

  def sendMsgToGroup(groupId: Long,textMessage: String): Future[Unit] = {
    for {
      _ <- request(SendMessage(groupId, s"*${textMessage}*", Some(ParseMode.Markdown)))
    } yield ()
  }


  def onCommandLog(msg :Message) ={
    log.info(" Command ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~ ")
    /** USER */
    log.info(" User :")
    log.info(" ID = "+msg.from.map(u => u.id).getOrElse(0))
    log.info(s" is bot ${msg.from.map(u => u.isBot)}")
    log.info(" FIRSTNAME = "+msg.from.map(u => u.firstName).getOrElse(" "))
    log.info(" LASTNAME = "+msg.from.map(u => u.lastName.getOrElse(" ")).getOrElse(" "))
    log.info(" USERNAME = "+msg.from.map(u => u.username.getOrElse(" ")).getOrElse(" "))
    log.info(" USERID   = "+msg.from.map(u => u.id).getOrElse(" "))
    log.info(" LANG     = "+msg.from.map(u => u.languageCode.getOrElse(" ")).getOrElse(" "))
    log.info(" isBot    = "+msg.from.map(u => u.isBot).getOrElse(" "))
    //todo: запретить ботам создавать чаты с этим ботом.
    /**~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~*/
    log.info(" LOC latitude     = "+msg.location.map(l => l.latitude))
    log.info(" LOC longitude    = "+msg.location.map(l => l.longitude))
    log.info(" isChat           = "+msg.chat.chatId.isChat)
    log.info("  chat(id)        = "+msg.chat.id)
    log.info("  linkedChatId    = "+msg.chat.linkedChatId)
    log.info(" isChannel        = "+msg.chat.chatId.isChannel)
    /**~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~*/
    log.info(" msg date        = "+msg.date)
    log.info(" messageId = "+msg.messageId)
    log.info(" text = "+msg.text.mkString(","))
    log.info(" ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~ ")
  }



}
