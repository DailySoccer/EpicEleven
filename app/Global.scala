import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

import play.Logger
import play.api.Play.current
import play.api._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.ws.{WS, WSRequestHolder}
import play.api.mvc.Results._
import play.api.mvc.{EssentialAction, Filter, Filters, _}
import play.filters.gzip.GzipFilter
import stormpath.StormPathClient
import utils.{TargetEnvironment, InstanceRole}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

// http://www.playframework.com/documentation/2.2.x/ScalaGlobal
object Global extends GlobalSettings {

  // Will be set onStart to the Heroku deploy version when we are in production
  var releaseVersion = "devel"

  // Role of this machine (DEVELOPMENT_ROLE, WEB_ROLE, OPTAPROCESSOR_ROLE, BOTS_ROLE...)
  var instanceRole = InstanceRole.DEVELOPMENT_ROLE

  // Target environment for our Model & Actors to connect to
  var targetEnvironment = TargetEnvironment.LOCALHOST


  val releaseFilter = Filter { (nextFilter, requestHeader) =>
    nextFilter(requestHeader).map { result =>
      result.withHeaders("Release-Version" -> releaseVersion)
    }
  }

  val concurrentThreads:AtomicInteger = new AtomicInteger()


  val loggingFilter = Filter { (nextFilter, requestHeader) =>
    val startTime = System.currentTimeMillis
    Logger.debug(""+concurrentThreads.incrementAndGet())

    nextFilter(requestHeader).map { result =>
      val endTime = System.currentTimeMillis
      concurrentThreads.decrementAndGet()
      val requestTime = endTime - startTime

      //Logger.info(s"${requestHeader.method} ${requestHeader.uri} took ${requestTime}ms " + s"and returned ${result.header.status}")
      //val action = requestHeader.tags(Routes.ROUTE_CONTROLLER) + "." + requestHeader.tags(Routes.ROUTE_ACTION_METHOD)

      // En algunas llamadas es posible que no venga este tag porque el router no sabe que hacer con ellas, por ejemplo cuando
      // es una llamada con verbo HEAD (probablemente generada por algun crawler bot)
      if (requestHeader.tags.contains(Routes.ROUTE_CONTROLLER)) {
        // Quitamos los logs que vienen de la descarga de Assets y de la zona de admin
        if (!requestHeader.tags(Routes.ROUTE_CONTROLLER).contains("Assets") && !requestHeader.tags(Routes.ROUTE_CONTROLLER).contains("admin")) {
          Logger.debug(requestHeader.tags(Routes.ROUTE_ACTION_METHOD) + s" took ${requestTime}ms")
        }
      }
      else {
        Logger.debug("Llamada no controlada por el router: {}", requestHeader)
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
    targetEnvironment = readTargetEnvironment
    Logger.info(s"Epic Eleven $instanceRole, Version: $releaseVersion, TargetEnvironment: $targetEnvironment, has started")

    model.Model.init(instanceRole, targetEnvironment)

    // Es aqui donde se llama a la inicializacion de Stormpath a traves del constructor
    if (StormPathClient.instance.isConnected) {
      Logger.info("Stormpath CONNECTED")
    }
  }


  override def onStop(app: Application) {
    Logger.info("Epic Eleven shutdown...")

    model.Model.shutdown()
  }


  override def onError(request: RequestHeader, ex: Throwable) = {
    // Si se provoca una excepcion la capturamos aqui de forma global, y devolvemos un HttpStatus 500, asegurandonos
    // de settear bien el CORS para que el browser no se niegue a decirle al cliente lo que esta pasando realmente
    Future.successful(InternalServerError("Internal Server Error").withHeaders("Access-Control-Allow-Origin" -> "*"))
  }


  private def readInstanceRole: InstanceRole = {
    val temp = scala.util.Properties.propOrNull("config.instanceRole")

    if (temp == null) instanceRole else InstanceRole.valueOf(temp)
  }

  private def readTargetEnvironment: TargetEnvironment = {
    val temp = Play.current.configuration.getString("targetEnvironment").orNull

    if (temp == null) targetEnvironment else TargetEnvironment.valueOf(temp)
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

        version = Await.result(futureResponse, Duration.create(10, TimeUnit.SECONDS))
      }
    }
    catch {
      case e: Exception => Logger.error("WTF 7932 Error durante la inicializacion de la version", e)
    }

    version
  }
}

