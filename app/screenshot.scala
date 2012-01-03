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

case class ScreenshotRequest(url: String, format:String, size: Size)

case class Size(width:Int, height:Int) {
  override def toString = width+"x"+height
}

object Size {
  def apply(s:String) = new Regex("([0-9]+)x([0-9]+)").unapplySeq(s).flatMap(l => l.map(_.toInt) match {
    case List(width, height) => Some(new Size(width, height))
    case _ => None
  })
}

/*** Outputs ***/

case class Screenshot(filepath: String, date: Date, expiration: Int) {
  lazy val headers = Screenshot.headersFor(this)
  lazy val file = new File(filepath)
  lazy val data = Resource.fromInputStream(new FileInputStream(file)).byteArray
  lazy val ext = filepath substring (1 + filepath lastIndexOf '.')
  implicit def contentTypeOf_Screenshot(implicit codec: Codec): ContentTypeOf[Screenshot] = 
    ContentTypeOf[Screenshot](Some("image/"+ext))
}

object Screenshot {
  val processingActor = actorOf[ScreenshotProcessingBalancer].start
  val cacheActor = actorOf[ScreenshotCache].start

  private val waitingRequests: MMap[ScreenshotRequest, Promise[Either[Screenshot, ScreenshotError]]] = 
    new MHashMap[ScreenshotRequest, Promise[Either[Screenshot, ScreenshotError]]]()

  def apply(params:ScreenshotRequest) : Promise[Either[Screenshot, ScreenshotError]] = {
    waitingRequests.get(params) getOrElse {
      val promise = cacheActor.?(params)(timeout = 120 seconds).
                    mapTo[Either[Screenshot, ScreenshotError]].asPromise
      waitingRequests += ( (params, promise) )
      promise extend (p => waitingRequests -= params)
      promise
    }
  }

  def responseFor(s:Screenshot)(implicit request:Request[_]) : SimpleResult[_] = {
    import s.contentTypeOf_Screenshot
    request.headers.get(IF_NONE_MATCH).filter(_ == etagFor(s)).map(_ => NotModified).getOrElse {
      Ok(s)
    }.withHeaders(s.headers:_*)
  }

  def headersFor(s:Screenshot) = {
    val lastModified = s.date
    val expires = new Date(s.date.getTime+s.expiration*1000)
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
}

sealed case class ScreenshotError(message:String) {
  lazy val response = InternalServerError(message)
  override def toString = message
}

object TimeoutError extends ScreenshotError("Resource was too long to respond.")
object NetworkError extends ScreenshotError("Resource unreachable.")
object UnknownError extends ScreenshotError("Resource screenshot processing failed.")

/*** Actors ***/

class ScreenshotCache extends Actor {
  import ScreenshotCache._
  def logger = Logger("ScreenshotCache")

  def receive = {
    // get
    case params: ScreenshotRequest => {
      get(params) map {
        logger.debug("retrieve from cache for "+params)
        self reply _
      } getOrElse {
        Screenshot.processingActor forward params
      }
    }
    // set
    case (params: ScreenshotRequest, Left(screenshot:Screenshot)) => {
      logger.debug("save to cache for "+params+" -> "+screenshot)
      set(params, screenshot)
    }
    case (params: ScreenshotRequest, Right(e:ScreenshotError)) => {
      logger.debug("error saved to cache for "+params+" -> "+e)
      set(params, e)
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
      logger.debug("current load: "+actors.map(_.dispatcher.mailboxSize(_)).mkString("[", ", ", "]"));
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
      Screenshot.cacheActor ! (params, screenshot) // send it to the cache actor
      self reply screenshot
    }
  }
}

/*** core ***/

object ScreenshotCache {
  lazy val cache = new BasicCache()
  lazy val errorCache = new BasicCache()
  val constExpirationSeconds = Play.configuration.getInt("screenshot.cache.expiration").getOrElse(600)
  def expirationSeconds() = (constExpirationSeconds * (0.95 + 0.1*Math.random)).toInt // +- 5% of randomness
  val errorExpirationSeconds = Play.configuration.getInt("screenshot.error.cache.expiration").getOrElse(300)

  def get(params:ScreenshotRequest) : Option[Either[Screenshot, ScreenshotError]] = {
    val s:Option[Either[Screenshot, ScreenshotError]] = cache.get[Screenshot](params.toString).flatMap(screenshot => {
      if (screenshot.file exists) 
        Some(Left(screenshot))
      else {
        clear(params)
        None
      }
    })
    if (s.isDefined) s else errorCache.get[ScreenshotError](params.toString).map(e => Right(e))
  }

  def set(params:ScreenshotRequest, screenshot:Screenshot) = {
    val expiration = expirationSeconds()
    val s = screenshot.copy(expiration=expiration)
    cache.set(params.toString, s, expiration)
  }

  def set(params:ScreenshotRequest, e:ScreenshotError) =
    errorCache.set(params.toString, e, errorExpirationSeconds)

  def clear(params:ScreenshotRequest) = {
    errorCache.set(params.toString, null)
    cache.set(params.toString, null)
  }
}

object ScreenshotProcessing {
  val logger = new ProcessLogger {
    val logger = Logger("phantomjs")
    def out(s: => String): Unit = logger info s
    def err(s: => String): Unit = logger error s
    def buffer[T](f: => T): T = f
  }

  object ExitCode {
    val SUCCESS = 0;
    val TIMEOUT = 2;
    val OPEN_FAILED = 3;
  }

  def process(params:ScreenshotRequest) : Either[Screenshot, ScreenshotError] = {
    val output = getAbsolutePath(params)
    val process = Process(script+" "+params.url+" "+output+" "+params.size.width+" "+params.size.height)
    process.run(logger).exitValue() match {
      case ExitCode.SUCCESS => Left(Screenshot(output, new Date(), 0))
      case ExitCode.TIMEOUT => Right(TimeoutError)
      case ExitCode.OPEN_FAILED => Right(NetworkError)
      case _ => Right(UnknownError)
    }
  }

  val script = Play.getFile("phantomjs_scripts/render").getAbsolutePath
  val outputDir = {
    val f = Play.getFile("phantomjs_cache/")
    f.mkdirs()
    f.getAbsolutePath
  }

  def getAbsolutePath(params:ScreenshotRequest) = outputDir+"/"+md5(params.url)+"_"+params.size+"."+params.format

  private def byteArrayToString(data: Array[Byte]) = {
     val hash = new java.math.BigInteger(1, data).toString(16)
     "0"*(32-hash.length) + hash
  }
  private def md5(s: String):String =
    byteArrayToString(java.security.MessageDigest.getInstance("MD5").digest(s.getBytes("US-ASCII")));

}

