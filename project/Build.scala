import sbt._, Keys._
import play._

object ApplicationBuild extends Build {

  val appName         = "lesbonneschoses"
  val appVersion      = "1.2"

  val main = Project(appName, file(".")) enablePlugins PlayScala settings (

    version := appVersion,
    scalaVersion := "2.11.1",

    // The Scala kit
    libraryDependencies += "io.prismic" %% "scala-kit" % "1.3.3"
  )

}
