package com.seancheatham.storage.firebase

import fixtures.JsonDocumentStorageSpec
import play.api.libs.json.{JsString, JsValue}

class FirebaseDatabaseSpec extends JsonDocumentStorageSpec(FirebaseDatabase()) {

  "Firebase" can {
    val db = FirebaseDatabase()

    "observe a value" in {
      var value: Option[JsValue] =
        None

      val id =
        db.watchValue(testBucketName, "someValue")(
          ValueChangedHandler(
            (v: JsValue) =>
              value = Some(v)
          ),
          ValueRemovedHandler(
            () => value = None
          )
        )()

      db.write(testBucketName, "someValue")(JsString("test")).await
      Thread.sleep(3000)
      assert(value contains JsString("test"))

      db.delete(testBucketName, "someValue").await
      Thread.sleep(3000)
      assert(value.isEmpty)

      db.unwatchValue(id)

      assert {
        true
      }
    }

    "observe an array" in {

      import scala.collection.mutable

      val newItems =
        mutable.Buffer.empty[String]

      val id =
        db.watchCollection(testBucketName, "someArray")(
          ChildAddedHandler(
            (_: String, v: JsValue) => newItems += v.as[String]
          )
        )()

      Thread.sleep(3000)

      db.append(testBucketName, "someArray")(JsString("item1")).await
      db.append(testBucketName, "someArray")(JsString("item2")).await

      Thread.sleep(3000)

      assert(newItems == Seq("item1", "item2"))

      db.unwatchCollection(id)

      assert {
        true
      }

    }

    "cleanup again" in {
      db.delete(testBucketName)

      assert {
        true
      }
    }

  }

}
