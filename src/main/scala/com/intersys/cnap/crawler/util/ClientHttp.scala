package com.intersys.cnap.crawler.util

import akka.actor.ActorSystem
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model._
import akka.http.scaladsl.Http
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import java.net.URL
import java.sql.Timestamp
import java.time.LocalDateTime
import java.util.UUID

import scala.util.Try
import scala.concurrent.{ExecutionContext, Future}

trait ClientHttp {
  import ClientHttp._

  def getRequest(url: Url)(implicit actorSystem: ActorSystem, executionContext: ExecutionContext): Future[(Url, HttpResponse)] =
    Http(actorSystem).singleRequest(
      HttpRequest(HttpMethods.GET, url.getUri)
    ).map(url -> _)

  def postRequest(uRL: URL, entity: Array[Byte])(implicit actorSystem: ActorSystem, executionContext: ExecutionContext): Future[HttpResponse] =
    Http(actorSystem).singleRequest(
      HttpRequest(HttpMethods.POST, uRL.toString)
        .withEntity(entity)
    )

  def postRequestCustom(uRL: URL, customResponse: CustomResponse)(implicit actorSystem: ActorSystem, executionContext: ExecutionContext): Future[Option[HttpResponse]] =
    customResponse.content.map(postRequest(uRL, _)) match {
      case Some(future) => future.map(Some(_))
      case None         => Future.successful(None)
    }

  def getRequestWithCustomResponse(url: Url)(
    implicit  actorSystem: ActorSystem,
              executionContext: ExecutionContext,
              materializer: Materializer): Future[ClientHttp.CustomResponse] = for {
    httpResponse  <- Http(actorSystem).singleRequest(HttpRequest(HttpMethods.GET, url.getUri))
    content       <- Unmarshal(httpResponse.entity).to[Array[Byte]]
  } yield
    if (httpResponse.status.isSuccess()) CustomResponse(url, Some(content), Some(httpResponse.entity.contentType.mediaType))
    else CustomResponse(url, None, None)
}

object ClientHttp {

  case class CustomResponse(url: Url, content: Option[Array[Byte]], mediaTye: Option[MediaType])

  case class Url(id: UUID, url: URL, depth: Int, maxDepth: Int, from: UUID, crawlJob: UUID, timestamp: Timestamp) {
    def getUri: String = url.toString
  }

  object Url {
    implicit class StringExtras(str: String) {
      def toUUID: UUID = UUID.fromString(str)
      def toTimestamp: Timestamp = Timestamp.valueOf(str)
      def toURL: URL = new URL(str)
      def asURL: Option[URL] = Try(new URL(str)).toOption
      def asInt: Option[Int] = Try(str.toInt).toOption
    }

    def now: Timestamp = Timestamp.valueOf(LocalDateTime.now())

    def apply(uri: String, depth: Int, maxDepth: Int, from: UUID, crawlJob: UUID): Url =
    Url(UUID.randomUUID(), uri.toURL, depth, maxDepth, from, crawlJob, now)

    def crawlJob(uri: String, maxDepth: Int): Url = {
      val crawlJobId = UUID.randomUUID()
      Url(UUID.randomUUID(), uri.toURL, 0, maxDepth, crawlJobId, crawlJobId, now)
    }
  }
}