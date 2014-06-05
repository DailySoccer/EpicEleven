import pages._
import org.scalatestplus.play._
import org.scalatest._
import org.scalatest.{GivenWhenThen, ShouldMatchers}
import org.openqa.selenium._
import org.openqa.selenium.firefox._
import org.openqa.selenium.chrome._
import org.openqa.selenium.remote.{CapabilityType, DesiredCapabilities, RemoteWebDriver}

class LoginTest extends SharedTest {

  before {
  }

  "User" must {
    "go to home" in {
      goToHomePage
    }

    /*
    "home to sign up" in {
      goToHomePage.clickOnSignUp
    }
    */

    "home to login" in {
      goToHomePage.clickOnLogin
    }

    "signup" in {
      goToSignUpPage.fillAndSubmitForm(Map(
        "firstName" -> "First",
        "lastName" -> "Last",
        "email" -> "test@test.com",
        "nick" -> "nick",
        "password" -> "private"
      ))
    }

    "login" in {
      goToLoginPage.fillAndSubmitForm(Map(
        "email" -> "test@test.com",
        "password" -> "private"
      ))
    }

    /*
    "bing" in {
      go to "http://www.bing.com"
      textField(cssSelector("#sb_form_q")).value = "FluentLenium"
      submit
      pageTitle must include ("FluentLenium")
    }

    "amazon" in {
      go to "http://www.amazon.com/"
      pageTitle must be ("Amazon.com: Online Shopping for Electronics, Apparel, Computers, Books, DVDs & more")
    }
    */
  }

  after {
  }
}
