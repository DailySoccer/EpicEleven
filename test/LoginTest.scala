import org.specs2.mutable._
import org.specs2.runner._
import org.junit.runner._

import play.api.test._
import play.api.test.Helpers._

import org.fluentlenium.core.filter.FilterConstructor._
import java.util.concurrent.TimeUnit
import play.Logger

import pages._

class LoginTest extends PlaySpecification {

  "Application" should {

    "load successfully" in new WithBrowser (webDriver = FIREFOX, port = 3333) {
      val homePage = new HomePage(browser.getDriver, port, browser.getBaseUrl)
      homePage.go
      homePage.isAt
    }

    "select sign up in navigation menu" in new WithBrowser (webDriver = FIREFOX, port = 3333) {
      val homePage = new HomePage(browser.getDriver, port, browser.getBaseUrl)
      homePage.go
      homePage.clickOnSignUp

      browser.await().atMost(10, TimeUnit.SECONDS).untilPage().isLoaded()
      browser.url must equalTo(SignUpPage.URL)
    }

    "select login in navigation menu" in new WithBrowser (webDriver = FIREFOX, port = 3333) {
      val homePage = new HomePage(browser.getDriver, port, browser.getBaseUrl)
      homePage.go
      homePage.clickOnLogin

      browser.await().atMost(10, TimeUnit.SECONDS).untilPage().isLoaded()
      browser.url must equalTo(LoginPage.URL)
    }

    "sign up user" in new WithBrowser (webDriver = FIREFOX, port = 3333) {
      val signUpPage = new SignUpPage(browser.getDriver, port, browser.getBaseUrl)
      signUpPage.go
      signUpPage.isAt

      browser.await().atMost(10, TimeUnit.SECONDS).untilPage().isLoaded()
      signUpPage.fillAndSubmitForm("firstName", "lastName", "test@test.com", "nick", "private")
      // browser.takeScreenShot
    }

    "login user" in new WithBrowser (webDriver = FIREFOX, port = 3333) {
      val loginPage = new LoginPage(browser.getDriver, port, browser.getBaseUrl)
      loginPage.go
      loginPage.isAt

      browser.await().atMost(10, TimeUnit.SECONDS).untilPage().isLoaded()
      loginPage.fillAndSubmitForm("test@test.com", "private")
      // browser.takeScreenShot
    }

  }
}