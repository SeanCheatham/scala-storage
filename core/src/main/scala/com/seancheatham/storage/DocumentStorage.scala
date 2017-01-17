package com.seancheatham.storage

import scala.concurrent.{ExecutionContext, Future}

/**
  * An interface for storing JSON-like documents
  * @tparam T A readable/writable data type, such as a Json Value
  */
abstract class DocumentStorage[T] {

  /**
    * Fetch the value located in the bucket at the given key path
    *
    * @param key The key path to the item
    * @return A Future value
    *         If the value at the given keypath is null or non-existent,
    *         the Future will fail with a [[NoSuchElementException]]
    */
  def get(key: String*)(implicit ec: ExecutionContext): Future[T]

  /**
    * A specialized version of [[com.seancheatham.storage.DocumentStorage#get(java.lang.String, scala.collection.Seq, scala.concurrent.ExecutionContext)]]
    * which fetches an array value as an Iterator
    *
    * @param key The key path to the item
    * @return A Future Iterator of values.  If the value at the given keypath is null or non-existent,
    *         the Future will succeed with an empty Iterator will be returned
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
