package com.intersys.cnap.crawler.service.database

import akka.stream.scaladsl._

trait Database[In, Mat] {
  def sink: Sink[In, Mat]
}
