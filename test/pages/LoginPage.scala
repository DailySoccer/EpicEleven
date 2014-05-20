package pages

class LoginPage extends SharedPage {
  val TITLE   = "Daily Soccer"
  val LEGEND  = "Login"
  val FORM_EMAIL    = "Email"
  val FORM_PASSWORD = "Password"

  val url = SharedPage.baseUrl + "/#/login"

  def open = {
    go to url
    this
  }

  def isAt = {
    pageTitle should be (TITLE)
    eventually { find(tagName("legend")).get.text should be (LEGEND) }
    find(name(FORM_EMAIL)) should be ('defined)
    find(name(FORM_PASSWORD)) should be ('defined)

    find(cssSelector("form input[name='Email']")) should be ('defined)
    find(cssSelector("form input[name='Password']")) should be ('defined)

    this
  }

  def fillAndSubmitForm(params: Map[String,String]) {
    emailField(FORM_EMAIL).value = params("email")
    pwdField(FORM_PASSWORD).value = params("password")
    // submit
  }

  def fillAndSubmitForm(paramsOrdered: String*) {
    /*
    textField("Email").value = "email"
    fill("input").`with`(paramsOrdered)
    click("#joinNow")
    */
  }
}
