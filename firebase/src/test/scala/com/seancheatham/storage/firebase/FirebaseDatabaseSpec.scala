package com.seancheatham.storage.firebase

import fixtures.JsonDocumentStorageSpec
import play.api.libs.json.{JsString, JsValue}

class FirebaseDatabaseSpec extends JsonDocumentStorageSpec(FirebaseDatabase()) {

  import scala.concurrent.ExecutionContext.Implicits.global

  "Firebase" can {

    "observe an array" in {
      val db = FirebaseDatabase()

      import scala.collection.mutable

      val newItems =
        mutable.Buffer.empty[String]

      val id =
        db.watchCollection(testBucketName, "someArray")(
          (_: String, v: JsValue) => newItems += v.as[String]
        )

      Thread.sleep(3000)

      db.append(testBucketName, "someArray")(JsString("item1")).await
      db.append(testBucketName, "someArray")(JsString("item2")).await

      Thread.sleep(3000)

      assert(newItems == Seq("item1", "item2"))

      db.unwatchCollection(id)

      assert(true)

    }

    "cleanup again" in {
      db.delete(testBucketName)

      assert(true)
    }

  }

}
