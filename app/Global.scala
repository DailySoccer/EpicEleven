import play.Logger
import play.api.Play.current
import play.api._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.ws.{WS, WSRequestHolder}
import play.api.mvc.{EssentialAction, Filter, Filters}
import play.filters.gzip.GzipFilter

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

// http://www.playframework.com/documentation/2.2.x/ScalaGlobal
object Global extends GlobalSettings {

  def isWorker: Boolean = { scala.util.Properties.propOrNull("config.isworker") == "true" }

  var release = "devel" // Will be set with getRelease if in production

  def getRelease:String = {
    var version = "devel"

    try {
      val herokuKey = Play.current.configuration.getString("heroku_key").orNull
      val herokuApp = Play.current.configuration.getString("heroku_app").orNull

      if (herokuKey != null) {
        val url = f"https://api.heroku.com/apps/$herokuApp%s/releases"
        val holder: WSRequestHolder = WS.url(url).withHeaders(("Authorization", f"Bearer $herokuKey%s"))

        val futureResponse: Future[String] = holder.get().map {
          response => (response.json \\ "name").last.as[String]
        }

        version = Await.result(futureResponse, 10 seconds)
      }
    } catch {
      case e: Exception => Logger.error("WTF 7932: ", e)
    }

    version
  }

  val releaseFilter = Filter { (nextFilter, requestHeader) =>
    nextFilter(requestHeader).map { result =>
      result.withHeaders("Release-Version" -> release)
    }
  }

  val loggingFilter = Filter { (nextFilter, requestHeader) =>
    val startTime = System.currentTimeMillis

    nextFilter(requestHeader).map { result =>
      val endTime = System.currentTimeMillis
      val requestTime = endTime - startTime

      //Logger.info(s"${requestHeader.method} ${requestHeader.uri} took ${requestTime}ms " + s"and returned ${result.header.status}")
      //val action = requestHeader.tags(Routes.ROUTE_CONTROLLER) + "." + requestHeader.tags(Routes.ROUTE_ACTION_METHOD)

      // Quitamos los logs que vienen de la descarga de Assets y de la zona de admin
      if (!requestHeader.tags(Routes.ROUTE_CONTROLLER).contains("Assets") && !requestHeader.tags(Routes.ROUTE_CONTROLLER).contains("admin")) {
        Logger.info(requestHeader.tags(Routes.ROUTE_ACTION_METHOD) + s" took ${requestTime}ms")
      }

      result.withHeaders("Request-Time" -> requestTime.toString)
    }
  }

  override def doFilter(next: EssentialAction): EssentialAction = {
    Filters(super.doFilter(next), loggingFilter, releaseFilter, new GzipFilter())
  }

  override def onStart(app: Application) {

    val processType = if (isWorker) "Worker Process" else "Web Process"

    release = getRelease
    Logger.info(s"Epic Eleven $processType version $release has started")

    model.Model.init()

    if (isWorker) {
      actors.DailySoccerActors.init()
    }
  }

  override def onStop(app: Application) {
    Logger.info("Epic Eleven shutdown...")

    if (isWorker) {
      actors.DailySoccerActors.shutdown()
    }

    model.Model.shutdown()
  }
}

