import sbt._
import Keys._
import play.Project._

object ApplicationBuild extends Build {

  val appName         = "lesbonneschoses"
  val appVersion      = "1.0-SNAPSHOT"

  val main = play.Project(appName, appVersion).settings(
    resolvers += "Prismic.io kits" at "https://github.com/prismicio/repository/raw/master/maven/",
    libraryDependencies += "io.prismic" %% "scala-kit" % "1.0-SNAPSHOT"
  )

}
