package com.seancheatham.storage

import scala.concurrent.{ExecutionContext, Future}

/**
  * An interface for storing JSON-like documents
  *
  * @tparam T A readable/writable data type, such as a Json Value
  */
abstract class DocumentStorage[T] {

  /**
    * Fetch the value located in the bucket at the given key path
    *
    * @param key The key path to the item
    * @return A Future value, failed by [[NoSuchElementException]] if the value does not exist
    */
  def get(key: String*)(implicit ec: ExecutionContext): Future[T]

  /**
    * Fetches the value located in the bucket at the given key path, if it exists.  In other words, lifts
    * [[com.seancheatham.storage.DocumentStorage#get(scala.collection.Seq, scala.concurrent.ExecutionContext)]] to
    * an Option[T]
    *
    * @param key The key path to the item
    * @return A Future Optional value
    */
  def getOpt(key: String*)(implicit ec: ExecutionContext): Future[Option[T]] =
    get(key: _*)
      .map(Some(_))
      .recover {
        case _: NoSuchElementException => None
      }

  /**
    * A specialized version of [[com.seancheatham.storage.DocumentStorage#get(scala.collection.Seq, scala.concurrent.ExecutionContext)]]
    * which fetches an array value as an Iterator
    *
    * @param key The key path to the item
    * @return A Future Iterator of values, or an empty iterator if the value does not exist
    */
  def getCollection(key: String*)(implicit ec: ExecutionContext): Future[Iterator[T]]

  /**
    * Write (overwriting if anything exists there already) the given value to the given
    * key path located in the given bucket
    *
    * @param key   The key path to the item
    * @param value The value to write
    * @return A Future
    */
  def write(key: String*)(value: T)(implicit ec: ExecutionContext): Future[_]

  /**
    * Merge the given value into the given key path. A merge is performed by traversing into object paths, and overwriting
    * terminal node values.  However, any nodes which aren't touched remain as they were.
    *
    * @param key   The key path to the item
    * @param value The value to write
    * @return A Future
    */
  def merge(key: String*)(value: T)(implicit ec: ExecutionContext): Future[_]

  /**
    * Delete the value located in the bucket at the given key path
    *
    * @param key The key path to the item
    * @return A Future
    */
  def delete(key: String*)(implicit ec: ExecutionContext): Future[_]

  /**
    * Append the given value to the array located in the given bucket at the given key.
    *
    * @param key   The key path to the item
    * @param value The value to append
    * @return A Future containing either the ID or index of the appended item
    */
  def append(key: String*)(value: T)(implicit ec: ExecutionContext): Future[String]

}
