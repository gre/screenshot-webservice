package screenshot

import java.io._
import java.util.{ SimpleTimeZone, Date }
import java.text.SimpleDateFormat
import sys.process._
import scala.util.matching._
import scala.collection.mutable.{ Map => MMap, HashMap => MHashMap }
import scalax.io.{ Resource }
import play.api._
import play.api.mvc._
import play.api.mvc.Results._
import play.api.libs.Codecs
import play.api.http.HeaderNames._
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

case class Screenshot(filepath: String, date: Date) {
  lazy val headers = Screenshot.headersFor(this)
  lazy val file = new File(filepath)
  lazy val data = Resource.fromInputStream(new FileInputStream(file)).byteArray
}

object Screenshot {
  val processingActor = actorOf[ScreenshotProcessingBalancer].start
  val cacheActor = actorOf[ScreenshotCache].start

  private val waitingRequests: MMap[ScreenshotRequest, Promise[Option[Screenshot]]] = 
    new MHashMap[ScreenshotRequest, Promise[Option[Screenshot]]]()

  def apply(params:ScreenshotRequest) : Promise[Option[Screenshot]] = {
    waitingRequests.get(params) getOrElse {
      val promise = cacheActor.?(params)(timeout = 120 seconds).
                    mapTo[Option[Screenshot]].asPromise
      waitingRequests += ( (params, promise) )
      promise extend (p => waitingRequests -= params)
      promise
    }
  }

  def responseFor(s:Screenshot)(implicit request:Request[_]) : SimpleResult[_] = {
    request.headers.get(IF_NONE_MATCH).filter(_ == etagFor(s)).map(_ => NotModified).getOrElse {
      Ok(s)
    }.withHeaders(s.headers:_*)
  }

  def headersFor(s:Screenshot) = {
    val lastModified = s.date
    val expires = new Date(s.date.getTime+ScreenshotCache.expirationSeconds*1000)
    def toGMTString(d:Date):String = {
      val sdf = new SimpleDateFormat()
      sdf.setTimeZone(new SimpleTimeZone(0, "GMT"))
      sdf.applyPattern("dd MMM yyyy HH:mm:ss z")
      sdf.format(d)
    }

    Array(
      EXPIRES -> toGMTString(expires), 
      LAST_MODIFIED -> toGMTString(lastModified), 
      ETAG -> etagFor(s)
    )
  }

  private val etags = scala.collection.mutable.HashMap.empty[Screenshot, String]
  private def computeETag(data: Array[Byte]) = Codecs.sha1(data)
  def etagFor(s: Screenshot) = {
    etags.get(s).getOrElse {
      etags.put(s, computeETag(s.data))
      etags(s)
    }
  }

  implicit def writeableOf_Screenshot(implicit codec: Codec): Writeable[Screenshot] = 
    Writeable[Screenshot](s => s.data)

  implicit def contentTypeOf_Screenshot(implicit codec: Codec): ContentTypeOf[Screenshot] = 
    ContentTypeOf[Screenshot](Some("image/jpg"))
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

class ScreenshotProcessingBalancer extends Actor {
  val nbActors = Play.configuration.getInt("screenshot.actors.processing").getOrElse(2)
  val actors = Range(0, nbActors).map(_=>actorOf[ScreenshotProcessing].start)
  def logger = Logger("ScreenshotProcessingBalancer")

  def receive = {
    // find the most available actor : not sure about this first implementation (sounds like it's fair only if all screenshots takes the same time to render which is wrong, maybe actors should pull for new screenshot requests?)
    case params: ScreenshotRequest => {
      val actor = actors.sortWith((a:ActorRef, b:ActorRef) => 
        a.dispatcher.mailboxSize(a) < b.dispatcher.mailboxSize(b)).head
      logger.debug("balancing to "+actor+" with size "+actor.dispatcher.mailboxSize(actor))
      actor forward params
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
    if (screenshot.file exists) 
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
      case 0 => Some(Screenshot(output, new Date()))
      case _ => None
    }
  }

  val script = Play.getFile("phantomjs_scripts/render").getAbsolutePath
  val outputDir = {
    val f = Play.getFile("phantomjs_cache/")
    f.mkdirs()
    f.getAbsolutePath
  }

  def getAbsolutePath(params:ScreenshotRequest) = outputDir+"/"+md5(params.url)+"_"+params.format+".jpg"

  private def byteArrayToString(data: Array[Byte]) = {
     val hash = new java.math.BigInteger(1, data).toString(16)
     "0"*(32-hash.length) + hash
  }
  private def md5(s: String):String =
    byteArrayToString(java.security.MessageDigest.getInstance("MD5").digest(s.getBytes("US-ASCII")));

}

