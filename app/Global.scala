import play.Logger
import play.api._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.mvc.{EssentialAction, Filter, Filters}
import play.filters.gzip.GzipFilter

// http://www.playframework.com/documentation/2.2.x/ScalaGlobal
object Global extends GlobalSettings {

  def isWorker: Boolean = { scala.util.Properties.propOrNull("config.isworker") == "true" }

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
    Filters(super.doFilter(next), loggingFilter, new GzipFilter())
  }

  override def onStart(app: Application) {
    Logger.info("Application has started")

    model.Model.init()
    actors.DailySoccerActors.init(isWorker);
  }

  override def onStop(app: Application) {
    Logger.info("Application shutdown...")

    actors.DailySoccerActors.shutdown();
    model.Model.shutdown()
  }
}

