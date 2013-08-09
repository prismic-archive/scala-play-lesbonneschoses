import play.api._
import play.api.mvc._

import scala.concurrent._
import play.api.libs.concurrent.Execution.Implicits._

object Global extends GlobalSettings {

  override def onHandlerNotFound(request: RequestHeader) = {
    Results.NotFound("BOUH")
  }

}