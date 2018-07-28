package com.intersys.cnap.crawler.service.validation

import akka.NotUsed
import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.stream.scaladsl.Sink
import com.intersys.cnap.crawler.conf.Context
import com.intersys.cnap.crawler.util.ClientHttp.Url

class Validator(publisher: ActorRef) extends Actor with Context with ActorLogging {
  import Validator._

  def valid(url: Url): Boolean = true

  override def receive: Receive = {
    case StreamInitialized =>
      sender() ! Ack
    case StreamCompleted =>
      sender() ! Ack
    case StreamFailure(ex) =>
      log.warning("Stream failure encounter: " + ex.toString)
    case url: Url =>
      if (valid(url)) publisher ! url
      sender() ! Ack
    case _ =>
  }

}

object Validator {
  case object Ack
  case object StreamInitialized
  case object StreamCompleted
  final case class StreamFailure(ex: Throwable)
  val onFailureMessage: Throwable => StreamFailure = (ex: Throwable) => StreamFailure(ex)

  def sink(actorRef: ActorRef): Sink[Url, NotUsed] = Sink.actorRefWithAck[Url](actorRef,
    onInitMessage     = Validator.StreamInitialized,
    ackMessage        = Validator.Ack,
    onCompleteMessage = Validator.StreamCompleted,
    onFailureMessage  = Validator.onFailureMessage
  )

}
