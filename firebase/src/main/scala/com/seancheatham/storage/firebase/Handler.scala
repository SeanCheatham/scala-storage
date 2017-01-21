package com.seancheatham.storage.firebase

import com.google.firebase.database.DatabaseError
import play.api.libs.json.JsValue

/**
  * A Handler is effectively a function which is called whenever a particular event happens
  * at a path in Firebase.
  */
sealed abstract class Handler

/**
  * A special handler which is called in the event of a Database Error or connection disruption
  *
  * @param handler A function which handles a Firebase Database error
  */
case class Cancelled(handler: DatabaseError => _)

/**
  * A handler which gets called in the event of a change to a child at a path in Firebase
  */
sealed abstract class ChildHandler extends Handler

/**
  * A handler which is triggered when a child is added at a Firebase path
  *
  * @param handler A function which handles a tuple (Child ID, Child Value)
  */
case class ChildAddedHandler(handler: (String, JsValue) => _) extends ChildHandler

/**
  * A handler which is triggered when a child is removed at a Firebase path
  *
  * @param handler A function which handles the ID of the deleted child
  */
case class ChildRemovedHandler(handler: String => _) extends ChildHandler

/**
  * A handler which is triggered when a child is changed at a Firebase path
  *
  * @param handler A function which handles a tuple (Changed Child ID, New Child Value)
  */
case class ChildChangedHandler(handler: (String, JsValue) => _) extends ChildHandler

/**
  * A handler which gets called in the event of a change at a path in Firebase
  */
sealed abstract class ValueHandler extends Handler

/**
  * A handler which gets called in the event that the data changed at a path in Firebase
  *
  * @param handler A function which handles the new value
  */
case class ValueChangedHandler(handler: JsValue => _) extends ValueHandler

/**
  * A handler which gets called in the event that the data was removed at a path in Firebase
  *
  * @param handler A function which gets called when the value is nullified
  */
case class ValueRemovedHandler(handler: () => _) extends ValueHandler
