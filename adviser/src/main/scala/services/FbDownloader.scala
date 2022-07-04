package services

import zio.{Clock, Console, Task, TaskLayer, ULayer, URIO, URLayer, ZIO, ZLayer}

/**
 * Service pattern 2:
 * 1. define interface
 * 2. accessor method inside companion objects
 * 3. implementation of service interface
 * 4. converting service implementation into ZLayer
 */
  //1. service interface - read Json string from given url.
  trait FbDownloader {
    def download(url: String): Task[String]
  }

  //2. accessor method inside companion objects
  object FbDownloader {
    def download(url: String): ZIO[FbDownloader, Throwable, String] =
      ZIO.serviceWithZIO(_.download(url))
  }

  //3. implementation of service interface
  case class FbDownloaderImpl(console: Console, clock: Clock) extends FbDownloader {
    override def download(url: String): Task[String] =
      for {
        time <- clock.currentDateTime
        _ <- console.printLine(s"$time - $url")
        srcJson <- ZIO.succeed("this isi a download json text.")
      } yield srcJson
  }

  //4. converting service implementation into ZLayer
  object FbDownloaderImpl {
    val layer: TaskLayer[FbDownloader] =
      ZLayer {
        for {
          console <- ZIO.console
          clock <- ZIO.clock
        } yield FbDownloaderImpl(console,clock)
      }
  }



