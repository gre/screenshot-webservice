import sbt._
import Keys._
import play.Project._

object ApplicationBuild extends Build {
  val appName         = "screenshot-webservice"
  val appVersion      = "1.0"

  val appDependencies = Seq(cache)

  val main = play.Project(appName, appVersion, appDependencies)
}
