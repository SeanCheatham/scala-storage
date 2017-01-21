package fixtures

import com.seancheatham.storage.DocumentStorage
import play.api.libs.json._

import scala.concurrent.{ExecutionContext, Future}

class MockDocumentStorage extends DocumentStorage[JsValue] {

  private var obj =
    Json.obj()

  def get(key: String*)(implicit ec: ExecutionContext): Future[JsValue] =
    keyToPath(key: _*).asSingleJson(obj).toOption match {
      case Some(value) =>
        Future.successful(value)
      case _ =>
        Future.failed(new NoSuchElementException)
    }

  def getCollection(key: String*)(implicit ec: ExecutionContext): Future[Iterator[JsValue]] =
    get(key: _*)
      .collect {
        case JsArray(items) =>
          items.iterator
      }
      .recover {
        case _: NoSuchElementException =>
          Iterator.empty
      }

  def write(key: String*)(value: JsValue)(implicit ec: ExecutionContext): Future[_] =
    Future.successful(???)

  def merge(key: String*)(value: JsValue)(implicit ec: ExecutionContext): Future[_] = ???

  def delete(key: String*)(implicit ec: ExecutionContext): Future[_] = ???

  def append(key: String*)(value: JsValue)(implicit ec: ExecutionContext): Future[String] = ???

  private def keyToPath(key: String*): JsPath =
    JsPath(key.toList.map(KeyPathNode.apply))

}
