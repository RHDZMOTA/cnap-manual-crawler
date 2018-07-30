package com.intersys.cnap.crawler.service.crawler

import akka.NotUsed
import akka.stream.scaladsl.Flow
import com.intersys.cnap.crawler.conf.{Context, Settings}
import com.intersys.cnap.crawler.util.ClientHttp
import com.intersys.cnap.crawler.util.ClientHttp.{CustomResponse, Url}
import org.jsoup.Jsoup

import scala.collection.JavaConverters._

object Crawler extends ClientHttp with Context {

  val download: Flow[Url, CustomResponse, NotUsed] =
    Flow[Url].mapAsync[CustomResponse](Settings.Crawler.parallelism)(getRequestWithCustomResponse)

  val getChildRefs: Flow[CustomResponse, Url, NotUsed] =
    Flow[CustomResponse].mapConcat(customResponse => extractUrls(from=customResponse))

  def extractUrls(from: CustomResponse): List[Url] = (from.url, from.content, from.mediaTye.map(_.fileExtensions contains "htm")) match {
    case (url, Some(content), Some(true)) => Jsoup.parse(content.map(_.toChar).mkString).select("a[href]").asScala.toList
      .map(_.attr("href").trim).map(href => if (href.endsWith("#")) href.init else href)
      .filter(href => href.length != 0 || href == url.getUri).map(
      href => {
        val uri = if (href.startsWith("http")) href else url.getUri + "/" + href
        val pattern = "/([\\w]+[.]html)/"
        val depth = url.depth + 1
        Url(uri.replaceAll(pattern, "/"), depth, url.maxDepth, url.id, url.crawlJob)
      }
    )
    case _ => Nil
  }

}
