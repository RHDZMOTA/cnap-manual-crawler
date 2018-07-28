package com.intersys.cnap.crawler.service.storage

import akka.stream.scaladsl.Sink

trait Storage[In, Mat] {
  def sink: Sink[In, Mat]
}
