package fb

case class DbConfig(
                     driver: String,
                     url: String,
                     username: String,
                     password: String,
                   )

case class BotConfig(
                     token: String,
                     webhookUrl: String,
                     webhook_port: Int,
                     keyStorePassword: String,
                     pubcertpath: String,
                     p12certpath: String,
                   )

case class AppConfig(dbConf: DbConfig, botConfig: BotConfig)
