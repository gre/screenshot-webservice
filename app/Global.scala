import play.api._
import play.api.Play.current
import play.api.libs.concurrent._
import screenshot._
import akka.actor.Actor
import akka.actor.Actor._
import akka.actor.Scheduler
import java.util.concurrent.TimeUnit
import java.io.File
import java.util.Date
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
      val list = new File(ScreenshotProcessing.outputDir).listFiles.toList.filter(_.lastModified < expiredDate) map { file =>
        Logger.debug( (new Date().getTime())+" "+file.lastModified+" "+file)
        file.delete()
      }
      Logger.debug(list.length+" cleaned.")
    }
  }
}

object PhantomJSCheck {
  val cleanFrequency = Play.configuration.getInt("screenshot.cache.clean.frequency").getOrElse(1)
  val cacheCleaner = actorOf[CacheCleaner].start()
  Scheduler.schedule(cacheCleaner, "clean", 1, cleanFrequency*60*60, TimeUnit.SECONDS)  

  def apply() {
    Logger.debug("PhantomJS checking...")
    Screenshot(
      ScreenshotRequest("http://google.com/", Format(1024, 1024))
    ) value match {
      case Redeemed(Some(_)) => Logger.debug("PhantomJS checked with success.")
      case o => {
        Logger.error("/!\\ PhantomJS check failed.")
        Logger.error("/!\\ Is phantomjs correctly installed on your system?")
        Logger.error("/!\\ For more information please refer to phantomjs install documentation.")
      }

    }
  }
}

