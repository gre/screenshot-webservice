package controllers

import play.api._
import play.api.mvc._

import java.io._
import java.net._

import screenshots._

import scalax.io.{ Resource }

import play.api.cache.BasicCache
import play.api.Play.current

object Application extends Controller {

  lazy val cache = new BasicCache()
  val expirationSeconds = Play.configuration.getInt("screenshot.cache.expiration").getOrElse(600)

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

  def screenshot = Action { (request) =>
    request.queryString.get("url").flatMap(_.headOption).map(link => {
      val format = request.queryString.get("format").flatMap(_.headOption).flatMap(Format(_)).getOrElse(defaultFormat)
      val address = InetAddress.getByName(new URI(link).getHost())
      if(address.isLoopbackAddress || localAddressForbidden && address.isSiteLocalAddress)
        Forbidden("This address is forbidden.")
      else if( autorizedFormats.length!=0 && !autorizedFormats.contains(format) )
        Forbidden("format "+format+" unautorized.")
      else {
        
        val params = ScreenshotParams( link, format )
        val image : Option[String] = cache.get[String](params.url).map(url => {
          if (new File(url).exists()) Some(url) else {
            cache.set(url, null)
            None
          }
        }).getOrElse({  
          val image = LocalScreenshot(params)
          cache.set(params.url, image)
          image
        })
        image.map(filepath => new FileInputStream(filepath)) match {
          case Some(stream) => Ok(Resource.fromInputStream(stream).byteArray).as("image/png")
          case None => InternalServerError("unable to process the screenshot")
        }
      }
    }).getOrElse(Forbidden("url is required."))
  }

}
