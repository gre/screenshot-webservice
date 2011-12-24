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

  def touch = ScreenshotAction { (request, url, format) =>
    val promise = Screenshot(ScreenshotRequest(url, format)).map(_ match { 
      case Left(s) => Ok.withHeaders(s.headers:_*) 
      case Right(e) => e.response
      case _ => InternalServerError("Screenshot processing failed.")
    })
    AsyncResult(promise.orTimeout(Status(202), 100))
  }

  def get = ScreenshotAction { (request, url, format) => 
    AsyncResult {
      Screenshot(ScreenshotRequest(url, format)).extend( _.value match {
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
  }
  
  object ScreenshotAction {
    val autorizedFormats:List[Format] = {
      val conf = Play.configuration.getString("screenshot.format.autorized").getOrElse("any")
      if(conf=="any") Nil
      else {
        conf.split(" ").toList.collect(Format(_) match { 
          case Some(f) => f 
        })
      }
    }
    val defaultFormat = Format(Play.configuration.getString("screenshot.format.default").getOrElse("1024x1024")).getOrElse({
      Logger.warn("invalid screenshot.format.default conf. Fallback on 1024x1024")
      Format(1024,1024)
    })
    val localAddressForbidden = Play.configuration.getBoolean("localAddress.forbidden").getOrElse(true)

    def apply(f: (Request[_], String, Format) => Result) = Action { request =>
      request.queryString.get("url").flatMap(_.headOption).map(url => {
        val format = request.queryString.get("format").flatMap(_.headOption).flatMap(Format(_)).getOrElse(defaultFormat)
        try {
          val address = InetAddress.getByName(new URI(url).getHost())
          if(localAddressForbidden && address.isLoopbackAddress)
            Forbidden("This address is forbidden.")
          else if( autorizedFormats.length!=0 && !autorizedFormats.contains(format) )
            Forbidden("format "+format+" unautorized.")
          else
            f(request, url, format)
        }
        catch {
          case e:URISyntaxException => Forbidden("Invalid URL syntax")
          case e:UnknownHostException => Forbidden("Invalid URL host")
        }

      }).getOrElse(Forbidden("url is required."))

    }
  }

}
