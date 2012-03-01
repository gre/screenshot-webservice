import play.api._
import play.api.Play.current
import play.api.libs.concurrent._
import screenshot._
import akka.actor.Actor
import akka.actor._
import akka.util._
import akka.actor.Actor._
import akka.actor.Scheduler
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeUnit._
import java.util.Date
import java.io.File
import collection.JavaConversions._


object Global extends GlobalSettings {
  override def onStart(app: Application) {
    PhantomJSCheck()
  }
}

class CacheCleaner extends Actor {
  def receive = {
    case "clean" => {
      Logger.debug("Cleaning old files...")
      val expiredDate = new Date().getTime() - ScreenshotCache.expirationSeconds*1000
      val nbCleaned = new File(ScreenshotProcessing.outputDir).listFiles.toList.filter(_.lastModified < expiredDate).map(_.delete()).length
      Logger.debug(nbCleaned+" cleaned.")
    }
  }
}

object PhantomJSCheck {
  var system = ActorSystem("cache")
  val cleanFrequency = Play.configuration.getInt("screenshot.cache.clean.frequency").getOrElse(1)
  val cacheCleaner = system.actorOf(Props[CacheCleaner])
  system.scheduler.schedule(Duration(cleanFrequency*60*60, SECONDS), Duration(cleanFrequency*60*60, SECONDS), cacheCleaner, "clean")

  def apply() {
    Logger.debug("PhantomJS checking...")
    ScreenshotProcessing.process(
      ScreenshotRequest("http://google.com/", "jpg", Size(1024, 1024))
    ) match {
      case Left(_) => Logger.debug("PhantomJS checked with success.")
      case Right(e) => {
        Logger.error("/!\\ PhantomJS check failed: "+e)
        Logger.error("/!\\ Is phantomjs correctly installed on your system?")
        Logger.error("/!\\ For more information please refer to phantomjs install documentation.")
      }

    }
  }
}

