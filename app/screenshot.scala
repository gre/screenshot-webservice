package screenshot

import java.io._
import java.util.Date
import sys.process._
import scala.util.matching._
import scala.collection.mutable.{ Map => MMap, HashMap => MHashMap }
import scalax.io.{ Resource }
import play.api._
import play.api.mvc._
import play.api.Play.current
import play.api.libs.concurrent._
import play.api.cache.BasicCache
import play.api.libs.akka._
import akka.actor._
import Actor._
import akka.util.duration._


/*** Inputs ***/

case class ScreenshotRequest(url: String, format: Format)

case class Format(width:Int, height:Int) {
  override def toString = width+"x"+height
}

object Format {
  def apply(s:String) = new Regex("([0-9]+)x([0-9]+)").unapplySeq(s).flatMap(l => l.map(_.toInt) match {
    case List(width, height) => Some(new Format(width, height))
    case _ => None
  })
}

/*** Outputs ***/

case class Screenshot(filepath: String) {
  lazy val headers = Screenshot.headersFor(this)
}

object Screenshot {
  val processingActor = actorOf[ScreenshotProcessing].start
  val cacheActor = actorOf[ScreenshotCache].start
  val TIMEOUT = 30000 millis
  private val waitingRequests: MMap[ScreenshotRequest, Promise[Option[Screenshot]]] = new MHashMap[ScreenshotRequest, Promise[Option[Screenshot]]]()
  
  def headersFor(s:Screenshot) = {
    val f = new File(s.filepath)
    val lastModified = new Date(f.lastModified)
    val expires = new Date(f.lastModified+ScreenshotCache.expirationSeconds*1000)
    Array("Expires" -> expires.toGMTString, "Last-Modified" -> lastModified.toGMTString)
  }

  def apply(params:ScreenshotRequest) : Promise[Option[Screenshot]] = {
    waitingRequests.get(params) getOrElse {
      val promise = (cacheActor.?(params)(timeout = TIMEOUT) ).mapTo[Option[Screenshot]].asPromise
      waitingRequests += ( (params, promise) )
      promise extend (p => waitingRequests -= params)
      promise
    }
  }

  implicit def writeableOf_Screenshot(implicit codec: Codec): Writeable[Screenshot] = 
    Writeable[Screenshot](s => Resource.fromInputStream(new FileInputStream(s.filepath)).byteArray )

  implicit def contentTypeOf_Screenshot(implicit codec: Codec): ContentTypeOf[Screenshot] = 
    ContentTypeOf[Screenshot](Some("image/png"))
}


/*** Actors ***/

class ScreenshotCache extends Actor {
  import ScreenshotCache._
  def logger = Logger("ScreenshotCache")

  def receive = {
    // get
    case params: ScreenshotRequest => {
      get(params) map {
        logger.debug("retrieve from cache for "+params)
        self reply Some(_)
      } getOrElse {
        Screenshot.processingActor forward params
      }
    }
    // set
    case (params: ScreenshotRequest, screenshot: Screenshot) => {
      logger.debug("save to cache for "+params+" -> "+screenshot)
      set(params, screenshot)
    }
  }
}

class ScreenshotProcessing extends Actor {
  import ScreenshotProcessing._
  def logger = Logger("ScreenshotProcessing")

  def receive = {
    // process screenshot with phantomjs
    case params: ScreenshotRequest => {
      logger.debug("processing... "+params)
      val screenshot = process(params) 
      logger.debug("done.")
      screenshot map { Screenshot.cacheActor ! (params, _) } // send it to the cache actor
      self reply screenshot
    }
  }
}

/*** core ***/

object ScreenshotCache {
  lazy val cache = new BasicCache()
  val expirationSeconds = Play.configuration.getInt("screenshot.cache.expiration").getOrElse(600)

  def get(params:ScreenshotRequest) = cache.get[Screenshot](params.url).flatMap(screenshot => {
    if (new File(screenshot.filepath) exists) 
      Some(screenshot) 
    else {
      clear(params)
      None
    }
  })
  def set(params:ScreenshotRequest, screenshot:Screenshot) = cache.set(params.url, screenshot, expirationSeconds)
  def clear(params:ScreenshotRequest) = cache.set(params.url, null)
}

object ScreenshotProcessing {
  val logger = new ProcessLogger {
    val logger = Logger("phantomjs")
    def out(s: => String): Unit = logger info s
    def err(s: => String): Unit = logger error s
    def buffer[T](f: => T): T = f
  }

  def process(params:ScreenshotRequest) : Option[Screenshot] = {
    val output = getAbsolutePath(params)
    val process = Process(script+" "+params.url+" "+output+" "+params.format.width+" "+params.format.height)
    process.run(logger).exitValue() match {
      case 0 => Some(Screenshot(output))
      case _ => None
    }
  }

  val script = Play.getFile("phantomjs_scripts/render").getAbsolutePath
  val outputDir = {
    val f = Play.getFile("phantomjs_cache/")
    f.mkdirs()
    f.getAbsolutePath
  }

  def getAbsolutePath(params:ScreenshotRequest) = outputDir+"/"+md5(params.url)+"_"+params.format+".png"

  private def byteArrayToString(data: Array[Byte]) = {
     val hash = new java.math.BigInteger(1, data).toString(16)
     "0"*(32-hash.length) + hash
  }
  private def md5(s: String):String =
    byteArrayToString(java.security.MessageDigest.getInstance("MD5").digest(s.getBytes("US-ASCII")));

}

