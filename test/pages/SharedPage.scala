package pages

import org.scalatest.{GivenWhenThen, OptionValues, MustMatchers, Matchers}
import org.scalatest.selenium.WebBrowser
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.openqa.selenium.WebDriver

trait SharedPage extends WebBrowser.Page
  with WebBrowser with Matchers with OptionValues with Eventually with IntegrationPatience {
  implicit val driver : WebDriver = SharedPage.driver
}

object SharedPage {
  var driver  : WebDriver = null
  var baseUrl : String = "http://dailysoccer.herokuapp.com" // "http://localhost:9000"
}
