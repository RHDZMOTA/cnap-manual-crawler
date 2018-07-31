import Dependencies._

lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
      organization := "com.intersys",
      scalaVersion := "2.12.6",
      version      := "0.0.0"
    )),
    name := "cnap-policy-crawler",
    libraryDependencies ++= {
      val akkaVersion = "2.5.13"
      val akkaHttpVersion = "10.1.1"
      val jsoupVersion = "1.11.3"
      val configVersion = "1.3.1"
      val solrAlpakkaVersion = "0.20"
      val cassandraAlpakkaVersion = "0.19"
      val scalaScraperVersion = "2.1.0"
      Seq(
        "org.jsoup" % "jsoup" % jsoupVersion,
        "com.typesafe" % "config" % configVersion,
        "com.typesafe.akka" %% "akka-actor" % akkaVersion,
        "com.typesafe.akka" %% "akka-stream" % akkaVersion,
        "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
        "com.typesafe.akka" %% "akka-http-core" % akkaHttpVersion,
        "com.lightbend.akka" %% "akka-stream-alpakka-cassandra" % cassandraAlpakkaVersion,
        "net.ruippeixotog" %% "scala-scraper" % scalaScraperVersion,
        scalaTest % Test
      )
    }
  )
