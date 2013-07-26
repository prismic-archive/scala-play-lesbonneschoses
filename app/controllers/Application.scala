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

  private val Cache = BuiltInCache(200)

  def apiHome = Api.get(Play.configuration.getString("prismic.api").getOrElse(sys.error("Missing configuration [prismic.api]")), cache = Cache)

  // -- Helpers

  def linkResolver(api: Api)(implicit request: RequestHeader) = DocumentLinkResolver(api) { 
    case (Fragment.DocumentLink(_, _, _, _, _), Some("about"))          => routes.Application.about().absoluteURL()
    case (Fragment.DocumentLink(_, _, _, _, _), Some("jobs"))           => routes.Application.jobs().absoluteURL()
    case (Fragment.DocumentLink(_, _, _, _, _), Some("stores"))         => routes.Application.stores().absoluteURL()
    case (Fragment.DocumentLink(id, "store", _, slug, false), _)        => routes.Application.storeDetail(id, slug).absoluteURL()
    case (Fragment.DocumentLink(id, "product", _, slug, false), _)      => routes.Application.productDetail(id, slug).absoluteURL()
    case (Fragment.DocumentLink(id, "job-offer", _, slug, false), _)    => routes.Application.jobDetail(id, slug).absoluteURL()
    case (Fragment.DocumentLink(id, "blog-post", _, slug, false), _)    => routes.Application.blogPost(id, slug).absoluteURL()
    case anyOtherLink                                                   => routes.Application.brokenLink().absoluteURL()
  }

  val PageNotFound = NotFound(views.html.pageNotFound())

  def brokenLink = Action(PageNotFound)

  def getBookmark(api: Api, ref: Ref, bookmark: String): Future[Option[Document]] = {
    api.bookmarks.get(bookmark).map { id =>
      getDocument(api, ref, id)
    }.getOrElse {
      Future.successful(None)
    }
  }

  def getDocument(api: Api, ref: Ref, id: String): Future[Option[Document]] = {
    for {
      documents <- api.forms("everything").query(s"""[[at(document.id, "$id")]]""").ref(ref).submit()
    } yield {
      documents.headOption
    }
  }

  def getDocuments(api: Api, ref: Ref, ids: String*): Future[Seq[Document]] = {
    ids match {
      case Nil => Future.successful(Nil)
      case ids => api.forms("everything").query(s"""[[any(document.id, ${ids.mkString("[\"","\",\"","\"]")})]]""").ref(ref).submit()
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
      api <- apiHome
      products <- api.forms("products").ref(api.master).submit()
      featured <- api.forms("featured").ref(api.master).submit()
    } yield {
      Ok(views.html.index(products, featured))
    }
  }

  // -- About us

  def about = Action.async { implicit request =>
    for {
      api <- apiHome
      maybePage <- getBookmark(api, api.master, "about")
    } yield {
      maybePage.map(page => Ok(views.html.about(page)(linkResolver(api)))).getOrElse(PageNotFound)
    }
  }

  // -- Jobs

  def jobs = Action.async { implicit request =>
    for {
      api <- apiHome
      maybePage <- getBookmark(api, api.master, "jobs")
      jobs <- api.forms("jobs").ref(api.master).submit()
    } yield {
      maybePage.map(page => Ok(views.html.jobs(page, jobs)(linkResolver(api)))).getOrElse(PageNotFound)
    }
  }

  def jobDetail(id: String, slug: String) = Action.async { implicit request =>
    for {
      api <- apiHome
      maybePage <- getBookmark(api, api.master, "jobs")
      maybeJob <- getDocument(api, api.master, id)
    } yield {
      checkSlug(maybeJob, slug) { 
        case Left(newSlug) => MovedPermanently(routes.Application.jobDetail(id, newSlug).url)
        case Right(job) => maybePage.map { page =>
          Ok(views.html.jobDetail(page, job)(linkResolver(api)))
        }.getOrElse(PageNotFound) 
      }
    }
  }

  // -- Stores

  def stores = Action.async { implicit request =>
    for {
      api <- apiHome
      maybePage <- getBookmark(api, api.master, "stores")
      stores <- api.forms("stores").ref(api.master).submit()
    } yield {
      maybePage.map(page => Ok(views.html.stores(page, stores)(linkResolver(api)))).getOrElse(PageNotFound)
    }
  }

  def storeDetail(id: String, slug: String) = Action.async { implicit request =>
    for {
      api <- apiHome
      maybeStore <- getDocument(api, api.master, id)
    } yield {
      checkSlug(maybeStore, slug) {
        case Left(newSlug) => MovedPermanently(routes.Application.storeDetail(id, newSlug).url)
        case Right(store) => Ok(views.html.storeDetail(store)(linkResolver(api)))
      }
    }
  }

  // -- Selections

  def selectionDetail(id: String, slug: String) = Action.async { implicit request =>
    for {
      api <- apiHome
      maybeSelection <- getDocument(api, api.master, id)
      products <- getDocuments(api, api.master, maybeSelection.map(_.getAll("selection.product").collect {
        case Fragment.DocumentLink(id, "product", _, _, false) => id
      }).getOrElse(Nil):_*)
    } yield {
      checkSlug(maybeSelection, slug) {
        case Left(newSlug) => MovedPermanently(routes.Application.selectionDetail(id, newSlug).url)
        case Right(selection) => Ok(views.html.selectionDetail(selection, products)(linkResolver(api)))
      }
    }
  }

  // -- Blog

  val BlogCategories = List(
     "Announcements", 
     "Do it yourself", 
     "Behind the scenes"
  )

  def blog(maybeCategory: Option[String]) = Action.async {
    for {
      api <- apiHome
      posts <- maybeCategory.map(
        category => api.forms("blog").query(s"""[[at(my.blog-post.category, "$category")]]""")
      ).getOrElse(api.forms("blog")).ref(api.master).submit()
    } yield {
      Ok(views.html.posts(posts.sortBy(_.getDate("blog-post.date").map(_.value.getMillis)).reverse))
    }
  }

  def blogPost(id: String, slug: String) = Action.async { implicit request =>
    for {
      api <- apiHome
      maybePost <- getDocument(api, api.master, id)
      relatedProducts <- getDocuments(api, api.master, maybePost.map(_.getAll("blog-post.relatedproduct").collect {
        case Fragment.DocumentLink(id, "product", _, _, false) => id
      }).getOrElse(Nil):_*)
      relatedPosts <- getDocuments(api, api.master, maybePost.map(_.getAll("blog-post.relatedpost").collect {
        case Fragment.DocumentLink(id, "blog-post", _, _, false) => id
      }).getOrElse(Nil):_*)
    } yield {
      checkSlug(maybePost, slug) {
        case Left(newSlug) => MovedPermanently(routes.Application.blogPost(id, newSlug).url)
        case Right(post) => Ok(views.html.postDetail(post, relatedProducts, relatedPosts)(linkResolver(api)))        
      }
    }
  }

  // -- Products

  val ProductCategories = collection.immutable.ListMap(
    "Macaron" -> "Macarons",
    "Cupcake" -> "Cup Cakes",
    "Pie" -> "Little Pies"
  )

  def products = Action.async {
    for {
      api <- apiHome
      products <- api.forms("products").ref(api.master).submit()
    } yield {
      Ok(views.html.products(products))
    }
  }

  def productDetail(id: String, slug: String) = Action.async { implicit request =>
    for {
      api <- apiHome
      maybeProduct <- getDocument(api, api.master, id)
      relatedProducts <- getDocuments(api, api.master, maybeProduct.map(_.getAll("product.related").collect {
        case Fragment.DocumentLink(id, "product", _, _, false) => id
      }).getOrElse(Nil):_*)
    } yield {
      checkSlug(maybeProduct, slug) {
        case Left(newSlug) => MovedPermanently(routes.Application.productDetail(id, newSlug).url)
        case Right(product) => Ok(views.html.productDetail(product, relatedProducts)(linkResolver(api)))
      }
    }
  }

  def productsByFlavour(flavour: String) = Action.async {
    for {
      api <- apiHome
      products <- api.forms("everything").query(s"""[[at(my.product.flavour, "$flavour")]]""").ref(api.master).submit()
    } yield {
      if(products.isEmpty) {
        PageNotFound
      } else {
        Ok(views.html.productsByFlavour(flavour, products))
      }
    }
  }

  // -- Search

  def search(query: Option[String]) = Action.async { implicit request =>
    query.map(_.trim).filterNot(_.isEmpty).map { q =>
      for {
        api <- apiHome
        products <- api.forms("everything").query(s"""[[any(document.type, ["product", "selection"])][fulltext(document, "$q")]]""").ref(api.master).submit()
        others <- api.forms("everything").query(s"""[[any(document.type, ["article", "blog-post", "job-offer", "store"])][fulltext(document, "$q")]]""").ref(api.master).submit()
      } yield {
        Ok(views.html.search(query, products, others)(linkResolver(api)))
      }
    }.getOrElse {
      for {
        api <- apiHome
      } yield {
         Ok(views.html.search()(linkResolver(api)))
      }
    }
  }

}