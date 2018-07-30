package com.intersys.cnap.crawler.service.storage.impl

import akka.NotUsed
import akka.http.scaladsl.model.HttpResponse
import akka.stream.scaladsl.{Flow, Sink}
import com.intersys.cnap.crawler.conf.{Context, Settings}
import com.intersys.cnap.crawler.service.storage.Storage
import com.intersys.cnap.crawler.util.ClientHttp
import com.intersys.cnap.crawler.util.ClientHttp._

case object Solr extends Storage[CustomResponse, NotUsed] with ClientHttp with Context {

  val uRL: java.net.URL = new java.net.URL(s"http://${Settings.Solr.address}:${Settings.Solr.port}/solr/${Settings.Solr.collection}/update")

  override def sink: Sink[CustomResponse, NotUsed] =
    Flow[CustomResponse].mapAsync[(String, Option[HttpResponse])](20)(
      customResponse => postRequestCustom(uRL, customResponse).map((customResponse.url.getUri, _))).to(
      Sink.foreach[(String, Option[HttpResponse])] {
        case (uri, None) => println(s"[SOLR FAIL] $uri")
        case (uri, Some(_)) => println(s"[SOLR SUCCESS] $uri")
      }
    )
}
