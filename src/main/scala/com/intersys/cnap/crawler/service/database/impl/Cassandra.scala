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

import scala.concurrent.Future

case object Cassandra extends Database[CustomResponse, NotUsed] with Context {

  private val browser = JsoupBrowser()

  implicit private val session: Session = Cluster.builder
    .addContactPoint(Settings.Cassandra.address)
    .withPort(Settings.Cassandra.port)
    .build.connect()

  private val query: String =
    s"""
       |INSERT INTO ${Settings.Cassandra.keyspaceName}.${Settings.Cassandra.urlTable}(id, uri, depth, max_depth, from_url, crawl_job, timestamp, body)
       |VALUES (?, ?, ?, ?, ?, ?, ?, ?)
     """.stripMargin

  private val preparedStatement: PreparedStatement = session.prepare(query)

  private val statementBinder: (CustomResponse, PreparedStatement) => BoundStatement =
    (customResponse: CustomResponse, statement: PreparedStatement) => {
      val url = customResponse.url
      val body: String = customResponse.content match {
        case None => ""
        case Some(resp) => browser.parseString(
          browser.parseString(resp.map(_.toChar).mkString) >> allText("#page_content")) >> allText
      }
      statement.bind(
        url.id, url.getUri, url.depth.asInstanceOf[java.lang.Integer], url.maxDepth.asInstanceOf[java.lang.Integer],
        url.from, url.crawlJob, url.timestamp, body)
    }

  val cassandraSink: Sink[CustomResponse, Future[Done]] = CassandraSink[CustomResponse](
    Settings.Cassandra.parallelism,
    preparedStatement,
    statementBinder
  )

  override def sink: Sink[CustomResponse, NotUsed] =
    Flow[CustomResponse].map(customResp => customResp).to(cassandraSink)
}
