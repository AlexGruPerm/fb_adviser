package app

import com.typesafe.config.Config
import fb.{AppConfig, BotConfig, DbConfig}

case object ConfigHelper{
  def getConfig(fileConfig :Config): AppConfig = {
    val (telegramPrefix,pgPrefix) = ("telegram.","postgres.")
    AppConfig(
      DbConfig(
        driver   = fileConfig.getString(pgPrefix+"driver"),
        url      = fileConfig.getString(pgPrefix+"url"),
        username = fileConfig.getString(pgPrefix+"username"),
        password = fileConfig.getString(pgPrefix+"password"),
      ),
      BotConfig(
        token = fileConfig.getString(telegramPrefix+"token"),
        webhookUrl = fileConfig.getString(telegramPrefix+"webhookUrl"),
        webhook_port = fileConfig.getInt(telegramPrefix+"webhook_port"),
        keyStorePassword = fileConfig.getString(telegramPrefix+"keyStorePassword"),
        pubcertpath = fileConfig.getString(telegramPrefix+"pubcertpath"),
        p12certpath = fileConfig.getString(telegramPrefix+"p12certpath"))
    )
  }
}
