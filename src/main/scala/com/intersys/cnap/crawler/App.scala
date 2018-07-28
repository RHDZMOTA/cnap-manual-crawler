package com.intersys.cnap.crawler

import akka.actor.{ActorRef, Props}
import akka.stream.ClosedShape
import akka.stream.scaladsl.GraphDSL.Builder
import akka.stream.scaladsl.{Broadcast, GraphDSL, RunnableGraph, Source}
import com.intersys.cnap.crawler.conf.{Context, Settings}
import com.intersys.cnap.crawler.service.Crawler
import com.intersys.cnap.crawler.service.database.impl.Cassandra
import com.intersys.cnap.crawler.service.publish.Publisher
import com.intersys.cnap.crawler.service.storage.impl.Solr
import com.intersys.cnap.crawler.service.validation.Validator
import com.intersys.cnap.crawler.util.ClientHttp._

object App extends Context {

  def main(args: Array[String]): Unit = {
    val publisher: ActorRef = actorSystem.actorOf(Props[Publisher], name = "publisher")
    val validator: ActorRef = actorSystem.actorOf(Props(new Validator(publisher)), name = "validator")
    val procedureRef: ActorRef = graph(validator).run

    publisher ! Publisher.InitMessage(procedureRef)
    publisher ! Publisher.Print("Up and running.")
    publisher ! Url.crawlJob("https://dbmefaapolicy.azdes.gov", Settings.Crawler.depth)
  }

  val actorSource: Source[Url, ActorRef] = Source.actorRef[Url](
    Settings.Source.bufferSize,
    Settings.Source.overflowStrategy
  )

  def graph(vref: ActorRef) : RunnableGraph[ActorRef] = RunnableGraph.fromGraph {
    GraphDSL.create(actorSource) { implicit builder: Builder[ActorRef] =>
      actorSource =>
        import GraphDSL.Implicits._
        val broadcastResult = builder.add(Broadcast[CustomResponse](outputPorts = 3))

        // Runnable graph definition
        actorSource ~> Crawler.download ~>  broadcastResult ~> Crawler.getChildRefs ~> Validator.sink(vref)
                                            broadcastResult ~> Solr.sink
                                            broadcastResult ~> Cassandra.sink
        ClosedShape
    }
  }

}

/**
import com.intersys.cnap.crawler.App.Crawler
import com.intersys.cnap.crawler.util.ClientHttp
val seed = ClientHttp.Url.crawlJob("https://dbmefaapolicy.azdes.gov", 100)
val cr = Crawler.getRequestWithCustomResponse(seed)
val urls = cr.map(Crawler.extractUrls(_))
urls.map(_.foreach(x => println(x.getUri)))
**/