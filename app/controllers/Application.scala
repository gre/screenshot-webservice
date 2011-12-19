package controllers

import play.api._
import play.api.mvc._

import java.io._
import java.net.URI

import screenshots._

import scalax.io.{ Resource }

import play.api.cache.BasicCache
import play.api.Play.current

object Application extends Controller {

  def index = Action {
    Redirect("http://github.com/gre/screenshot-webservice");
  }

  lazy val cache = new BasicCache()
  val expirationSeconds = 5*60

  def screenshot = Action { (request) =>
    request.queryString.get("url").flatMap(_.headOption).map(link => {
      if(new URI(link).getHost()=="localhost")
        Forbidden("localhost forbidden")
      else {
        val params = ScreenshotParams(
          link, 
          request.queryString.get("width").flatMap(_.headOption).map(_.toInt).getOrElse(1024), 
          request.queryString.get("height").flatMap(_.headOption).map(_.toInt).getOrElse(1024)
        )
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
