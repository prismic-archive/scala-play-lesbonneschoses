package controllers

import play.api._
import play.api.mvc._

import org.joda.time._

import scala.concurrent._
import play.api.libs.concurrent.Execution.Implicits._

import Play.current

import io.prismic._

object Application extends Controller {

  // -- API

  val CACHE = BuiltInCache(200)

  def SESSION = Api.get(Play.configuration.getString("prismic.api").getOrElse(sys.error("Missing configuration [prismic.api]")), cache = CACHE)

  val LINK_RESOLVER: LinkResolver = { 
    case Fragment.DocumentLink(_, _, _, _, Some("about"), _)        => LinkDestination(url = routes.Application.about().url)
    case Fragment.DocumentLink(_, _, _, _, Some("jobs"), _)         => LinkDestination(url = routes.Application.jobs().url)
    case Fragment.DocumentLink(_, _, _, _, Some("stores"), _)       => LinkDestination(url = routes.Application.stores().url)
    case Fragment.DocumentLink(id, "store", _, slug, _, false)      => LinkDestination(url = routes.Application.storeDetail(id, slug).url)
    case Fragment.DocumentLink(id, "product", _, slug, _, false)    => LinkDestination(url = routes.Application.productDetail(id, slug).url)
    case Fragment.DocumentLink(id, "job-offer", _, slug, _, false)  => LinkDestination(url = routes.Application.jobDetail(id, slug).url)
    case anyOtherLink                                               => LinkDestination(url = routes.Application.brokenLink().url)
  }

  // -- Helpers

  val PageNotFound = NotFound("OOPS")

  def brokenLink = Action {
    PageNotFound
  }

  def getBookmark(session: Api, ref: Ref, bookmark: String): Future[Option[Document]] = {
    session.bookmarks.get(bookmark).map { id =>
      getDocument(session, ref, id)
    }.getOrElse {
      Future.successful(None)
    }
  }

  def getDocument(session: Api, ref: Ref, id: String): Future[Option[Document]] = {
    for {
      documents <- session.forms("everything").query(s"""[[at(document.id, "$id")]]""").ref(ref).submit()
    } yield {
      documents.headOption
    }
  }

  def getDocuments(session: Api, ref: Ref, ids: String*): Future[Seq[Document]] = {
    ids match {
      case Nil => Future.successful(Nil)
      case ids => session.forms("everything").query(s"""[[any(document.id, ${ids.mkString("[\"","\",\"","\"]")})]]""").ref(ref).submit()
    }
  }

  def checkSlug(document: Option[Document], slug: String)(callback: Either[String,Document] => SimpleResult) = {
    document.collect {
      case document if document.slug == slug => callback(Right(document))
      case document if document.slugs.contains(slug) => callback(Left(document.slug))
    }.getOrElse {
      PageNotFound
    }
  }

  // -- Home page

  def index = Action.async {
    for {
      session <- SESSION
      products <- session.forms("products").ref(session.master).submit()
      featured <- session.forms("featured").ref(session.master).submit()
    } yield {
      Ok(views.html.index(products, featured))
    }
  }

  // -- About us

  def about = Action.async {
    for {
      session <- SESSION
      maybePage <- getBookmark(session, session.master, "about")
    } yield {
      maybePage.map(page => Ok(views.html.about(page))).getOrElse(PageNotFound)
    }
  }

  // -- Jobs

  def jobs = Action.async {
    for {
      session <- SESSION
      maybePage <- getBookmark(session, session.master, "jobs")
      jobs <- session.forms("jobs").ref(session.master).submit()
    } yield {
      maybePage.map(page => Ok(views.html.jobs(page, jobs))).getOrElse(PageNotFound)
    }
  }

  def jobDetail(id: String, slug: String) = Action.async {
    for {
      session <- SESSION
      maybePage <- getBookmark(session, session.master, "jobs")
      maybeJob <- getDocument(session, session.master, id)
    } yield {
      checkSlug(maybeJob, slug) { 
        case Left(newSlug) => MovedPermanently(routes.Application.jobDetail(id, newSlug).url)
        case Right(job) => maybePage.map { page =>
          Ok(views.html.jobDetail(page, job))
        }.getOrElse(PageNotFound) 
      }
    }
  }

  // -- Stores

  def stores = Action.async {
    for {
      session <- SESSION
      maybePage <- getBookmark(session, session.master, "stores")
      stores <- session.forms("stores").ref(session.master).submit()
    } yield {
      maybePage.map(page => Ok(views.html.stores(page, stores))).getOrElse(PageNotFound)
    }
  }

  def storeDetail(id: String, slug: String) = Action.async {
    for {
      session <- SESSION
      maybeStore <- getDocument(session, session.master, id)
    } yield {
      checkSlug(maybeStore, slug) {
        case Left(newSlug) => MovedPermanently(routes.Application.storeDetail(id, newSlug).url)
        case Right(store) => Ok(views.html.storeDetail(store))
      }
    }
  }

  // -- Selections

  def selectionDetail(id: String, slug: String) = Action.async {
    for {
      session <- SESSION
      maybeSelection <- getDocument(session, session.master, id)
      products <- getDocuments(session, session.master, maybeSelection.map(_.getAll("selection.product").collect {
        case Fragment.DocumentLink(id, "product", _, _, _, false) => id
      }).getOrElse(Nil):_*)
    } yield {
      checkSlug(maybeSelection, slug) {
        case Left(newSlug) => MovedPermanently(routes.Application.selectionDetail(id, newSlug).url)
        case Right(selection) => Ok(views.html.selectionDetail(selection, products))
      }
    }
  }

  // -- Blog

  val blogCategories = List(
     "Announcements", 
     "Do it yourself", 
     "Behind the scenes"
  )

  def blog(maybeCategory: Option[String]) = Action.async {
    for {
      session <- SESSION
      posts <- maybeCategory.map(
        category => session.forms("blog").query(s"""[[at(my.blog-post.category, "$category")]]""")
      ).getOrElse(session.forms("blog")).ref(session.master).submit()
    } yield {
      Ok(views.html.posts(posts.sortBy(_.getDate("blog-post.date").map(_.value.getMillis)).reverse))
    }
  }

  // -- Products

  val productCategories = collection.immutable.ListMap(
    "Macaron" -> "Macarons",
    "Cupcake" -> "Cup Cakes",
    "Pie" -> "Little Pies"
  )

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
      maybeProduct <- getDocument(session, session.master, id)
      relatedProducts <- getDocuments(session, session.master, maybeProduct.map(_.getAll("product.related").collect {
        case Fragment.DocumentLink(id, "product", _, _, _, false) => id
      }).getOrElse(Nil):_*)
    } yield {
      checkSlug(maybeProduct, slug) {
        case Left(newSlug) => MovedPermanently(routes.Application.productDetail(id, newSlug).url)
        case Right(product) => Ok(views.html.productDetail(product, relatedProducts))
      }
    }
  }

  def productsByFlavour(flavour: String) = Action.async {
    for {
      session <- SESSION
      products <- session.forms("everything").query(s"""[[at(my.product.flavour, "$flavour")]]""").ref(session.master).submit()
    } yield {
      if(products.isEmpty) {
        PageNotFound
      } else {
        Ok(views.html.productsByFlavour(flavour, products))
      }
    }
  }

}