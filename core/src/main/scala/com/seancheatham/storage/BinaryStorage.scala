package com.seancheatham.storage

import scala.concurrent.{ExecutionContext, Future}


/**
  * An interface for storing binary-like items
  */
abstract class BinaryStorage(implicit val ec: ExecutionContext) {

  /**
    * Fetch the value located in the bucket at the given key path
    *
    * @param key    The key path to the item
    * @return A Future optional Iterator of Bytes
    */
  def get(key: String*): Future[Iterator[Byte]]

  /**
    * Fetches the value located in the bucket at the given key path, if it exists.  In other words, lifts
    * [[com.seancheatham.storage.BinaryStorage#get(scala.collection.Seq)]] to
    * an Option[Iterator[Byte]
    *
    * @param key The key path to the item
    * @return A Future Optional value
    */
  def lift(key: String*): Future[Option[Iterator[Byte]]] =
    get(key: _*)
      .map(Some(_))
      .recover {
        case _: NoSuchElementException => None
      }

  /**
    * (Over)write the file located at the given path in the given bucket
    *
    * @param key    The key path to the item
    * @param value  An iterator of bytes to store
    * @return A Future
    */
  def write(key: String*)(value: Iterator[Byte]): Future[_]

  /**
    * Delete the value located in the bucket at the given key path
    *
    * @param key    The key path to the item
    * @return A Future
    */
  def delete(key: String*): Future[_]

}