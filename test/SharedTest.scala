import pages._
import org.scalatestplus.play._
import org.scalatest._
import org.scalatest.{GivenWhenThen, ShouldMatchers}
import org.openqa.selenium.remote.{CapabilityType, DesiredCapabilities, RemoteWebDriver}

class SharedTest extends PlaySpec
  with OneServerPerSuite with OneBrowserPerSuite with SauceLabsFactory with BeforeAndAfter with BeforeAndAfterAll {

  override def beforeAll {
    println("Before!")  // start up your web server or whatever
    SharedPage.driver = webDriver
  }

  override def afterAll {
    println("After!")  // shut down the web server
    webDriver.quit
  }

  def goToHomePage : HomePage = {
    val homePage = new HomePage
    homePage.open.isAt
  }

  def goToSignUpPage : SignUpPage = {
    val signupPage = new SignUpPage
    signupPage.open.isAt
  }

  def goToLoginPage : LoginPage = {
    val loginPage = new LoginPage
    loginPage.open.isAt
  }
}
