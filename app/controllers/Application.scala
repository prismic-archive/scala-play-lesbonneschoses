package controllers

import play.api._
import play.api.mvc._

import org.joda.time._

import scala.concurrent._
import play.api.libs.concurrent.Execution.Implicits._

import Play.current

import io.prismic._

object Application extends Controller {

  import Prismic.ctx

  // -- Links resolver

  def linkResolver(api: Api, ref: Option[String])(implicit request: RequestHeader) = DocumentLinkResolver(api) { 
    case (Fragment.DocumentLink(_, _, _, _, _), Some("about"))          => routes.Application.about(ref).absoluteURL()
    case (Fragment.DocumentLink(_, _, _, _, _), Some("jobs"))           => routes.Application.jobs(ref).absoluteURL()
    case (Fragment.DocumentLink(_, _, _, _, _), Some("stores"))         => routes.Application.stores(ref).absoluteURL()
    case (Fragment.DocumentLink(id, "store", _, slug, false), _)        => routes.Application.storeDetail(id, slug, ref).absoluteURL()
    case (Fragment.DocumentLink(id, "product", _, slug, false), _)      => routes.Application.productDetail(id, slug, ref).absoluteURL()
    case (Fragment.DocumentLink(id, "job-offer", _, slug, false), _)    => routes.Application.jobDetail(id, slug, ref).absoluteURL()
    case (Fragment.DocumentLink(id, "blog-post", _, slug, false), _)    => routes.Application.blogPost(id, slug, ref).absoluteURL()
    case anyOtherLink                                                   => routes.Application.brokenLink(ref).absoluteURL()
  }
  
  // -- Helpers

  def PageNotFound(implicit ctx: Prismic.Context) = NotFound(views.html.pageNotFound())

  def getBookmark(api: Api, ref: String, bookmark: String): Future[Option[Document]] = {
    api.bookmarks.get(bookmark).map { id =>
      getDocument(api, ref, id)
    }.getOrElse {
      Future.successful(None)
    }
  }

  def getDocument(api: Api, ref: String, id: String): Future[Option[Document]] = {
    for {
      documents <- api.forms("everything").query(s"""[[at(document.id, "$id")]]""").ref(ref).submit()
    } yield {
      documents.headOption
    }
  }

  def getDocuments(api: Api, ref: String, ids: String*): Future[Seq[Document]] = {
    ids match {
      case Nil => Future.successful(Nil)
      case ids => api.forms("everything").query(s"""[[any(document.id, ${ids.mkString("[\"","\",\"","\"]")})]]""").ref(ref).submit()
    }
  }

  def checkSlug(document: Option[Document], slug: String)(callback: Either[String,Document] => SimpleResult)(implicit r: Prismic.Request[_]) = {
    document.collect {
      case document if document.slug == slug => callback(Right(document))
      case document if document.slugs.contains(slug) => callback(Left(document.slug))
    }.getOrElse {
      PageNotFound
    }
  }

  // -- Page not found

  def brokenLink(ref: Option[String]) = Prismic.action(ref) { implicit request =>
    Future.successful(PageNotFound)
  }

  // -- Home page

  def index(ref: Option[String]) = Prismic.action(ref) { implicit request =>
    for {
      products <- ctx.api.forms("products").ref(ctx.ref).submit()
      featured <- ctx.api.forms("featured").ref(ctx.ref).submit()
    } yield {
      Ok(views.html.index(products, featured))
    }
  }

  // -- About us

  def about(ref: Option[String]) = Prismic.action(ref) { implicit request =>
    for {
      maybePage <- getBookmark(ctx.api, ctx.ref, "about")
    } yield {
      maybePage.map(page => Ok(views.html.about(page))).getOrElse(PageNotFound)
    }
  }

  // -- Jobs

  def jobs(ref: Option[String]) = Prismic.action(ref) { implicit request =>
    for {
      maybePage <- getBookmark(ctx.api, ctx.ref, "jobs")
      jobs <- ctx.api.forms("jobs").ref(ctx.ref).submit()
    } yield {
      maybePage.map(page => Ok(views.html.jobs(page, jobs))).getOrElse(PageNotFound)
    }
  }

  def jobDetail(id: String, slug: String, ref: Option[String]) = Prismic.action(ref) { implicit request =>
    for {
      maybePage <- getBookmark(ctx.api, ctx.ref, "jobs")
      maybeJob <- getDocument(ctx.api, ctx.ref, id)
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

  def stores(ref: Option[String]) = Prismic.action(ref) { implicit request =>
    for {
      maybePage <- getBookmark(ctx.api, ctx.ref, "stores")
      stores <- ctx.api.forms("stores").ref(ctx.ref).submit()
    } yield {
      maybePage.map(page => Ok(views.html.stores(page, stores))).getOrElse(PageNotFound)
    }
  }

  def storeDetail(id: String, slug: String, ref: Option[String]) = Prismic.action(ref) { implicit request =>
    for {
      maybeStore <- getDocument(ctx.api, ctx.ref, id)
    } yield {
      checkSlug(maybeStore, slug) {
        case Left(newSlug) => MovedPermanently(routes.Application.storeDetail(id, newSlug).url)
        case Right(store) => Ok(views.html.storeDetail(store))
      }
    }
  }

  // -- Selections

  def selectionDetail(id: String, slug: String, ref: Option[String]) = Prismic.action(ref) { implicit request =>
    for {
      maybeSelection <- getDocument(ctx.api, ctx.ref, id)
      products <- getDocuments(ctx.api, ctx.ref, maybeSelection.map(_.getAll("selection.product").collect {
        case Fragment.DocumentLink(id, "product", _, _, false) => id
      }).getOrElse(Nil):_*)
    } yield {
      checkSlug(maybeSelection, slug) {
        case Left(newSlug) => MovedPermanently(routes.Application.selectionDetail(id, newSlug).url)
        case Right(selection) => Ok(views.html.selectionDetail(selection, products))
      }
    }
  }

  // -- Blog

  val BlogCategories = List(
     "Announcements", 
     "Do it yourself", 
     "Behind the scenes"
  )

  def blog(maybeCategory: Option[String], ref: Option[String]) = Prismic.action(ref) { implicit request =>
    for {
      posts <- maybeCategory.map(
        category => ctx.api.forms("blog").query(s"""[[at(my.blog-post.category, "$category")]]""")
      ).getOrElse(ctx.api.forms("blog")).ref(ctx.ref).submit()
    } yield {
      Ok(views.html.posts(posts.sortBy(_.getDate("blog-post.date").map(_.value.getMillis)).reverse))
    }
  }

  def blogPost(id: String, slug: String, ref: Option[String]) = Prismic.action(ref) { implicit request =>
    for {
      maybePost <- getDocument(ctx.api, ctx.ref, id)
      relatedProducts <- getDocuments(ctx.api, ctx.ref, maybePost.map(_.getAll("blog-post.relatedproduct").collect {
        case Fragment.DocumentLink(id, "product", _, _, false) => id
      }).getOrElse(Nil):_*)
      relatedPosts <- getDocuments(ctx.api, ctx.ref, maybePost.map(_.getAll("blog-post.relatedpost").collect {
        case Fragment.DocumentLink(id, "blog-post", _, _, false) => id
      }).getOrElse(Nil):_*)
    } yield {
      checkSlug(maybePost, slug) {
        case Left(newSlug) => MovedPermanently(routes.Application.blogPost(id, newSlug).url)
        case Right(post) => Ok(views.html.postDetail(post, relatedProducts, relatedPosts))        
      }
    }
  }

  // -- Products

  val ProductCategories = collection.immutable.ListMap(
    "Macaron" -> "Macarons",
    "Cupcake" -> "Cup Cakes",
    "Pie" -> "Little Pies"
  )

  def products(ref: Option[String]) = Prismic.action(ref) { implicit request =>
    for {
      products <- ctx.api.forms("products").ref(ctx.ref).submit()
    } yield {
      Ok(views.html.products(products))
    }
  }

  def productDetail(id: String, slug: String, ref: Option[String]) = Prismic.action(ref) { implicit request =>
    for {
      maybeProduct <- getDocument(ctx.api, ctx.ref, id)
      relatedProducts <- getDocuments(ctx.api, ctx.ref, maybeProduct.map(_.getAll("product.related").collect {
        case Fragment.DocumentLink(id, "product", _, _, false) => id
      }).getOrElse(Nil):_*)
    } yield {
      checkSlug(maybeProduct, slug) {
        case Left(newSlug) => MovedPermanently(routes.Application.productDetail(id, newSlug).url)
        case Right(product) => Ok(views.html.productDetail(product, relatedProducts))
      }
    }
  }

  def productsByFlavour(flavour: String, ref: Option[String]) = Prismic.action(ref) { implicit request =>
    for {
      products <- ctx.api.forms("everything").query(s"""[[at(my.product.flavour, "$flavour")]]""").ref(ctx.ref).submit()
    } yield {
      if(products.isEmpty) {
        PageNotFound
      } else {
        Ok(views.html.productsByFlavour(flavour, products))
      }
    }
  }

  // -- Search

  def search(query: Option[String], ref: Option[String]) = Prismic.action(ref) { implicit request =>
    query.map(_.trim).filterNot(_.isEmpty).map { q =>
      for {
        products <- ctx.api.forms("everything").query(s"""[[any(document.type, ["product", "selection"])][fulltext(document, "$q")]]""").ref(ctx.ref).submit()
        others <- ctx.api.forms("everything").query(s"""[[any(document.type, ["article", "blog-post", "job-offer", "store"])][fulltext(document, "$q")]]""").ref(ctx.ref).submit()
      } yield {
        Ok(views.html.search(query, products, others))
      }
    }.getOrElse {
      Future.successful(Ok(views.html.search()))
    }
  }

}