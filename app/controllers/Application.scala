package controllers

import play.api._
import play.api.mvc._
import play.api.libs.concurrent.Execution.Implicits._

import com.zenexity.wroom.client._

object Application extends Controller {

  def SESSION = Wroom.url("http://lesbonneschoses.wroom.io/api").api()

  def index = Action.async {
    for {
      session  <- SESSION
      products <- session.form("documents").q("""[[$d document.type ["product"]]]""").ref(session.master).submit()
      featured <- session.form("documents").q("""[[$d document.tag ["Featured"]]]""").ref(session.master).submit()
    } yield {

      println(products.headOption)

      println(s"${products.size} products")
      println(s"${featured.size} featured")

      Ok(views.html.index(products))
    }
  }

  def products = Action {
    Ok(views.html.products())
  }

  def product(id: String, slug: String) = Action {
    Ok(views.html.product())
  }

}