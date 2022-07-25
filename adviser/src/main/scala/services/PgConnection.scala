package services

import fb.{AppConfig, DbConfig}
import zio.{Task, UIO, URLayer, ZIO, ZLayer}

import java.sql.{Connection, DriverManager, ResultSet, Statement, Types}
import java.util.Properties

/**
 *
 * The 3 Laws of ZIO Environment:
 * 1. Methods inside service definitions (traits) should NEVER use the environment
 * 2. Service implementations (classes) should accept all dependencies in constructor
 * 3. All other code ('business logic') should use the environment to consume services https://t.co/iSWzMhotOv
 *
 */
/**
 * Service pattern 2:
 * 1. define interface
 * 2. accessor method inside companion objects
 * 3. implementation of service interface
 * 4. converting service implementation into ZLayer
 */

  //1. service interface - get config
  trait PgConnection {
   def connection : Task[Connection]
   def execute(sql: String): Task[String]
  }

  //2.accessor method inside companion object

  //3. service interface implementation
  case class PgConnectionImpl(conf: DbConfig) extends PgConnection {
    override def connection: Task[Connection] = {
      ZIO.attempt {
        val props = new Properties()
        props.setProperty("user", conf.username)
        props.setProperty("password", conf.password)
        val c: Connection = DriverManager.getConnection(conf.url, props)
        c.setClientInfo("ApplicationName", s"fb_adviser")
        val stmt: Statement = c.createStatement
        val rs: ResultSet = stmt.executeQuery("SELECT pg_backend_pid() as pg_backend_pid")
        rs.next()
        val pg_backend_pid: Int = rs.getInt("pg_backend_pid")
        c
      }
    }

    override def execute(sql: String): Task[String] =
      for {
        con <- connection
        stex <- ZIO.attempt {
          con.setAutoCommit(true)
          val stmt = con.prepareCall(sql)
          stmt.execute()
        }
      } yield stex.toString
  }



    //4. converting service implementation into ZLayer
    object PgConnectionImpl{
      val layer :ZLayer[AppConfig,Throwable,PgConnection] =
        ZLayer{
          for {
            cfg <- ZIO.service[AppConfig]
          } yield PgConnectionImpl(cfg.dbConf)
        }
    }



