package controllers

import java.net._
import play.api._
import play.api.libs.concurrent.{Promise => PPromise}
import play.api.mvc._
import play.api.Play.current
import screenshot._
import akka.pattern.{AskTimeoutException ⇒ ActorTimeoutException}
import scala.concurrent._
import scala.concurrent.duration._
import scala.util.{Success, Failure}
import play.api.libs.concurrent.Execution.Implicits._

object Application extends Controller {

  def touch(format:String) = ScreenshotAction.async { (request, url, size) ⇒
    if (formats.contains(format)) {
      val future = Screenshot(ScreenshotRequest(url, format, size)).map {
        case Left(s)  ⇒ Screenshot.responseFor(s)(request)
        case Right(e) ⇒ e.response
        case _        ⇒ InternalServerError("Screenshot processing failed.")
      }
      val timeoutFuture = PPromise.timeout(Status(202), 100.milliseconds)
      Future.firstCompletedOf(Seq(future, timeoutFuture))
    } else Future.successful(Status(415)("Unsupported format"))
  }


  def get(format:String) = {
    val error = Status(503)("The server was not able to finish processing the screenshot.")
    ScreenshotAction.async { (request, url, size) ⇒
      if (formats.contains(format)) {
        val promise = Promise[SimpleResult]()
        Screenshot(ScreenshotRequest(url, format, size)).onComplete {
          case Success(v) => v match {
            case Left(s) ⇒ promise.success(Screenshot.responseFor(s)(request))
            case Right(e) ⇒ promise.success(e.response)
            case _ ⇒ promise.success(InternalServerError("Screenshot processing failed."))
          }
          case Failure(e) => e match {
            case e:ActorTimeoutException ⇒ promise.success(error)
          }
        }
        promise.future
      } else Future.successful(Status(415)("Unsupported format"))
    }
  }

  val formats = Play.configuration.getString("screenshot.format.autorized")
    .getOrElse("jpg").split("\\s+").toList
  
  object ScreenshotAction {
    val autorizedSizes:List[Size] = {
      val conf = Play.configuration.getString("screenshot.size.autorized").getOrElse("any")
      if(conf == "any") Nil
      else {
        conf.split("\\s+").toList.collect(Size(_) match { 
          case Some(f) ⇒ f 
        })
      }
    }
    val defaultSize = Size(Play.configuration.getString("screenshot.size.default")
      .getOrElse("1024x1024")).getOrElse({
        Logger.warn("invalid screenshot.size.default conf. Fallback on 1024x1024")
        Size(1024,1024)
    })
    val localAddressForbidden = Play.configuration.getBoolean("localAddress.forbidden")
      .getOrElse(true)


    def async(f: (Request[_], String, Size) ⇒ Future[SimpleResult]) = Action.async { request ⇒
        request.queryString.get("url").flatMap(_.headOption).map(url ⇒ {
          val size = request.queryString.get("size").flatMap(_.headOption)
            .flatMap(Size(_)).getOrElse(defaultSize)
          try {
            val address = InetAddress.getByName(new URI(url).getHost)
            if(localAddressForbidden && address.isLoopbackAddress)
              Future.successful(Forbidden("This address is forbidden."))
            else if( autorizedSizes.length!=0 && !autorizedSizes.contains(size) )
              Future.successful(Forbidden("size "+size+" unautorized."))
            else
              f(request, url, size)
          }
          catch {
            case e:URISyntaxException   ⇒ Future.successful(Forbidden("Invalid URL syntax"))
            case e:UnknownHostException ⇒ Future.successful(Forbidden("Invalid URL host"))
          }

        }).getOrElse(Future.successful(Forbidden("url is required.")))
    }
  }

}
