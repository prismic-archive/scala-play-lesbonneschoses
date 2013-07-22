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
      maybePage = pages.headOption
    } yield {
      maybePage.map(page => Ok(views.html.about(page))).getOrElse(PageNotFound)
    }
  }

  // --

  def jobs = Action.async {
    for {
      session <- SESSION
      pageId = session.bookmarks.get("jobs").getOrElse("")
      pages <- session.forms("everything").query(s"""[[:d document.id "$pageId"]]""").ref(session.master).submit()
      jobs <- session.forms("jobs").ref(session.master).submit()
      maybePage = pages.headOption
    } yield {
      maybePage.map(page => Ok(views.html.jobs(page, jobs))).getOrElse(PageNotFound)
    }
  }

  // -- 

  def stores = Action.async {
    for {
      session <- SESSION
      pageId = session.bookmarks.get("stores").getOrElse("")
      pages <- session.forms("everything").query(s"""[[:d document.id "$pageId"]]""").ref(session.master).submit()
      stores <- session.forms("stores").ref(session.master).submit()
      maybePage = pages.headOption
    } yield {
      maybePage.map(page => Ok(views.html.stores(page, stores))).getOrElse(PageNotFound)
    }
  }

  def storeDetail(id: String, slug: String) = Action.async {
    for {
      session <- SESSION
      stores <- session.forms("everything").query(s"""[[:d document.id "$id"][:d document.type "store"]]""").ref(session.master).submit()
      maybeStore = stores.headOption
    } yield {
      maybeStore.collect {
        case store if store.slug == slug => Ok(views.html.storeDetail(store))
        case store if store.slugs.contains(slug) => MovedPermanently(routes.Application.storeDetail(id, store.slug).url)
      }.getOrElse(PageNotFound)
    }
  }

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

  def productDetail(id: String, slug: String) = Action.async {
    for {
      session <- SESSION
      products <- session.forms("everything").query(s"""[[:d document.id "$id"][:d document.type "product"]]""").ref(session.master).submit()
      maybeProduct = products.headOption
    } yield {
      maybeProduct.collect {
        case product if product.slug == slug => Ok(views.html.productDetail(product))
        case product if product.slugs.contains(slug) => MovedPermanently(routes.Application.productDetail(id, product.slug).url)
      }.getOrElse(PageNotFound)
    }
  }

}