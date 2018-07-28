package com.intersys.cnap.crawler.service.publish

import akka.actor.{Actor, ActorLogging, ActorRef}
import com.intersys.cnap.crawler.conf.Context
import com.intersys.cnap.crawler.util.ClientHttp.Url

class Publisher extends Actor with Context with ActorLogging {
  import Publisher._
  var crawlProcedure: Option[ActorRef] = None

  override def receive: Receive = {
    case InitMessage(actorRef) =>
      crawlProcedure = Some(actorRef)
    case Print(str) => println(str)
    case url: Url => crawlProcedure match {
      case Some(ref) =>
        ref ! url
      case None => Unit
    }
    case _ => Unit
  }
}

object Publisher {
  final case class InitMessage(actorRef: ActorRef)
  final case class Print(str: String)
}
