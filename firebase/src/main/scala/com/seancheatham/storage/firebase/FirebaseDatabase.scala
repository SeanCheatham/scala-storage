package com.seancheatham.storage.firebase

import java.io.{ByteArrayInputStream, File, FileInputStream, InputStream}
import java.util.{NoSuchElementException, UUID}

import com.google.firebase.database.{ChildEventListener, DataSnapshot, DatabaseError, ValueEventListener}
import com.google.firebase.tasks.{OnFailureListener, OnSuccessListener}
import com.google.firebase.{FirebaseApp, FirebaseOptions}
import com.seancheatham.storage.DocumentStorage
import com.typesafe.config.{Config, ConfigFactory}
import play.api.libs.json._

import scala.collection.concurrent.TrieMap
import scala.concurrent.{ExecutionContext, Future, Promise}

/**
  * Implements [[com.seancheatham.storage.DocumentStorage#DocumentStorage()]] with Firebase Realtime Database as the
  * backend.  Firebase provides a JSON-like document storage structure, allowing for nested objects.  Firebase, by nature,
  * does not support or use arrays.  Instead, all arrays are stored as objects.  If an array ([[JsArray]]) is given,
  * Firebase will store it as an object with the keys being the stringified indices of the given array.  Although
  * annoying at first, this brings numerous benefits to their platform.  Most notably is the ability to rapidly
  * append values to an "array" through their `push` concept.  Furthermore, they also provide a layer to "listen"
  * to an "array" (or object), asynchronously running callbacks for new items.
  *
  * @param app The FirebaseApp to use when connecting
  */
class FirebaseDatabase(private val app: FirebaseApp) extends DocumentStorage[JsValue] {

  import com.google.firebase.database.{FirebaseDatabase => GFirebaseDatabase}

  /**
    * An Admin Database reference to the Firebase Database
    */
  val database: GFirebaseDatabase =
    GFirebaseDatabase.getInstance(app)

  def get(key: String*)(implicit ec: ExecutionContext): Future[JsValue] = {
    val p = Promise[JsValue]()
    database.getReference(key.keyify)
      .addListenerForSingleValueEvent(
        new ValueEventListener {
          def onDataChange(dataSnapshot: DataSnapshot): Unit =
            anyToJson(dataSnapshot.getValue()).toOption match {
              case Some(value) =>
                p success value
              case _ =>
                p failure new NoSuchElementException
            }

          def onCancelled(databaseError: DatabaseError): Unit =
            p failure new IllegalStateException(databaseError.getMessage)
        }
      )
    p.future
  }

  def getCollection(key: String*)(implicit ec: ExecutionContext): Future[Iterator[JsValue]] =
    get(key: _*)
      .recover {
        case _: NoSuchElementException =>
          Iterator.empty
      }
      .map {
        case v: JsArray =>
          v.value.iterator
        case v: JsObject =>
          v.fields.sortBy(_._1).map(_._2).iterator
        case _ =>
          Iterator.empty
      }

  def write(key: String*)(value: JsValue)(implicit ec: ExecutionContext): Future[_] = {
    val p = Promise[Any]()
    database.getReference(key.keyify)
      .setValue(jsonToAny(value))
      .addOnSuccessListener(new OnSuccessListener[Void] {
        def onSuccess(tResult: Void): Unit =
          p success()
      })
      .addOnFailureListener(new OnFailureListener {
        def onFailure(e: Exception): Unit =
          p failure e
      })
    p.future
  }

  def merge(key: String*)(value: JsValue)(implicit ec: ExecutionContext): Future[_] = {
    val p = Promise[Any]()
    val reference =
      database.getReference(key.keyify)
    (value match {
      case v: JsObject =>
        reference.updateChildren(jsonToAny(v).asInstanceOf[java.util.Map[String, AnyRef]])
      case v =>
        reference.setValue(jsonToAny(v))
    })
      .addOnSuccessListener(new OnSuccessListener[Void] {
        def onSuccess(tResult: Void): Unit =
          p success()
      })
      .addOnFailureListener(new OnFailureListener {
        def onFailure(e: Exception): Unit =
          p failure e
      })
    p.future
  }

  def delete(key: String*)(implicit ec: ExecutionContext): Future[_] = {
    val p = Promise[Any]()
    database.getReference(key.keyify)
      .removeValue()
      .addOnSuccessListener(new OnSuccessListener[Void] {
        def onSuccess(tResult: Void): Unit =
          p success()
      })
      .addOnFailureListener(new OnFailureListener {
        def onFailure(e: Exception): Unit =
          p failure e
      })
    p.future
  }

  def append(key: String*)(value: JsValue)(implicit ec: ExecutionContext): Future[String] = {
    val p = Promise[String]()
    val reference =
      database.getReference(key.keyify)
    reference
      .push()
      .setValue(jsonToAny(value))
      .addOnSuccessListener(new OnSuccessListener[Void] {
        def onSuccess(tResult: Void): Unit =
          p success reference.getKey
      })
      .addOnFailureListener(new OnFailureListener {
        def onFailure(e: Exception): Unit =
          p failure e
      })
    p.future
  }

  /**
    * A Mapping from (Watcher ID -> (Firebase Event Listener, Firebase key path))
    */
  private val valueWatchers =
    TrieMap.empty[String, (ValueEventListener, String)]

  /**
    * Firebase provides functionality to attach listeners to nodes in its Realtime Database.  This method
    * attaches a listener to the node at the given bucket+key, and whenever the node changes or is deleted, the given
    * handlers will be called when relevant.
    *
    * NOTE: This method has a side-effect
    *
    * TODO: How do we ensure old/unused listeners get cleaned up when a client terminates
    *
    * @param key           The key path to the item
    * @param handlers      A collection of ValueHandlers to apply
    * @param cancelHandler A special handler called when the listener is canceled or disconnected.  If this event occurs,
    *                      the watcher will automatically be unwatched/removed, so there is no need to do it in this
    *                      step.  The default handler does nothing.
    * @return An identifier for the watcher, to be used when removing the watcher
    */
  def watchValue(key: String*)
                (handlers: ValueHandler*)
                (cancelHandler: Cancelled = Cancelled((_: DatabaseError) => ())): String = {
    val id =
      UUID.randomUUID().toString
    val listener =
      new ValueEventListener {
        def onDataChange(dataSnapshot: DataSnapshot): Unit = {
          anyToJson(dataSnapshot.getValue()).toOption match {
            case Some(value) =>
              handlers
                .collect {
                  case ValueChangedHandler(handler) =>
                    handler
                }
                .foreach(_.apply(value))
            case _ =>
              handlers
                .collect {
                  case ValueRemovedHandler(handler) =>
                    handler
                }
                .foreach(_.apply())
          }
        }

        def onCancelled(databaseError: DatabaseError): Unit = {
          unwatchValue(id)
          cancelHandler.handler(databaseError)
        }
      }

    val keyified =
      key.keyify
    valueWatchers.update(id, (listener, keyified))
    database.getReference(keyified).addValueEventListener(listener)
    id
  }

  /**
    * Removes a watcher by ID, as constructed in #watchValue(...)
    *
    * NOTE: This method has a side-effect
    *
    * @param id The ID of the watcher provided in #watchValue(...)
    * @return Unit
    */
  def unwatchValue(id: String): Unit =
    valueWatchers
      .remove(id)
      .foreach(
        kv =>
          database.getReference(kv._2).removeEventListener(kv._1)
      )

  /**
    * A Mapping from (Watcher ID -> (Firebase Event Listener, Firebase key path))
    */
  private val collectionWatchers =
    TrieMap.empty[String, (ChildEventListener, String)]

  /**
    * Firebase provides functionality to attach listeners to array-like nodes in its Realtime Database.  This method
    * attaches a listener to the node at the given bucket+key, and when a new item is added, the function f is called.
    *
    * NOTE: This method has a side-effect
    *
    * TODO: How do we ensure old/unused listeners get cleaned up when a client terminates
    *
    * @param handlers      A collection of ChildHandlers to apply
    * @param cancelHandler A special handler called when the listener is canceled or disconnected.  If this event occurs,
    *                      the watcher will automatically be unwatched/removed, so there is no need to do it in this
    *                      step.  The default handler does nothing.
    * @return An identifier for the watcher, to be used when removing the watcher
    */
  def watchCollection(key: String*)
                     (handlers: ChildHandler*)
                     (cancelHandler: Cancelled = Cancelled((_: DatabaseError) => ())): String = {
    val id =
      UUID.randomUUID().toString
    val listener =
      new ChildEventListener {
        def onChildRemoved(dataSnapshot: DataSnapshot): Unit = {
          val key =
            dataSnapshot.getKey
          handlers
            .collect {
              case ChildRemovedHandler(handler) =>
                handler
            }
            .foreach(_.apply(key))
        }

        def onChildMoved(dataSnapshot: DataSnapshot, s: String): Unit = {}

        def onChildChanged(dataSnapshot: DataSnapshot, s: String): Unit = {
          val key =
            dataSnapshot.getKey
          val value =
            anyToJson(dataSnapshot.getValue())
          handlers
            .collect {
              case ChildChangedHandler(handler) =>
                handler
            }
            .foreach(_.apply(key, value))
        }

        def onCancelled(databaseError: DatabaseError): Unit = {
          unwatchCollection(id)
          cancelHandler.handler(databaseError)
        }

        def onChildAdded(dataSnapshot: DataSnapshot, s: String): Unit = {
          val key =
            dataSnapshot.getKey
          val value =
            anyToJson(dataSnapshot.getValue())
          handlers
            .collect {
              case ChildAddedHandler(handler) =>
                handler
            }
            .foreach(_.apply(key, value))
        }
      }
    val keyified =
      key.keyify
    collectionWatchers.update(id, (listener, keyified))
    database.getReference(keyified)
      .addChildEventListener(listener)
    id
  }

  /**
    * Removes a watcher by ID, as constructed in #watchCollection(...)
    *
    * NOTE: This method has a side-effect
    *
    * @param id The ID of the watcher provided in #watchCollection(...)
    * @return Unit
    */
  def unwatchCollection(id: String): Unit =
    collectionWatchers.remove(id)
      .foreach(
        kv =>
          database.getReference(kv._2)
            .removeEventListener(kv._1)
      )

  implicit class KeyHelper(key: Seq[String]) {
    def keyify: String =
      key mkString "/"
  }

  implicit class JsHelper(v: JsValue) {
    def toOption: Option[JsValue] =
      v match {
        case JsNull =>
          None
        case x =>
          Some(x)
      }
  }

  /**
    * Converts a value returned by Firebase into a [[JsValue]]
    *
    * @param any The value returned by Firebase
    * @return a JsValue
    */
  private def anyToJson(any: Any): JsValue =
    any match {
      case null =>
        JsNull
      case v: Double =>
        JsNumber(v)
      case v: Long =>
        JsNumber(v)
      case s: String =>
        JsString(s)
      case v: Boolean =>
        JsBoolean(v)
      case v: java.util.HashMap[String@unchecked, _] =>
        import scala.collection.JavaConverters._
        JsObject(v.asScala.mapValues(anyToJson))
      case v: java.util.ArrayList[_] =>
        import scala.collection.JavaConverters._
        JsArray(v.asScala.map(anyToJson))
    }

  /**
    * Converts the given [[JsValue]] into a consumable format by the Firebase API
    *
    * @param json The JSON value to convert
    * @return a value consumable by the Firebase API
    */
  private def jsonToAny(json: JsValue): Any =
    json match {
      case JsNull =>
        null
      case v: JsNumber =>
        val long =
          v.value.longValue()
        val double =
          v.value.doubleValue()
        if (long == double)
          long
        else
          double
      case v: JsString =>
        v.value
      case v: JsBoolean =>
        v.value
      case v: JsArray =>
        import scala.collection.JavaConverters._
        v.value.toVector.map(jsonToAny).asJava
      case v: JsObject =>
        import scala.collection.JavaConverters._
        v.value.mapValues(jsonToAny).asJava
    }

}


object FirebaseDatabase {

  /**
    * The default instance, with the details provided by the default typesafe config loader
    */
  lazy val default: FirebaseDatabase =
    fromConfig(ConfigFactory.load())

  /**
    * @param config a Typesafe Config object containing at least:
    *               firebase.url
    *               firebase.project_id
    *               firebase.private_key_id
    *               firebase.client_email
    *               firebase.client_id
    *               firebase.client_x509_cert_url
    */
  def fromConfig(config: Config): FirebaseDatabase = {
    val baseUrl: String =
      config.getString("firebase.url")
        .ensuring(_ startsWith "https://")

    if (config.hasPath("firebase.service_account_key_location"))
      fromServiceAccountKey(
        config.getString("firebase.service_account_key_location"),
        baseUrl
      )

    else
      apply(
        baseUrl,
        config.getString("firebase.project_id"),
        config.getString("firebase.private_key_id"),
        config.getString("firebase.private_key"),
        config.getString("firebase.client_email"),
        config.getString("firebase.client_id"),
        config.getString("firebase.client_x509_cert_url")
      )
  }

  /**
    * Constructs a FirebaseDatabase from a Service Account credentials file (path)
    *
    * @param path    the path to the service account credentials
    * @param baseUrl the base URL of the database
    * @return a FirebaseDatabase
    */
  def fromServiceAccountKey(path: String, baseUrl: String): FirebaseDatabase =
    fromServiceAccountKey(new FileInputStream(path), baseUrl)

  /**
    * Constructs a FirebaseDatabase from a Service Account credentials file
    *
    * @param file    the file with the service account credentials
    * @param baseUrl the base URL of the database
    * @return a FirebaseDatabase
    */
  def fromServiceAccountKey(file: File, baseUrl: String): FirebaseDatabase =
    fromServiceAccountKey(new FileInputStream(file), baseUrl)

  /**
    * Constructs a FirebaseDatabase from a Service Account credentials (stream)
    *
    * @param inputStream the path to the service account credentials
    * @param baseUrl     the base URL of the database
    * @return a FirebaseDatabase
    */
  def fromServiceAccountKey(inputStream: InputStream, baseUrl: String): FirebaseDatabase =
    new FirebaseDatabase(
      FirebaseApp.initializeApp(
        new FirebaseOptions.Builder()
          .setServiceAccount(inputStream)
          .setDatabaseUrl(baseUrl)
          .build()
      )
    )

  /**
    * Constructs a [[FirebaseDatabase]] using the default Typesafe config
    *
    * @return a [[FirebaseDatabase]]
    */
  def apply(): FirebaseDatabase =
    default

  /**
    * Constructs a [[FirebaseDatabase]] using the provided configuration
    *
    * @return a [[FirebaseDatabase]]
    */
  def apply(config: Config): FirebaseDatabase =
    fromConfig(config)

  /**
    * Construct a FirebaseDatabase using the given configuration values
    *
    * @param baseUrl           The Base Path to the database
    * @param projectId         The project ID
    * @param privateKeyId      The project's private key ID
    * @param privateKey        The project's private key
    * @param clientEmail       The client email
    * @param clientId          The client ID
    * @param clientX509CertUrl The URL to the client x509 Certificate URL
    * @return a FirebaseDatabase
    */
  def apply(baseUrl: String,
            projectId: String,
            privateKeyId: String,
            privateKey: String,
            clientEmail: String,
            clientId: String,
            clientX509CertUrl: String): FirebaseDatabase = {

    val firebaseConfiguration =
      Json.obj(
        "type" -> "service_account",
        "project_id" -> baseUrl,
        "private_key_id" -> privateKeyId,
        "private_key" -> privateKey,
        "client_email" -> clientEmail,
        "client_id" -> clientId,
        "auth_uri" -> "https://accounts.google.com/o/oauth2/auth",
        "token_uri" -> "https://accounts.google.com/o/oauth2/token",
        "auth_provider_x509_cert_url" -> "https://www.googleapis.com/oauth2/v1/certs",
        "client_x509_cert_url" -> clientX509CertUrl
      )
    val inputStream =
      new ByteArrayInputStream(firebaseConfiguration.toString.toArray.map(_.toByte))

    fromServiceAccountKey(inputStream, baseUrl)
  }

}