package com.seancheatham.storage.gcloud

import java.io.{FileInputStream, InputStream}
import java.util.NoSuchElementException

import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.storage.{BlobInfo, Storage, StorageOptions}
import com.seancheatham.storage.BinaryStorage
import com.typesafe.config.{Config, ConfigFactory}

import scala.concurrent.{ExecutionContext, Future}

/**
  * Implements [[com.seancheatham.storage.BinaryStorage]] with Google Cloud Storage as the
  * backend.  Google Cloud Storage stores files by bucket.  As such, the provided key for each
  * of the implemented methods must be at least two segments long.
  *
  * @param storage A reference to a Google Storage instance
  */
class GoogleCloudStorage(private val storage: Storage)(implicit ec: ExecutionContext) extends BinaryStorage {

  import GoogleCloudStorage.KeyHelper

  def get(key: String*): Future[Iterator[Byte]] =
    Option(
      storage.get(
        key.head,
        key.ensuring(_.length > 1).tail.keyify
      )
    )
      .map(_.getContent().iterator)
      .map(Future.successful)
      .getOrElse(Future.failed(new NoSuchElementException()))

  def write(key: String*)(value: Iterator[Byte]): Future[Unit] =
    Future(
      storage.create(
        BlobInfo.newBuilder(
          key.head,
          key.ensuring(_.length > 1).tail.keyify
        ).build(),
        new InputStream {
          def read(): Int =
            if (value.hasNext)
              value.next()
            else
              -1
        }
      )
    )

  def delete(key: String*): Future[Unit] =
    Future(
      storage.delete(
        key.head,
        key.ensuring(_.length > 1).tail.keyify
      )
    )

}

object GoogleCloudStorage {

  /**
    * The default instance, with the details provided by the default typesafe config loader
    */
  lazy val default: GoogleCloudStorage =
    fromConfig(ConfigFactory.load())(scala.concurrent.ExecutionContext.Implicits.global)

  /**
    * @param config a Typesafe Config object containing at least:
    *               google.cloud.storage.project.id
    *               google.cloud.credentials.key.location
    */
  def fromConfig(config: Config)(implicit ec: ExecutionContext): GoogleCloudStorage = {
    val projectId: String =
      config.getString("google.cloud.storage.project.id")

    apply(
      projectId,
      config.getString("google.cloud.credentials.key.location")
    )
  }

  /**
    * Constructs a [[GoogleCloudStorage]] using the default Typesafe config
    *
    * @return a [[GoogleCloudStorage]]
    */
  def apply(): GoogleCloudStorage =
    default

  /**
    * Constructs a [[GoogleCloudStorage]] using the provided configuration
    *
    * @return a [[GoogleCloudStorage]]
    */
  def apply(config: Config)(implicit ec: ExecutionContext): GoogleCloudStorage =
    fromConfig(config)

  def apply(projectId: String,
            credentialsPath: String)(implicit ec: ExecutionContext): GoogleCloudStorage =
    new GoogleCloudStorage(
      StorageOptions.newBuilder()
        .setProjectId(projectId)
        .setCredentials(GoogleCredentials.fromStream(new FileInputStream(credentialsPath)))
        .build()
        .getService
    )

  implicit class KeyHelper(key: Seq[String]) {
    def keyify: String =
      key mkString "."
  }

}