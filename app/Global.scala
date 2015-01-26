import play.Logger
import play.api.Play.current
import play.api._
import play.api.mvc._
import play.api.mvc.Results._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.ws.{WS, WSRequestHolder}
import play.api.mvc.{EssentialAction, Filter, Filters}
import play.filters.gzip.GzipFilter
import stormpath.StormPathClient

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

// http://www.playframework.com/documentation/2.2.x/ScalaGlobal
object Global extends GlobalSettings {

  var releaseVersion = "devel"           // Will be set onStart to the Heroku deploy version when we are in production
  var instanceRole = "DEVELOPMENT_ROLE"  // Role of this machine (DEVELOPMENT_ROLE, WEB_ROLE, OPTAPROCESSOR_ROLE, BOTS_ROLE...)

  val releaseFilter = Filter { (nextFilter, requestHeader) =>
    nextFilter(requestHeader).map { result =>
      result.withHeaders("Release-Version" -> releaseVersion)
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

    instanceRole = readInstanceRole
    releaseVersion = readReleaseVersion
    Logger.info(s"Epic Eleven $instanceRole, version $releaseVersion has started")

    model.Model.init()
    actors.DailySoccerActors.init(false)

    if (StormPathClient.instance.isConnected) {
      Logger.info("Stormpath connected properly")
    }
    else {
      Logger.info( "Stormpath not connected")
    }
  }

  override def onStop(app: Application) {
    Logger.info("Epic Eleven shutdown...")

    actors.DailySoccerActors.shutdown()
    model.Model.shutdown()
  }

  override def onError(request: RequestHeader, ex: Throwable) = {
    // Si se provoca una excepcion la capturamos aqui de forma global, y devolvemos un HttpStatus 500, asegurandonos
    // de settear bien el CORS para que el browser no se niegue a decirle al cliente lo que esta pasando realmente
    Future.successful(InternalServerError("Internal Server Error").withHeaders("Access-Control-Allow-Origin" -> "*"))
  }

  private def readInstanceRole: String = {
    val temp = scala.util.Properties.propOrNull("config.instanceRole")

    if (temp == null) instanceRole else temp
  }

  private def readReleaseVersion : String = {
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
    }
    catch {
      case e: Exception => Logger.error("WTF 7932 Error durante la inicializacion de la version", e)
    }

    version
  }

}

