package actors

import akka.actor.{Actor, ActorRef, Props}
import play.api.libs.iteratee.{Concurrent, Enumeratee, Enumerator, Iteratee}
import play.api.libs.json.JsObject
import play.api.libs.oauth.{ConsumerKey, OAuthCalculator, RequestToken}
import play.api.libs.ws.WS
import play.api.{Logger, Play}
import play.extras.iteratees.{Encoding, JsonIteratees}
import play.api.Play.current
import scala.concurrent.ExecutionContext.Implicits.global


class TwitterStreamer(out: ActorRef) extends Actor {
  def receive = {
    case "subscribe" =>
      Logger.info("Receive subscription from a client")
      TwitterStreamer.subscribe(out)
  }
}

object TwitterStreamer {
  private var broadcastEnumerator: Option[Enumerator[JsObject]] = None

  def props(out: ActorRef) = Props(new TwitterStreamer(out))

  def connect(): Unit = {
    credentials.map { case (consumerKey, requestToken) =>

      val (iteratee, enumerator) = Concurrent.joined[Array[Byte]]

      val jsonStream: Enumerator[JsObject] =
        enumerator &>
          Encoding.decode() &>
          Enumeratee.grouped(JsonIteratees.jsSimpleObject)

      val (be, _) = Concurrent.broadcast(jsonStream)
      broadcastEnumerator = Some(be)

      val url = "https://stream.twitter.com/1.1/statuses/filter.json"

      WS
        .url(url)
        .withRequestTimeout(-1)
        .sign(OAuthCalculator(consumerKey, requestToken))
        .withQueryString("locations" -> "139.163818359375,36.949891786813296,140.8612060546875,38.017803980061146")
        .get { response =>
        Logger.info("Status: " + response.status)
        iteratee
      }.map { _ =>
        Logger.info("Twitter stream closed")
      }
    } getOrElse {
      Logger.error("Twitter credential missing")
    }
  }

  def subscribe(out: ActorRef): Unit = {
    if (broadcastEnumerator == None) {
      connect()
    }
    val twitterClient = Iteratee.foreach[JsObject] { t => out ! t}
    broadcastEnumerator.map { enumerator =>
      enumerator run twitterClient
    }
  }

  def credentials: Option[(ConsumerKey, RequestToken)] = for {
    apiKey <- Play.configuration.getString("twitter.apiKey")
    apiSecret <- Play.configuration.getString("twitter.apiSecret")
    token <- Play.configuration.getString("twitter.token")
    tokenSecret <- Play.configuration.getString("twitter.tokenSecret")
  } yield {
    (ConsumerKey(apiKey, apiSecret), RequestToken(token, tokenSecret))
  }

}

