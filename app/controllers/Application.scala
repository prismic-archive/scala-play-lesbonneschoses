package controllers

import play.api._
import play.api.mvc._

import org.joda.time._

import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent._

import Play.current

import io.prismic._

/**
 * Main controller for the Website.
 *
 * It uses some helpers provided by `controllers.Prismic`
 */
object Application extends Controller {

  import Prismic._

  // -- Resolve links to documents
  def linkResolver(api: Api)(implicit request: RequestHeader) = DocumentLinkResolver(api) {

    // For "Bookmarked" documents that use a special page
    case (_, Some("about")) => routes.Application.about.absoluteURL()
    case (_, Some("jobs")) => routes.Application.jobs.absoluteURL()
    case (_, Some("stores")) => routes.Application.stores.absoluteURL()

    // Store documents
    case (link:Fragment.DocumentLink, _) if link.typ == "store" && !link.isBroken =>
      routes.Application.storeDetail(link.id, link.slug).absoluteURL()

    // Any product
    case (link:Fragment.DocumentLink, _) if link.typ == "product" && !link.isBroken =>
      routes.Application.productDetail(link.id, link.slug).absoluteURL()

    // Product selection
    case (link:Fragment.DocumentLink, _) if link.typ == "selection" && !link.isBroken =>
      routes.Application.selectionDetail(link.id, link.slug).absoluteURL()

    // Job offers
    case (link:Fragment.DocumentLink, _) if link.typ == "job-offer" && !link.isBroken =>
      routes.Application.jobDetail(link.id, link.slug).absoluteURL()

    // Blog
    case (link:Fragment.DocumentLink, _) if link.typ == "blog-post" && !link.isBroken =>
      routes.Application.blogPost(link.id, link.slug).absoluteURL()

    case anyOtherLink => routes.Application.brokenLink.absoluteURL()
  }

  // -- Page not found

  def PageNotFound(implicit ctx: Prismic.Context) = NotFound(views.html.pageNotFound())

  def brokenLink = Prismic.action { implicit request =>
    Future.successful(PageNotFound)
  }

  // -- Home page

  def index = Prismic.action { implicit request =>
    for {
      products <- ctx.api.forms("products").ref(ctx.ref).submit()
      featured <- ctx.api.forms("featured").ref(ctx.ref).submit()
    } yield {
      Ok(views.html.index(products.results, featured.results))
    }
  }

  // -- About us

  def about = Prismic.action { implicit request =>
    for {
      maybePage <- getBookmark("about")
    } yield {
      maybePage.map(page => Ok(views.html.about(page))).getOrElse(PageNotFound)
    }
  }

  // -- Jobs

  def jobs = Prismic.action { implicit request =>
    for {
      maybePage <- getBookmark("jobs")
      jobs <- ctx.api.forms("jobs").ref(ctx.ref).submit()
    } yield {
      maybePage.map(page => Ok(views.html.jobs(page, jobs.results))).getOrElse(PageNotFound)
    }
  }

  def jobDetail(id: String, slug: String) = Prismic.action { implicit request =>
    for {
      maybePage <- getBookmark("jobs")
      maybeJob <- getDocument(id)
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

  def stores = Prismic.action { implicit request =>
    for {
      maybePage <- getBookmark("stores")
      stores <- ctx.api.forms("stores").ref(ctx.ref).submit()
    } yield {
      maybePage.map(page => Ok(views.html.stores(page, stores.results))).getOrElse(PageNotFound)
    }
  }

  def storeDetail(id: String, slug: String) = Prismic.action { implicit request =>
    for {
      maybeStore <- getDocument(id)
    } yield {
      checkSlug(maybeStore, slug) {
        case Left(newSlug) => MovedPermanently(routes.Application.storeDetail(id, newSlug).url)
        case Right(store)  => Ok(views.html.storeDetail(store))
      }
    }
  }

  // -- Products Selections

  def selectionDetail(id: String, slug: String) = Prismic.action { implicit request =>
    for {
      maybeSelection <- getDocument(id)
      products <- getDocuments(maybeSelection.map(_.getAll("selection.product").collect {
        case link:Fragment.DocumentLink if link.typ == "product" && !link.isBroken => link.id
      }).getOrElse(Nil): _*)
    } yield {
      checkSlug(maybeSelection, slug) {
        case Left(newSlug)    => MovedPermanently(routes.Application.selectionDetail(id, newSlug).url)
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

  def blog(maybeCategory: Option[String]) = Prismic.action { implicit request =>
    for {
      posts <- maybeCategory.map(
        category => ctx.api.forms("blog").query(s"""[[:d = at(my.blog-post.category, "$category")]]""").orderings("[my.blog-post.date desc]")
      ).getOrElse(ctx.api.forms("blog").orderings("[my.blog-post.date desc]")).ref(ctx.ref).submit()
    } yield {
      Ok(views.html.posts(posts.results))
    }
  }

  def blogPost(id: String, slug: String) = Prismic.action { implicit request =>
    for {
      maybePost <- getDocument(id)
      relatedProducts <- getDocuments(maybePost.map(_.getAll("blog-post.relatedproduct").collect {
        case link:Fragment.DocumentLink if link.typ == "product" && !link.isBroken => link.id
      }).getOrElse(Nil): _*)
      relatedPosts <- getDocuments(maybePost.map(_.getAll("blog-post.relatedpost").collect {
        case link:Fragment.DocumentLink if link.typ == "blog-post" && !link.isBroken => link.id
      }).getOrElse(Nil): _*)
    } yield {
      checkSlug(maybePost, slug) {
        case Left(newSlug) => MovedPermanently(routes.Application.blogPost(id, newSlug).url)
        case Right(post)   => Ok(views.html.postDetail(post, relatedProducts, relatedPosts))
      }
    }
  }

  // -- Products

  val ProductCategories = collection.immutable.ListMap(
    "Macaron" -> "Macarons",
    "Cupcake" -> "Cup Cakes",
    "Pie" -> "Little Pies"
  )

  def products = Prismic.action { implicit request =>
    for {
      products <- ctx.api.forms("products").ref(ctx.ref).submit()
    } yield {
      Ok(views.html.products(products.results))
    }
  }

  def productDetail(id: String, slug: String) = Prismic.action { implicit request =>
    for {
      maybeProduct <- getDocument(id)
      relatedProducts <- getDocuments(maybeProduct.map(_.getAll("product.related").collect {
        case link:Fragment.DocumentLink if link.typ == "product" && !link.isBroken => link.id
      }).getOrElse(Nil): _*)
    } yield {
      checkSlug(maybeProduct, slug) {
        case Left(newSlug)  => MovedPermanently(routes.Application.productDetail(id, newSlug).url)
        case Right(product) => Ok(views.html.productDetail(product, relatedProducts))
      }
    }
  }

  def productsByFlavour(flavour: String) = Prismic.action { implicit request =>
    for {
      products <- ctx.api.forms("everything").query(s"""[[:d = at(my.product.flavour, "$flavour")]]""").ref(ctx.ref).submit()
    } yield {
      if (products.results.isEmpty) {
        PageNotFound
      }
      else {
        Ok(views.html.productsByFlavour(flavour, products.results))
      }
    }
  }

  // -- Search

  def search(query: Option[String]) = Prismic.action { implicit request =>
    query.map(_.trim).filterNot(_.isEmpty).map { q =>
      for {
        products <- ctx.api.forms("everything").query(s"""[[:d = any(document.type, ["product", "selection"])][:d = fulltext(document, "$q")]]""").ref(ctx.ref).submit()
        others <- ctx.api.forms("everything").query(s"""[[:d = any(document.type, ["article", "blog-post", "job-offer", "store"])][:d = fulltext(document, "$q")]]""").ref(ctx.ref).submit()
      } yield {
        Ok(views.html.search(query, products.results, others.results))
      }
    }.getOrElse {
      Future.successful(Ok(views.html.search()))
    }
  }

}
