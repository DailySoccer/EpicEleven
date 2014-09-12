import jobs.Scheduler
import model.Model
import play.Logger
import play.api._
import play.api.mvc.{Filter, Filters, EssentialAction}
import play.filters.gzip.GzipFilter
import play.api.libs.concurrent.Execution.Implicits.defaultContext

// http://www.playframework.com/documentation/2.2.x/ScalaGlobal
object Global extends GlobalSettings {

  // Use the environment var "PORT" to determine whether we are a worker process or not
  val port = scala.util.Properties.envOrElse("PORT", null)

  def isWorker: Boolean = { port == null || port.equals("0") }

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

    Model.init()

    if (isWorker) {
      Scheduler.scheduleMethods("jobs")
    }
  }

  override def onStop(app: Application) {
    Logger.info("Application shutdown...")

    Model.shutdown()
  }
}

