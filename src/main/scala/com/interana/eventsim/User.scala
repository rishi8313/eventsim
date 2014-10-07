package com.interana.eventsim

import java.io.Serializable

import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat

import scala.util.parsing.json.JSONObject

class User(val alpha: Double, // alpha = expected request inter-arrival time
           val beta: Double,  // beta  = expected session inter-arrival time
           val startTime: DateTime,
           val initialSessionStates: scala.collection.Map[(String,String),WeightedRandomThingGenerator[State]],
           val auth: String,
           val props: scala.collection.immutable.Map[String,Any],
           var device: scala.collection.immutable.Map[String,Any]
          ) extends Serializable with Ordered[User] {

  val userId = Counters.nextUserId
  var session = new Session(
    Some(Session.pickFirstTimeStamp(startTime, alpha, beta)),
      alpha, beta, initialSessionStates, auth, props.getOrElse("level","").asInstanceOf[String])

  override def compare(that: User): Int =
    (that.session.nextEventTimeStamp, this.session.nextEventTimeStamp) match {
      case (None, None) => 0
      case (_: Some[DateTime], None) => -1
      case (None, _: Some[DateTime]) => 1
      case (thatValue: Some[DateTime], thisValue: Some[DateTime]) =>
        thatValue.get.compareTo(thisValue.get)
    }

  def nextEvent(): Unit = nextEvent(0.0)

  def nextEvent(prAttrition: Double) = {
    session.incrementEvent()
    if (session.done) {
      if (TimeUtilities.rng.nextDouble() < prAttrition ||
          session.currentState.auth == SiteConfig.churnedState.getOrElse("")) {
        session.nextEventTimeStamp = None
        // TODO: mark as churned
      }
      else
        session = session.nextSession
    }
  }

  private val EMPTY_MAP = Map()

  def eventString = {
    val showUserDetails = SiteConfig.showUserWithState(session.currentState.auth)
    val m = device.+(
      "ts" -> session.nextEventTimeStamp.get.getMillis,
      "userId" -> (if (showUserDetails) userId else ""),
      "sessionId" -> session.sessionId,
      "page" -> session.currentState.page,
      "auth" -> session.currentState.auth,
      "method" -> session.currentState.method,
      "status" -> session.currentState.status,
      "itemInSession" -> session.itemInSession
    ).++(if (showUserDetails) props else EMPTY_MAP)

    val j = new JSONObject(m)
    j.toString()
  }

  def tsToString(ts: DateTime): String = {
      ts.toString(ISODateTimeFormat.dateTime())
  }

  def nextEventTimeStampString = tsToString(this.session.nextEventTimeStamp.get)

  def mkString = props.+(
    "alpha" -> alpha,
    "beta" -> beta,
    "startTime" -> tsToString(startTime),
    "initialSessionStates" -> initialSessionStates,
    "nextEventTimeStamp" -> tsToString(session.nextEventTimeStamp.get) ,
    "sessionId" -> session.sessionId ,
    "userId" -> userId ,
    "currentState" -> session.currentState)

}