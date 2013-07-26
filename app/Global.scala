import play.api._
import play.api.mvc._

object Global extends GlobalSettings {

  override def onHandlerNotFound(request: RequestHeader) = {
    controllers.Application.PageNotFound
  }

}