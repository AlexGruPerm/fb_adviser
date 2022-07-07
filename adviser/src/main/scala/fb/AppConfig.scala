package fb

case class DbConfig(
                     driver: String,
                     url: String,
                     username: String,
                     password: String,
                   )

case class AppConfig(dbConf: DbConfig)
