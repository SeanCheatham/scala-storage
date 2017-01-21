package fixtures

import com.seancheatham.storage.DocumentStorage
import org.scalatest.WordSpec
import play.api.libs.json.{JsString, JsValue, Json}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.util.Failure

abstract class JsonDocumentStorageSpec(val db: DocumentStorage[JsValue],
                                       val testBucketName: String = "test_bucket_12435624572457625") extends WordSpec {

  import scala.concurrent.ExecutionContext.Implicits.global

  "A Document Database" can {

    "write a single value" in {
      db.write(testBucketName, "item")(Json.obj("foo" -> 645, "bar" -> Seq.empty[String])).await

      assert(true)
    }

    "read a single value" in {

      val result =
        db.get(testBucketName, "item", "foo").await

      assert(result.as[Int] == 645)

    }

    "push to an array" in {

      db.append(testBucketName, "item", "bar")(JsString("item1")).await
      db.append(testBucketName, "item", "bar")(JsString("item2")).await

      val result =
        db.getCollection(testBucketName, "item", "bar").await

      assert(result.toSeq.map(_.as[String]) == Seq("item1", "item2"))

    }

    "merge an object" in {

      db.merge(testBucketName, "item")(Json.obj("baz" -> true)).await

      val result =
        db.get(testBucketName, "item").await.as[Map[String, JsValue]]

      assert(result("foo").as[Int] == 645)
      assert(result("baz").as[Boolean])

    }

    "delete a value" in {
      db.delete(testBucketName, "item", "foo").awaitReady

      val f1 =
        db.get(testBucketName, "item", "foo")
          .awaitReady
          .value
          .get

      assert(
        f1 match {
          case Failure(_: NoSuchElementException) =>
            true
          case x =>
            false
        }
      )

      db.delete(testBucketName, "item").awaitReady

      val f2 =
        db.get(testBucketName, "item")
          .awaitReady
          .value
          .get

      assert(
        f2 match {
          case Failure(_: NoSuchElementException) =>
            true
          case x =>
            false
        }
      )

    }

    "cleanup" in {
      db.delete(testBucketName)

      assert(true)
    }

  }

  implicit class Awaiter[T](f: Future[T]) {
    def await: T =
      Await.result(f, Duration.Inf)

    def awaitReady: Future[T] =
      Await.ready(f, Duration.Inf)
  }

}
