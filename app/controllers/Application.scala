package controllers

import play.api._
import play.api.mvc._
import play.api.libs.concurrent.Execution.Implicits._

import com.zenexity.wroom.client._

object Application extends Controller {

  val CACHE = BuiltInCache(200)

  def SESSION = Api.get("http://lesbonneschoses.wroom.io/api", cache = CACHE)

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
        session.forms("featured").ref(session.master).submit()
      )
      products <- productsRequest
      featured <- featuredRequest
    } yield {
      Ok(views.html.index(products))
    }
  }

  // -- 

  def about = Action.async {
    for {
      session <- SESSION
      pageId = session.bookmarks.get("about").getOrElse("")
      pages <- session.forms("everything").query(s"""[[:d document.id "$pageId"]]""").ref(session.master).submit()
      maybeAbout = pages.headOption
    } yield {
      maybeAbout.map { doc =>
        Ok(views.html.about(doc))
      }.getOrElse(PageNotFound)
    }
  }

  // --

  def jobs = TODO

  // -- 

  def stores = TODO

  // --

  def blog = TODO

  // --

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