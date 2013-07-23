package controllers

import play.api._
import play.api.mvc._
import play.api.libs.concurrent.Execution.Implicits._

import Play.current

import io.prismic._

object Application extends Controller {

  // -- API

  val CACHE = BuiltInCache(200)

  def SESSION = Api.get(Play.configuration.getString("prismic.api").getOrElse(sys.error("Missing configuration [prismic.api]")), cache = CACHE)

  val CATEGORIES = collection.immutable.ListMap(
    "Macaron" -> "Macarons",
    "Cupcake" -> "Cup Cakes",
    "Pie" -> "Little Pies"
  )

  val LINK_RESOLVER: LinkResolver = { 
    case Fragment.DocumentLink(_, _, _, _, Some("about"), _) => LinkDestination(url = routes.Application.about().url)
    case Fragment.DocumentLink(_, _, _, _, Some("jobs"), _) => LinkDestination(url = routes.Application.jobs().url)
    case Fragment.DocumentLink(_, _, _, _, Some("stores"), _) => LinkDestination(url = routes.Application.stores().url)
    case Fragment.DocumentLink(id, "store", _, slug, _, false) => LinkDestination(url = routes.Application.storeDetail(id, slug).url)
    case Fragment.DocumentLink(id, "product", _, slug, _, false) => LinkDestination(url = routes.Application.productDetail(id, slug).url)
    case Fragment.DocumentLink(id, "job-offer", _, slug, _, false) => LinkDestination(url = routes.Application.jobDetail(id, slug).url)
    case _ => LinkDestination(url = routes.Application.brokenLink().url)
  }

  // -- Helpers

  val PageNotFound = NotFound("OOPS")

  def brokenLink = Action {
    PageNotFound
  }

  // -- Home page

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
      Ok(views.html.index(products, featured))
    }
  }

  // -- 

  def about = Action.async {
    for {
      session <- SESSION
      pageId = session.bookmarks.get("about").getOrElse("")
      pages <- session.forms("everything").query(s"""[[at(document.id, "$pageId")]]""").ref(session.master).submit()
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
      pages <- session.forms("everything").query(s"""[[at(document.id, "$pageId")]]""").ref(session.master).submit()
      jobs <- session.forms("jobs").ref(session.master).submit()
      maybePage = pages.headOption
    } yield {
      maybePage.map(page => Ok(views.html.jobs(page, jobs))).getOrElse(PageNotFound)
    }
  }

  def jobDetail(id: String, slug: String) = Action.async {
    for {
      session <- SESSION
      pageId = session.bookmarks.get("jobs").getOrElse("")
      pages <- session.forms("everything").query(s"""[[at(document.id, "$pageId")]]""").ref(session.master).submit()
      jobs <- session.forms("everything").query(s"""[[at(document.id, "$id")][at(document.type, "job-offer")]]""").ref(session.master).submit()
      maybeJob = jobs.headOption
      maybePage = pages.headOption
    } yield {
      maybePage.flatMap { page =>
        maybeJob.collect {
          case job if job.slug == slug => Ok(views.html.jobDetail(page, job))
          case job if job.slugs.contains(slug) => MovedPermanently(routes.Application.jobDetail(id, job.slug).url)
        }
      }.getOrElse(PageNotFound)
    }
  }

  // -- 

  def stores = Action.async {
    for {
      session <- SESSION
      pageId = session.bookmarks.get("stores").getOrElse("")
      pages <- session.forms("everything").query(s"""[[at(document.id, "$pageId")]]""").ref(session.master).submit()
      stores <- session.forms("stores").ref(session.master).submit()
      maybePage = pages.headOption
    } yield {
      maybePage.map(page => Ok(views.html.stores(page, stores))).getOrElse(PageNotFound)
    }
  }

  def storeDetail(id: String, slug: String) = Action.async {
    for {
      session <- SESSION
      stores <- session.forms("everything").query(s"""[[at(document.id, "$id")][at(document.type, "store")]]""").ref(session.master).submit()
      maybeStore = stores.headOption
    } yield {
      maybeStore.collect {
        case store if store.slug == slug => Ok(views.html.storeDetail(store))
        case store if store.slugs.contains(slug) => MovedPermanently(routes.Application.storeDetail(id, store.slug).url)
      }.getOrElse(PageNotFound)
    }
  }

  // --

  def selectionDetail(id: String, slug: String) = Action.async {
    for {
      session <- SESSION
      selections <- session.forms("everything").query(s"""[[at(document.id, "$id")][at(document.type, "selection")]]""").ref(session.master).submit()
      maybeSelection = selections.headOption
    } yield {
      maybeSelection.collect {
        case selection if selection.slug == slug => Ok(views.html.selectionDetail(selection))
        case selection if selection.slugs.contains(slug) => MovedPermanently(routes.Application.selectionDetail(id, selection.slug).url)
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
      products <- session.forms("everything").query(s"""[[at(document.id, "$id")][at(document.type, "product")]]""").ref(session.master).submit()
      maybeProduct = products.headOption
    } yield {
      maybeProduct.collect {
        case product if product.slug == slug => Ok(views.html.productDetail(product))
        case product if product.slugs.contains(slug) => MovedPermanently(routes.Application.productDetail(id, product.slug).url)
      }.getOrElse(PageNotFound)
    }
  }

}