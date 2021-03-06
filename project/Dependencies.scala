import sbt._

object Dependencies {

  object versions {
    val play = "2.5.10"
  }

  val playJson =
    Seq(
      "com.typesafe.play" %% "play-json" % versions.play
    )

  val playWS =
    Seq(
      "com.typesafe.play" %% "play-ws" % versions.play
    )

  val typesafe =
    Seq(
      "com.typesafe" % "config" % "1.3.1"
    )

  val test =
    Seq(
      "org.scalatest" %% "scalatest" % "3.0.1" % "test"
    )

  val logging =
    Seq(
      "ch.qos.logback" % "logback-classic" % "1.1.7",
      "com.typesafe.scala-logging" %% "scala-logging" % "3.5.0"
    )

  val hbase = {
    val version = "1.2.4"
    Seq(
      "org.apache.hbase" % "hbase-client" % version,
      "org.apache.hbase" % "hbase-common" % version,
      "org.apache.hadoop" % "hadoop-common" % "2.6.5"
    )
  }

  val bigTable =
    Seq(
      "com.google.cloud.bigtable" % "bigtable-hbase-1.2" % "0.9.4",
      "io.netty" % "netty-tcnative-boringssl-static" % "1.1.33.Fork19"
    )

  val firebase =
    Seq(
      "com.google.firebase" % "firebase-admin" % "4.0.3"
    )

  val googleCloudStorage =
    Seq(
      "com.google.cloud" % "google-cloud-storage" % "0.8.1-beta"
    )

}
