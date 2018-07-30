package com.intersys.cnap.crawler.service.validation

import java.sql.Timestamp

import akka.NotUsed
import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.stream.scaladsl.Sink
import com.intersys.cnap.crawler.conf.{Context, Settings}
import com.intersys.cnap.crawler.util.ClientHttp.Url

class Validator(publisher: ActorRef) extends Actor with Context with ActorLogging {
  import Validator._

  var seen: Map[String, Timestamp] = Map[String, Timestamp]()

  def valid(url: Url): Boolean = seen.get(url.getUri)
    .forall(ts => (url.timestamp.getNanos - ts.getNanos) < Settings.Crawler.urlLifeSpan.toNanos)

  def resetSeen(): Unit =
    seen = Map[String, Timestamp]()

  def updateSeen(uri: String, timestamp: Timestamp): Unit =
    seen + (uri -> timestamp)

  override def receive: Receive = {
    case StreamInitialized =>
      resetSeen()
      sender() ! Ack
    case StreamCompleted =>
      sender() ! Ack
    case StreamFailure(ex) =>
      log.warning("Stream failure encounter: " + ex.toString)
    case ResetSeen =>
      resetSeen()
    case url: Url =>
      if (valid(url)) publisher ! url
      sender() ! Ack
    case _ =>
  }
}

object Validator {
  case object Ack
  case object ResetSeen
  case object StreamCompleted
  case object StreamInitialized
  final case class StreamFailure(ex: Throwable)
  val onFailureMessage: Throwable => StreamFailure = (ex: Throwable) => StreamFailure(ex)

  def sink(actorRef: ActorRef): Sink[Url, NotUsed] = Sink.actorRefWithAck[Url](actorRef,
    onInitMessage     = Validator.StreamInitialized,
    ackMessage        = Validator.Ack,
    onCompleteMessage = Validator.StreamCompleted,
    onFailureMessage  = Validator.onFailureMessage
  )

}
