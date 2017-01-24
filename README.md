Database and Binary Storage Interface with adapter(s), written in Scala

[![Build Status](https://travis-ci.org/SeanCheatham/scala-storage.svg?branch=master)](https://travis-ci.org/SeanCheatham/scala-storage)

# Overview
This library serves two purposes:
1. Provide a common abstraction for accessing and manipulating databases and binary storage backends
2. Provide adapters for various databases and storage backends

I am a one-man show, so at best, what you see here is work I need in side projects.  I've open-sourced this library because other people may find some of it useful.

# Usage
This library is written in Scala.  It _might_ interoperate with other JVM languages, but I make no guarantees.

This library uses Typesafe's Play JSON library for serialization of content.  I hope to support other mechanisms at some point.

## Include the library in your project.
In your build.sbt:
`libraryDependencies += "com.seancheatham" %% "storage-firebase" % "0.1.2"`

## Connect to a Database
### Firebase
* Setup a [Firebase Service Account](https://console.firebase.google.com/project/_/settings/serviceaccounts/adminsdk)
* Generate/Download a new Private Key
* Store the key as you see fit, depending on your environment setup.  Just remember the path to it.
```scala
// You will need to provide an ExecutionContext.  If you have one, use it, otherwise, you can use this:
import scala.concurrent.ExecutionContext.Implicits.global

import com.seancheatham.storage.firebase.FirebaseDatabase
val db: FirebaseDatabase =
    FirebaseDatabase.fromServiceAccountKey("/path/to/key.json", "https://address-of-firebase-app.firebaseio.com")
```

## Write a value
Write or overwrite a value at a path.  If data already existed at the path, it will be completely replaced.
```scala
import com.seancheatham.storage.DocumentStorage.DocumentStorage
import play.api.libs.json._

val db: DocumentStorage[JsValue] = ???
val value = JsString("Alan")
val userId = "1"
val writeFuture: Future[_] = 
  db.write("users", userId, "firstName")(value)

```

## Read a value
```scala
val userId = "1"
val readFuture: Future[String] = 
  db.get("users", userId, "firstName") // References /users/1/firstName
    .map(_.as[String])
```

If the value doesn't exist, the Future will fail with a `NoSuchElementException`
Alternatively, if you know the value is generally optional, you can _lift_ it instead.
```scala
val readOptionalFuture: Future[Option[JsValue]] =
    db.lift("users", userId, "lastName")
```

## Merge a value
Merges the given value into the value located at the given path. For example:

*Original*
```json
{
  "firstName": "Alan",
  "email": "alan@turning.machine"

}
```

*To Merge In*
```json
{
  "email": "alan@saved.us",
  "lastName": "Turing"
}
```

*Results In*
```json
{
  "firstName": "Alan",
  "lastName": "Turing",
  "email": "alan@saved.us"
}
```

```scala
val value = Json.obj("email" -> "alan@saved.us", "lastName" -> "Turing")
val userId = "1"
val mergeFuture: Future[_] = 
  db.merge("users", userId)(value)
```

## Delete a value
```scala
val userId = "1"
val deleteFuture: Future[_] = 
  db.delete("users", userId, "lastName")
```

## FIREBASE ONLY:
Firebase provides functionality to attach listeners at key-paths in their realtime database.  This allows your
application to react to changes in data almost immediately after they occur.  Firebase allows for listening to a value
at a specific path, or for listening to children-changes at a specific path.
### Value Listeners:
Listen for changes or deletions of a value at a given path.
```scala
import com.seancheatham.storage.firebase._
val db: FirebaseDatabase = ???
val watcherId =
    db.watchValue("users", "1", "firstName")(
        // Provide as many handlers as you want
        // For example, if the value changes to a new value:
        ValueChangedHandler(
          (v: JsValue) =>
            println(s"Hello ${v.as[String]}.  It looks like you just changed your name!")
        ),
        // Or if the value changes: 
        ValueRemovedHandler(
          () =>
            println("User #1 just removed his/her first name")
        )
    )(
      // Optional cancellation/error handler
      Cancelled(
        (error: DatabaseError) =>
          println(s"Oops, something broke: $e")
      )
    )

// Make sure to clean up the watcher when you are finished with it.
db.unwatchValue(watcherId)
```

### Collection Listeners:
Listen for changes, additions, or deletions to children at a given path.  Children are sub-nodes of an object.  Firebase
does not use arrays; instead collections are represented as objects, where keys are sequential but not numeric.
As such, you can listen in when a new child is added (or changed or deleted).
```scala
import com.seancheatham.storage.firebase._
val db: FirebaseDatabase = ???
val watcherId =
    db.watchCollection("posts")(
        // Attach as many handlers as you want
        // For example, when a child is added
        ChildAddedHandler {
          (post: JsValue) =>
            val title =
              post.as[Map[String, JsValue]]("title").as[String]
            println(s"New post added: $postTitle")
        }
    )( /* Optional cancellation/error handler */)

// Make sure to clean up the watcher when you are finished with it.
db.unwatchCollection(watcherId)
```
