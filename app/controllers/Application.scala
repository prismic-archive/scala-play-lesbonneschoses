package controllers

import play.api._
import play.api.mvc._
import play.api.libs.concurrent.Execution.Implicits._

import com.zenexity.wroom.client._

object Application extends Controller {

  def SESSION = Wroom.url("http://lesbonneschoses.wroom.io/api").api()

  val CATEGORIES = collection.immutable.ListMap(
    "Macaron" -> "Macarons",
    "Cupcake" -> "Cup Cakes",
    "Pie" -> "Little Pies"
  )

  def index = Action.async {
    for {
      session <- SESSION

      (productsRequest, featuredRequest) = (
        session.form("documents").q("""[[$d document.type ["product"]]]""").ref(session.master).submit(),
        session.form("documents").q("""[[$d document.tag  ["Featured"]]]""").ref(session.master).submit()
      )

      products <- productsRequest
      featured <- featuredRequest
    } yield {
      Ok(views.html.index(products))
    }
  }

  def products = Action.async {
    for {
      session <- SESSION
      products <- session.form("documents").q("""[[$d document.type ["product"]]]""").ref(session.master).submit()
    } yield {
      Ok(views.html.products(products))
    }
  }

  def product(id: String, slug: String) = Action {
    Ok(views.html.product())
  }

}