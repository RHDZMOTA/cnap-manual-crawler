package com.intersys.cnap.crawler.service.database.impl

import java.sql.{Connection, DriverManager, Statement, Timestamp}
import java.util.UUID

import akka.NotUsed
import akka.stream.scaladsl.{Flow, Sink}
import com.intersys.cnap.crawler.conf.{Context, Settings}
import com.intersys.cnap.crawler.service.database.Database
import com.intersys.cnap.crawler.util.ClientHttp.CustomResponse
import net.ruippeixotog.scalascraper.scraper.ContentExtractors.text
import net.ruippeixotog.scalascraper.dsl.DSL._
import net.ruippeixotog.scalascraper.dsl.DSL.Extract._
import net.ruippeixotog.scalascraper.browser.JsoupBrowser
import net.ruippeixotog.scalascraper.model._

case object PostgreSQL {
  Class.forName(Settings.Postgres.driver)

  private val browser = JsoupBrowser()

  val connectionString: String =
    s"jdbc:postgresql://${Settings.Postgres.address}:${Settings.Postgres.port}/${Settings.Postgres.database}"
  val connection: Connection = DriverManager.getConnection(
    connectionString, Settings.Postgres.username, Settings.Postgres.pwd)
  val statement: Statement = connection.createStatement()

  case object UrlTable extends Database[CustomResponse, NotUsed] with Context {
    def query(id: UUID, uri: String, depth: Int, max_depth: Int, fromUrl: UUID, crawlJob: UUID, timestamp: Timestamp, rawBody: String, body: String, breadcrumbs: String): String =
      s"""
         |INSERT INTO ${Settings.Postgres.Url.table}
         |VALUES (
         | '${id.toString}',
         | '$uri',
         | $depth,
         | $max_depth,
         | '${fromUrl.toString}',
         | '${crawlJob.toString}',
         | '${timestamp.toString}',
         | '$rawBody',
         | '$body',
         | '$breadcrumbs)'""".stripMargin

    def postgreSink: Sink[CustomResponse, NotUsed] =
      Flow[CustomResponse].map(customResponse => {
        val url = customResponse.url
        val rawBody: String = customResponse.content match {
          case None => ""
          case Some(resp) => resp.map(_.toChar).mkString
        }
        val body: String = customResponse.content match {
          case None => ""
          case Some(resp) => browser.parseString(
            browser.parseString(rawBody) >> allText("#page_content")) >> allText
        }
        val breadcrumbs: String = customResponse.content match {
          case None => ""
          case Some(resp) => browser.parseString(
            browser.parseString(rawBody) >> allText(".ww_skin_breadcrumbs")) >> allText
        }
        query(
          url.id,
          url.getUri,
          url.depth,
          url.maxDepth,
          url.from,
          url.crawlJob,
          url.timestamp,
          rawBody,
          body,
          breadcrumbs
        )
      }).map(statement.execute).to(Sink.ignore)

    override def sink: Sink[CustomResponse, NotUsed] =
      Flow[CustomResponse].filter(x => x.content match  {
        case None => false
        case Some(resp) =>
          val document = browser.parseString( resp.map(_.toChar).mkString )
          val heading: String = document >?> text("div[class^=Heading_]") getOrElse ""
          !heading.toLowerCase.contains("removed")
      }).to(postgreSink)

  }

  case object AnswerExtraction extends Database[CustomResponse, NotUsed] with Context {

    final case class Answer(id: UUID, url: UUID, uri: String, heading: String, var text: String)

    object Answer {
      def apply(url: UUID, uri: String, heading: String, text: String): Answer =
        Answer(UUID.randomUUID(), url, uri, heading, text)
    }

    def query(id: UUID, url: UUID, uri: String, heading: String, text: String): String =
      s"""
        |INSERT INTO ${Settings.Postgres.Answer.table}(id,url,uri,heading,text)
        |VALUES ('${id.toString}','${url.toString}','$uri','$heading','$text');
      """.stripMargin

    def postgreSink: Sink[Answer, NotUsed] =
      Flow[Answer].map(a => query(a.id, a.url, a.uri, a.heading, a.text)).map(statement.execute).to(Sink.ignore)

    override def sink: Sink[CustomResponse, NotUsed] =
      Flow[CustomResponse].mapConcat(customResponse => {
        val document: Option[Document] = customResponse.content.map(r => browser.parseString(r.map(_.toChar).mkString))
        val heading: String = document.flatMap {doc => doc >?> text("div[class^=Heading_]")} getOrElse ""
        val elements: List[Element] = document.map {doc => (doc >> elementList("div"))
            .filter(_.attrs.get("class").exists(classVal => (classVal contains "Body_Text_") || (classVal contains "List_Bullet_")))
            .filter(_.text.length != 0)
        } getOrElse List[Element]()
        val answers: List[Answer] = elements.foldLeft(List[Answer]()){(acc, e) => acc.reverse match {
          case Nil => List(Answer(customResponse.url.id, customResponse.url.getUri, heading, e.text))
          case head :: tail =>
            if (e.attrs("class") contains "Body_Text_")
              acc :+ Answer(customResponse.url.id, customResponse.url.getUri, heading, e.text)
            else {
              head.text += ("/n" + e.text)
              (head +: tail).reverse
            }
        }}
        answers
      }).to(postgreSink)
  }

}
