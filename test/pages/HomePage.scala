package pages

class HomePage extends SharedPage {
  val TITLE   = "Daily Soccer"
  val LOGIN_TEXT  = "Login"
  val SIGNUP_TEXT = "Join"

  val url = SharedPage.baseUrl

  def open = {
    go to url
    this
  }

  def isAt = {
    pageTitle should be (TITLE)
    this
  }

  def clickOnLogin = {
    click on linkText(LOGIN_TEXT)
    new LoginPage().isAt
  }

  def clickOnSignUp = {
    click on linkText(SIGNUP_TEXT)
    new SignUpPage().isAt
  }
}
