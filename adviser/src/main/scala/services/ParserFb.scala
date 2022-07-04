package services

import fb.Event
import zio.{Task, ZIO, ZLayer}


/**
 * Service that parse input json string and return Seq of events.
 */
trait ParserFb{
  def parseJson: Task[Seq[Event]]
}

/**
 * Service Companion object
*/
object ParserFb{
  // implementation
  case class ParseFbData(json: String) extends ParserFb {
    override def parseJson: Task[Seq[Event]] =
      ZIO.succeed(
        Seq(Event(1L),Event(2L))
      )
  }

  //layer
  val live: ZLayer[String, Throwable, ParserFb] = ZLayer{
    for {
      json <- ZIO.service[String]
    } yield ParseFbData(json)
  }

  // accessor
  def parseJson: ZIO[String,Throwable,Task[Seq[Event]]] =
    ZIO.serviceWith(json => ParseFbData(json).parseJson)

}


