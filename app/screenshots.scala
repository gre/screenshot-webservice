package screenshots 

import sys.process._
import play.api._
import play.api.Play.current

import scala.util.matching._

case class ScreenshotParams(
  url: String,
  format: Format
)

case class Format(width:Int, height:Int) {
  override def toString = width+"x"+height
}

object Format {
  def apply(s:String) = new Regex("([0-9]+)x([0-9]+)").unapplySeq(s).flatMap(l => l.map(_.toInt) match {
      case List(width, height) => Some(new Format(width, height))
      case _ => None
    })
}

object LocalScreenshot {
  val script = Play.getFile("phantomjs_scripts/render").getAbsolutePath
  val outputDir = {
    val f = Play.getFile("phantomjs_cache/")
    f.mkdirs()
    f.getAbsolutePath
  }

  def getAbsolutePath(params:ScreenshotParams) = outputDir+"/"+md5(params.url)+"_"+params.format+".png"
  
  def apply(params:ScreenshotParams) : Option[String] = {
    val output = getAbsolutePath(params)
    Process(script+" "+params.url+" "+output+" "+params.format.width+" "+params.format.height).run().exitValue() match {
        case 0 => Some(output)
        case _ => None
      }
  }

  private def byteArrayToString(data: Array[Byte]) = {
     val hash = new java.math.BigInteger(1, data).toString(16)
     "0"*(32-hash.length) + hash
  }
  private def md5(s: String):String = byteArrayToString(java.security.MessageDigest.getInstance("MD5").digest(s.getBytes("US-ASCII")));
}

