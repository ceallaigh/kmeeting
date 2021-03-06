package controllers

import org.joda.time.DateTimeZone
import play.api._
import play.api.mvc._
import play.api.cache.Cached
import play.api.Play.current
import io.michaelallen.mustache.PlayImplicits
import scala.concurrent.Future
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import rooms._
import events._
import config.Config

object RoomsListPresenter extends Controller with PlayImplicits {

  case class RoomListItem(
    room: BaseRoomDetail,
    url: String,
    status: RoomStatus
  )

  object RoomListItem {
    def apply(room: BaseRoomDetail, status: RoomStatus): RoomListItem = {
      RoomListItem(
        room = room,
        status = status,
        url = routes.RoomDashboardPresenter.roomDashboard(room.email).url
      )
    }
  }

  case class RoomsList(
    title: String,
    rooms: Seq[RoomListItem],
    fewRooms: Boolean
  ) extends mustache.roomsList

  object RoomsList {
    def apply(title: String, rooms: Seq[Room]): Future[RoomsList] = {
      val futureRoomList = Future.sequence(
        rooms.par.map { room =>
          val timezone = Rooms.timezone(room)
          val details = Rooms.roomDetails(room)
          val events = Events.todaysEvents(room)
          val eventsWithTimezone = events map {
            _ map { event =>
              EventBriefInTimeZone(event, timezone)
            }
          }

          val status = RoomStatus(eventsWithTimezone)
          for {
            roomStatus <- status
            roomDetails <- details
          } yield {
            RoomListItem(roomDetails, roomStatus)
          }
        }.seq
      )
      futureRoomList.map { list =>
        RoomsList(title, list, list.size < 5)
      }
    }
  }

  def roomsList(office: String) = Action.async {
    (office, Config.roomGroups.get(office)) match {
      case ("all", _) => {
        val roomsList = RoomsList("All Meeting Rooms", Rooms.allRooms())
        roomsList.map { rooms =>
          Ok(rooms)
        }
      }
      case (_, Some(id)) => {
        val roomsList = RoomsList(s"${office.capitalize} Meeting Rooms", Rooms.rooms(id))
        roomsList.map { rooms =>
          Ok(rooms)
        }
      }
      case _ => Future { Redirect(routes.HomePresenter.index()) }
    }
  }
}
