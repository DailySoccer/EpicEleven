package controllers


import play.api.mvc._
import model.{XMLContest, Model}

/**
 * Created by gnufede on 12/05/14.
 */
class OptaHttpController extends Controller{

  def optaXmlInput = Action(parse.xml) { request =>
    Model.xmlcontests().insert(new XMLContest(xml=request.body, name=___, startDate=___))
  }
}
  /*
(request.body \\ "name" headOption).map(_.text).map { name =>
Ok("Hello " + name)
}.getOrElse {
BadRequest("Missing parameter [name]")
}
*/
