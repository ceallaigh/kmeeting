package events

import org.joda.time.format.{ISODateTimeFormat, PeriodPrinter, PeriodFormatterBuilder}
import com.github.nscala_time.time.Imports._
import play.FormDelegate
import org.joda.time.{Minutes, ReadablePeriod}
import java.util.Locale
import java.io.Writer

import play.api.libs.json.JsArray
import play.api.libs.ws.WSResponse

import scala.util.{Failure, Success, Try}

object PeriodFormat {
  val roughHoursMins = {
    new PeriodFormatterBuilder()
      .appendHours()
      .appendSuffix(" hr", " hrs")
      .appendSeparator("", " ")
      .appendMinutes()
      .appendSuffix(" min", " mins")
      .toFormatter
  }
}

case class EventBriefInTimeZone(
    event: EventBrief,
    timezone: DateTimeZone
) {
  def endTimeString = {
    event.endTimeString(timezone)
  }

  def startTimeString = {
    event.startTimeString(timezone)
  }

  def subject = event.subject
  def start = event.start
  def end = event.end
  def importance = event.importance
  def isAllDay = event.isAllDay
  def isCancelled = event.isCancelled
  def endIsoString = event.endIsoString
  def startIsoString = event.startIsoString
  def durationString = event.durationString
  def isToday = event.isToday
  def isPassed = event.isPassed
}

case class EventBrief (
    subject: String,
    start: DateTime,
    end: DateTime,
    importance: String,
    isAllDay: Boolean,
    isCancelled: Boolean
) {
  def endTimeString(timezone: DateTimeZone) = {
    if (end.toLocalDate.equals(LocalDate.today)) {
      end.toString(DateTimeFormat.shortTime().withZone(timezone))
    } else if (end.toLocalDate.equals(LocalDate.tomorrow)) {
      end.toString(DateTimeFormat.shortTime().withZone(timezone)) + " tomorrow"
    } else {
      end.toString(DateTimeFormat.forPattern("h:m a EEEEEEEEE").withZone(timezone))
    }
  }
  def endIsoString = {
    end.toString(ISODateTimeFormat.dateTimeNoMillis())
  }
  def startTimeString(timezone: DateTimeZone) = {
    start.toString(DateTimeFormat.shortTime().withZone(timezone))
  }
  def startIsoString = {
    start.toString(ISODateTimeFormat.dateTimeNoMillis())
  }
  def durationString = {
    val duration = (start to end).toPeriod
    if (duration.toStandardMinutes.isLessThan(5.minutes.toPeriod.toStandardMinutes)) {
      "a few minutes"
    } else {
      PeriodFormat.roughHoursMins.print(duration)
    }
  }

  def isToday = {
    start.toLocalDate.equals(LocalDate.today)
  }

  def isPassed = {
    end isBefore DateTime.now
  }
}

object EventBrief extends FormDelegate[EventBrief] {
  import playMappings._

  val dateTime: Mapping[DateTime] = {
    text.transform(_.toDateTime, _.toString)
  }

  val form = Form(
    mapping(
      "Subject"     -> text,
      "Start"       -> dateTime,
      "End"         -> dateTime,
      "Importance"  -> text,
      "IsAllDay"    -> boolean,
      "IsCancelled" -> boolean
    ) (EventBrief.apply) (EventBrief.unapply)
  )

  def bind(response: WSResponse): Seq[EventBrief] = {
    Try {
      val js = (response.json \ "value").as[JsArray]
      js.value map { EventBrief.bind(_).get }
    } match {
      case Success(js) => js
      case Failure(throwable) => Seq.empty[EventBrief]
    }
  }
}
