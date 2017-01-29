package fixtures

import com.seancheatham.storage.BinaryStorage
import org.scalatest.WordSpec

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

abstract class BinaryStorageSpec(val bs: BinaryStorage,
                                 val testBucketName: String = "test_bucket_12435624572457625") extends WordSpec {

  "A Binary Storage Database" can {
    val byteString =
      "A test file"
    "write a single file" in {
      val bytes =
        byteString.getBytes.iterator

      assert {
        bs.write(testBucketName, "item")(bytes).await
        true
      }
    }

    "read a file" in {
      val result =
        bs.get(testBucketName, "item").await

      assert(result.toSeq == byteString.toSeq)
    }

    "read an optional value" in {

      val someResult =
        bs.lift(testBucketName, "item").await

      assert(someResult.get.toSeq == byteString.toSeq)

      val emptyResult =
        bs.lift(testBucketName, "other").await

      assert(emptyResult.isEmpty)

    }

    "delete a file" in {
      bs.delete(testBucketName, "item").await

      assert(bs.lift(testBucketName, "item").await.isEmpty)
    }
  }

  implicit class Awaiter[T](f: Future[T]) {
    def await: T =
      Await.result(f, Duration.Inf)

    def awaitReady: Future[T] =
      Await.ready(f, Duration.Inf)
  }

}
