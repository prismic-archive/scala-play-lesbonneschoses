package controllers

import play.api._
import play.api.mvc._
import play.api.libs.concurrent.Execution.Implicits._

import com.zenexity.wroom.client._

object Application extends Controller {

  def SESSION = Api.get("http://lesbonneschoses.wroom.io/api")

  val PageNotFound = NotFound("OOPS")

  val CATEGORIES = collection.immutable.ListMap(
    "Macaron" -> "Macarons",
    "Cupcake" -> "Cup Cakes",
    "Pie" -> "Little Pies"
  )

  def index = Action.async {
    for {
      session <- SESSION

      (productsRequest, featuredRequest) = (
        session.forms("products").ref(session.master).submit(),
        session.forms("everything").query("""[[:d document.tag  "Featured"]]""").ref(session.master).submit()
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
      products <- session.forms("products").ref(session.master).submit()
    } yield {
      Ok(views.html.products(products))
    }
  }

  def product(id: String, slug: String) = Action.async {
    for {
      session <- SESSION
      products <- session.forms("everything").query(s"""[[:d document.id "$id"]]""").ref(session.master).submit()
      maybeProduct = products.headOption
    } yield {
      maybeProduct.collect {
        case product if product.slug == slug => Ok(views.html.product(product))
        case product if product.slugs.contains(slug) => MovedPermanently(routes.Application.product(id, product.slug).url)
      }.getOrElse(PageNotFound)
    }
  }

}