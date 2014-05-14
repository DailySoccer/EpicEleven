package controllers


import play.api.mvc._
import model.{XMLContest, Model}
import org.joda.time.DateTime
import play.mvc.Results._

/**
 * Created by gnufede on 12/05/14.
 */

class OptaHttpController extends Controller{}
object OptaHttpController extends Controller{

  def optaXmlInput = Action(parse.text(maxLength=1024*1024*4)){ request =>
    Model.xmlcontests.insert(new XMLContest(xml=request.body, headers=request.headers.toSimpleMap,
                                              name=request.headers("x-meta-default-filename"),
                                              startDate=DateTime.now))
    Ok("Yeah")
  }
}
  /*
(request.body \\ "name" headOption).map(_.text).map { name =>
Ok("Hello " + name)
}.getOrElse {
BadRequest("Missing parameter [name]")
}
*/
