package com.intersys.cnap.crawler.service.database.impl

import akka.stream.alpakka.cassandra.scaladsl.CassandraSink
import akka.stream.scaladsl.{Flow, Sink}
import akka.{Done, NotUsed}
import com.datastax.driver.core.{BoundStatement, Cluster, PreparedStatement, Session}
import com.intersys.cnap.crawler.conf.{Context, Settings}
import com.intersys.cnap.crawler.service.database.Database
import com.intersys.cnap.crawler.util.ClientHttp.CustomResponse
import net.ruippeixotog.scalascraper.dsl.DSL._
import net.ruippeixotog.scalascraper.dsl.DSL.Extract._
import net.ruippeixotog.scalascraper.browser.JsoupBrowser
import net.ruippeixotog.scalascraper.model._
import java.util.UUID

import scala.concurrent.Future

case object Cassandra {

  private val browser = JsoupBrowser()

  implicit private val session: Session = Cluster.builder
    .addContactPoint(Settings.Cassandra.address)
    .withPort(Settings.Cassandra.port)
    .build.connect()

  case object UrlTable extends Database[CustomResponse, NotUsed] with Context {

    private val query: String =
      s"""
         |INSERT INTO ${Settings.Cassandra.keyspaceName}.${Settings.Cassandra.Url.table}(id, uri, depth, max_depth, from_url, crawl_job, timestamp, raw_body, body, breadcrumbs)
         |VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
     """.stripMargin

    private val preparedStatement: PreparedStatement = session.prepare(query)

    private val statementBinder: (CustomResponse, PreparedStatement) => BoundStatement =
      (customResponse: CustomResponse, statement: PreparedStatement) => {
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
        statement.bind(
          url.id,
          url.getUri,
          url.depth.asInstanceOf[java.lang.Integer],
          url.maxDepth.asInstanceOf[java.lang.Integer],
          url.from,
          url.crawlJob,
          url.timestamp,
          rawBody,
          body,
          breadcrumbs)
      }

    val cassandraSink: Sink[CustomResponse, Future[Done]] = CassandraSink[CustomResponse](
      Settings.Cassandra.Url.parallelism,
      preparedStatement,
      statementBinder
    )

    override def sink: Sink[CustomResponse, NotUsed] =
      Flow[CustomResponse].filter(x => x.content match  {
        case None => false
        case Some(resp) =>
          val document = browser.parseString( resp.map(_.toChar).mkString )
          val heading: String = document >?> text("div[class^=Heading_]") getOrElse ""
          !heading.toLowerCase.contains("removed")
      }).to(cassandraSink)
  }
  case object AnswerExtraction extends Database[CustomResponse, NotUsed] {

    final case class Answer(id: UUID, url: UUID, uri: String, heading: String, var text: String)

    object Answer {
      def apply(url: UUID, uri: String, heading: String, text: String): Answer =
        Answer(UUID.randomUUID(), url, uri, heading, text)
    }

    private val query: String =
      s"""
        |INSERT INTO ${Settings.Cassandra.keyspaceName}.${Settings.Cassandra.Answer.table}(id, url, uri, heading, text)
        |VALUES (?, ?, ?, ?, ?)
      """.stripMargin

    private val preparedStatement: PreparedStatement = session.prepare(query)

    private val statementBinder: (Answer, PreparedStatement) => BoundStatement =
      (answer: Answer, statement: PreparedStatement) => statement.bind(
        answer.id,
        answer.url,
        answer.uri,
        answer.heading,
        answer.text
      )

    val cassandraSink: Sink[Answer, Future[Done]] = CassandraSink[Answer](
      Settings.Cassandra.Answer.parallelism,
      preparedStatement,
      statementBinder
    )

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
      }).to(cassandraSink)
  }
}
