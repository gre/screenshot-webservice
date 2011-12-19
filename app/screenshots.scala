package screenshots 

import sys.process._
import play.api._
import play.api.Play.current

case class ScreenshotParams(
  url: String,
  width: Int, 
  height: Int
)

object LocalScreenshot {
  val script = Play.getFile("phantomjs_scripts/render").getAbsolutePath
  val outputDir = {
    val f = Play.getFile("phantomjs_cache/")
    f.mkdirs()
    f.getAbsolutePath
  }

  def getAbsolutePath(params:ScreenshotParams) = outputDir+"/"+md5(params.url)+"_"+params.width+"_"+params.height+".png"
  
  def apply(params:ScreenshotParams) : Option[String] = {
    val output = getAbsolutePath(params)
    Process(script+" "+params.url+" "+output+" "+params.width+" "+params.height).run().exitValue() match {
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

