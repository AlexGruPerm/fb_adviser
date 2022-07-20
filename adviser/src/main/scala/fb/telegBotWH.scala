package fb


import org.slf4j.{Logger, LoggerFactory}

import java.io.{File, FileInputStream, InputStream}
import java.security.{KeyStore, SecureRandom}
import java.time.LocalDate
import akka.http.scaladsl.{ConnectionContext, Http, HttpsConnectionContext}
import com.bot4s.telegram.api.declarative.{Commands, RegexCommands}
import com.bot4s.telegram.api.{AkkaTelegramBot, Webhook}
import com.bot4s.telegram.clients.AkkaHttpClient
import com.bot4s.telegram.future.Polling
import com.bot4s.telegram.methods.{ApproveChatJoinRequest, CreateChatInviteLink, DeclineChatJoinRequest, GetChatMenuButton, SetChatMenuButton, SetMyCommands}
import com.bot4s.telegram.models.{BotCommand, Chat, ChatId, InputFile, KeyboardButton, MenuButton, MenuButtonCommands, MenuButtonDefault, MenuButtonWebApp, Message, ReplyKeyboardMarkup}
import com.typesafe.config.Config
import com.bot4s.telegram.models.UpdateType.Filters._

import javax.net.ssl.{KeyManagerFactory, SSLContext, TrustManagerFactory}
import scala.concurrent.Future
import scala.compat.Platform.EOL
import scala.util.Try
import com.bot4s.telegram.api.declarative.{Commands, JoinRequests}
import cats.instances.future._
import cats.syntax.functor._
import com.bot4s.telegram.models.UpdateType.UpdateType


class telegBotWH(config :BotConfig)
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

  val confPrefix :String = "teleg."
  val port :Int = config.webhook_port // config.getInt(confPrefix+"webhook_port")
  val webhookUrl = config.webhookUrl// config.getString(confPrefix+"webhookUrl")
  log.info(" webhookUrl="+webhookUrl+" port="+port)

  val certPathStr :String = config.pubcertpath // config.getString(confPrefix+"pubcertpath")
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

  val keystorePassword :Array[Char] = config.keyStorePassword/*config.getString(confPrefix+"keyStorePassword")*/.toCharArray
  override val interfaceIp: String = "0.0.0.0"

  override def allowedUpdates: Option[Seq[UpdateType]] =
    Some(MessageUpdates ++ InlineUpdates)

  // Set custom context.
  //Http().setDefaultServerHttpContext(getHttpsContext(keystorePassword))

  override val httpsContext: Option[akka.http.scaladsl.HttpsConnectionContext] = Some(getHttpsContext(keystorePassword))

  def getHttpsContext(keystorePassword : Array[Char]): HttpsConnectionContext = {
    // Manual HTTPS configuration
    val password: Array[Char] = keystorePassword

    val ks: KeyStore = KeyStore.getInstance("PKCS12")
    val keystore: InputStream = new FileInputStream(config.p12certpath/*config.getString(confPrefix+"p12certpath")*/)

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

  log.info("AkkaHttpClient - " + config.token /*config.getString(confPrefix+"token")*/)
  val client = new AkkaHttpClient(config.token/*config.getString(confPrefix+"token")*/)

  /*
  onCommand("res") { implicit msg =>
    onCommandLog(msg)
    msg.from match {
      case Some(user) =>
        request(
          SetMyCommands(
            List(
              BotCommand("help", "Welcome someone"),
              BotCommand("author", "You guessed it"),
              BotCommand("res", "show menu"),
              BotCommand("link", "link for add"),
              BotCommand("accept", "accept for add"),
              BotCommand("deny", "deny for add")
            )
          )
        ).void
        request(SetChatMenuButton(user.id,Some(MenuButtonDefault()))).flatMap{s =>
        reply(s.toString)}.recoverWith{case error =>
        reply(s"error - ${error.getCause + " - " + error.getMessage}")}.void
      case None => reply("User not found").void
    }
  }
*/
  /*
    onCommand("rem") { implicit msg =>
      onCommandLog(msg)
      msg.from match {
        case Some(user) =>
          request(
            SetMyCommands(
              List(
                BotCommand("help", "Welcome someone"),
                BotCommand("author", "You guessed it"),
                BotCommand("getb","get button")
              )
            )
          ).void
          request(SetChatMenuButton(user.id,Some(MenuButtonDefault()))).flatMap{s =>
            reply(s.toString)}.recoverWith{case error =>
            reply(s"error - ${error.getCause + " - " + error.getMessage}")}.void
        case None => reply("User not found").void
      }
    }
    */




  onCommand("but") { implicit msg =>
    onCommandLog(msg)
    reply(text = "HELP !!!",
      replyMarkup = Some(ReplyKeyboardMarkup.singleButton(button = KeyboardButton(text = "/help"),inputFieldPlaceholder = Some("Нажмите кнопку или введите команду"),selective = Some(true)))
    )
    Future.successful[Unit](Unit)
  }

  onCommand("but1") { implicit msg =>
    onCommandLog(msg)
    reply(text = "Открыто новое меню, выбирите дальнешее действие.",
      replyMarkup = Some(ReplyKeyboardMarkup.singleColumn(buttonColumn = Seq(
        KeyboardButton(text = "/help"),
        KeyboardButton(text = "/but2")
      ),resizeKeyboard = Some(true)))
    )
    Future.successful[Unit](Unit)
  }

  var accept: Boolean = true

  onJoinRequest { joinRequest =>
    if (accept)
      request(ApproveChatJoinRequest(joinRequest.chat.chatId, joinRequest.from.id)).void
    else
      request(DeclineChatJoinRequest(joinRequest.chat.chatId, joinRequest.from.id)).void
  }

  onCommand("but2") { implicit msg =>
    onCommandLog(msg)
    reply(text = "Текст при открытии but2",
      replyMarkup = Some(ReplyKeyboardMarkup.singleColumn(buttonColumn = Seq(
        KeyboardButton(text = "/help"),
        KeyboardButton(text = "/res"),
        KeyboardButton(text = "/but1"),
        KeyboardButton(text = "/author")
      ),resizeKeyboard = Some(true)))
    )
    Future.successful[Unit](Unit)
  }










  /*
    onCommand("/link") { implicit msg =>
    request(CreateChatInviteLink(ChatId(msg.chat.id), createsJoinRequest = Some(true))).flatMap { link =>
      reply(s"Invite link is ${link.inviteLink}").void
    }.recoverWith { case error => reply(s"Unable to create link ${error}").void }
  }
  */

  def onCommandLog(msg :Message) ={
    log.info(" Command ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~ ")
    /** USER */
    log.info(" User :")
    log.info(" ID = "+msg.from.map(u => u.id).getOrElse(0))
    log.info(s" is bot ${msg.from.map(u => u.isBot)}")
    log.info(" FIRSTNAME = "+msg.from.map(u => u.firstName).getOrElse(" "))
    log.info(" LASTNAME = "+msg.from.map(u => u.lastName.getOrElse(" ")).getOrElse(" "))
    log.info(" USERNAME = "+msg.from.map(u => u.username.getOrElse(" ")).getOrElse(" "))
    log.info(" LANG = "+msg.from.map(u => u.languageCode.getOrElse(" ")).getOrElse(" "))
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


  onCommand("/command1") { implicit msg =>
    replyMd(s"Text1".stripMargin)
    Future.successful[Unit](Unit)
  }

  onCommand("/command2") { implicit msg =>
    replyMd(s"Text2".stripMargin)
    Future.successful[Unit](Unit)
  }



  // String commands.
  onCommand("/accept") { _ =>
    Future { accept = true }
  }

  onCommand("/deny") { _ =>
    Future { accept = false }
  }

  onCommand("/link") { implicit msg =>
    request(CreateChatInviteLink(ChatId(msg.chat.id), createsJoinRequest = Some(true))).flatMap { link =>
      reply(s"Invite link is ${link.inviteLink}").void
    }.recoverWith { case error => reply(s"Unable to create link ${error}").void }
  }



  // withArgs with pattern matching.
  onCommand("/inc") { implicit msg =>
    withArgs {
      case Seq(Int(i)) =>
        reply("" + (i + 1)).void

      // Conveniently avoid MatchError, providing hints on usage.
      case _ =>
        reply("Invalid argument. Usage: /inc 123").void
    }
  }

  onCommand("author") { implicit msg =>
    onCommandLog(msg)
    replyMd(
      s""" *Yakushev Aleksey*
         | _ugr@bk.ru_
         | _yakushevaleksey@gmail.com_
         | telegram = @AlexGruPerm
         | vk       = https://vk.com/id11099676
         | linkedin = https://www.linkedin.com/comm/in/yakushevaleksey
         | gihub    = https://github.com/AlexGruPerm
         | bigdata  = https://yakushev-bigdata.blogspot.com
         | oracle   = http://yakushev-oracle.blogspot.com
             """.stripMargin
    )
    Future.successful[Unit](Unit)
  }

  onCommand("help" ) {
    implicit msg => reply(BotCommandsHelper.getHelpText)
      Future.successful[Unit](Unit)
  }

}
