import play.api._
import play.api.mvc._

import scala.concurrent._
import scala.concurrent.duration._
import play.api.libs.concurrent.Execution.Implicits._

import controllers._

object Global extends GlobalSettings {

  override def onHandlerNotFound(request: RequestHeader) = {
    Prismic.buildContext(request.queryString.get("ref").flatMap(_.headOption))(request).map { api =>
      Application.PageNotFound(api)
    }
  }

}