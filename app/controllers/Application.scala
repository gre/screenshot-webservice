package controllers

import java.net._
import java.io._
import play.api._
import play.api.mvc._
import play.api.libs.concurrent._
import play.api.cache.BasicCache
import play.api.Play.current
import screenshot._
import Screenshot._
import akka.dispatch._


object Application extends Controller {

  implicit def eitherResults(e:Either[Result, Result]) = e.fold(a=>a, b=>b)
  implicit def promiseEitherResults(p:Promise[Either[Result, Result]]) : Promise[Result] = p.map(eitherResults(_))

  def touch(format:String) = ScreenshotAction { (request, url, size) =>
    (if (formats.contains(format)) Some(format) else None).map(format => {
      val promise = Screenshot(ScreenshotRequest(url, format, size)).map(_ match { 
        case Left(s) => Screenshot.responseFor(s)(request)
        case Right(e) => e.response
        case _ => InternalServerError("Screenshot processing failed.")
      })
      AsyncResult(promise.orTimeout(Status(202), 100))
    }).getOrElse(Status(415)("Unsupported format"))
  }

  def get(format:String) = ScreenshotAction { (request, url, size) => 
    (if (formats.contains(format)) Some(format) else None).map(format => {
      AsyncResult {
        Screenshot(ScreenshotRequest(url, format, size)).extend( _.value match {
          case Redeemed(screenshot) =>
            screenshot match { 
              case Left(s) => Screenshot.responseFor(s)(request)
              case Right(e) => e.response
              case _ => InternalServerError("Screenshot processing failed.")
  }
              case Thrown(e) => e match {
                case e:FutureTimeoutException => Status(503)("The server was not able to finish processing the screenshot.")
            }
        })
      }
    }).getOrElse(Status(415)("Unsupported format"))
  }

  val formats = Play.configuration.getString("screenshot.format.autorized").getOrElse("jpg").split("\\s+").toList
  
  object ScreenshotAction {
    val autorizedSizes:List[Size] = {
      val conf = Play.configuration.getString("screenshot.size.autorized").getOrElse("any")
      if(conf=="any") Nil
      else {
        conf.split("\\s+").toList.collect(Size(_) match { 
          case Some(f) => f 
        })
      }
    }
    val defaultSize = Size(Play.configuration.getString("screenshot.size.default").getOrElse("1024x1024")).getOrElse({
      Logger.warn("invalid screenshot.size.default conf. Fallback on 1024x1024")
      Size(1024,1024)
    })
    val localAddressForbidden = Play.configuration.getBoolean("localAddress.forbidden").getOrElse(true)


    def apply(f: (Request[_], String, Size) => Result) = Action { request =>
        request.queryString.get("url").flatMap(_.headOption).map(url => {
          val size = request.queryString.get("size").flatMap(_.headOption).flatMap(Size(_)).getOrElse(defaultSize)
          try {
            val address = InetAddress.getByName(new URI(url).getHost())
            if(localAddressForbidden && address.isLoopbackAddress)
              Forbidden("This address is forbidden.")
            else if( autorizedSizes.length!=0 && !autorizedSizes.contains(size) )
              Forbidden("size "+size+" unautorized.")
            else
              f(request, url, size)
          }
          catch {
            case e:URISyntaxException => Forbidden("Invalid URL syntax")
            case e:UnknownHostException => Forbidden("Invalid URL host")
          }

        }).getOrElse(Forbidden("url is required."))


    }
  }

}
