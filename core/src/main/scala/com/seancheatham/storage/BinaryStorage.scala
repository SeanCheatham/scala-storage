package com.seancheatham.storage

import scala.concurrent.{ExecutionContext, Future}


/**
  * An interface for storing binary-like items
  */
abstract class BinaryStorage {

  /**
    * Fetch the value located in the bucket at the given key path
    *
    * @param key    The key path to the item
    * @return A Future optional Iterator of Bytes
    */
  def get(key: String*)(implicit ec: ExecutionContext): Future[Iterator[Byte]]

  /**
    * (Over)write the file located at the given path in the given bucket
    *
    * @param key    The key path to the item
    * @param value  An iterator of bytes to store
    * @return A Future
    */
  def write(key: String*)(value: Iterator[Byte])(implicit ec: ExecutionContext): Future[_]

  /**
    * Delete the value located in the bucket at the given key path
    *
    * @param key    The key path to the item
    * @return A Future
    */
  def delete(key: String*)(implicit ec: ExecutionContext): Future[_]

}