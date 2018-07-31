package com.intersys.cnap.crawler.service.storage.impl

import java.util.UUID

import akka.NotUsed
import akka.http.scaladsl.model.HttpResponse
import akka.stream.scaladsl.{Flow, Sink}
import com.intersys.cnap.crawler.conf.{Context, Settings}
import com.intersys.cnap.crawler.service.storage.Storage
import com.intersys.cnap.crawler.util.ClientHttp
import com.intersys.cnap.crawler.util.ClientHttp._

case object Solr extends Storage[CustomResponse, NotUsed] with ClientHttp with Context {

  def uRL(id: UUID = UUID.randomUUID()): java.net.URL =
    new java.net.URL(s"http://${Settings.Solr.address}:${Settings.Solr.port}/solr/${Settings.Solr.collection}/update/extract?literal.id=${id.toString}&commit=true")

  override def sink: Sink[CustomResponse, NotUsed] =
    Flow[CustomResponse].mapAsync[(String, Option[HttpResponse])](Settings.Solr.parallelism)(
      customResponse => postRequestCustom(uRL(customResponse.url.id), customResponse).map((customResponse.url.getUri, _))).to(
      Sink.foreach[(String, Option[HttpResponse])] {
        case (uri, None) => println(s"[SOLR FAIL] $uri")
        case (uri, Some(httpResponse)) =>
          if (httpResponse.status.isFailure()) println(s"[SOLR FAIL] $uri : ${httpResponse.status.reason()}")
      }
    )
}
