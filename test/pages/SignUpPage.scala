package pages

class SignUpPage extends SharedPage {
  val TITLE   = "Daily Soccer"
  val LEGEND  = "Start Playing"
  val FORM_FIRST_NAME = "Your first name"
  val FORM_LAST_NAME  = "Your last name"
  val FORM_EMAIL      = "Email"
  val FORM_NICKNAME   = "NickName"
  val FORM_PASSWORD   = "Password"

  val url = SharedPage.baseUrl + "/#/join"

  def open = {
    go to url
    this
  }

  def isAt = {
    pageTitle should be (TITLE)
    eventually { find(tagName("legend")).get.text should be (LEGEND) }
    find(name(FORM_FIRST_NAME)) should be ('defined)
    find(name(FORM_LAST_NAME)) should be ('defined)
    find(name(FORM_EMAIL)) should be ('defined)
    find(name(FORM_NICKNAME)) should be ('defined)
    find(name(FORM_PASSWORD)) should be ('defined)

    find(cssSelector("form input[name='Your first name']")) should be ('defined)
    find(cssSelector("form input[name='Your last name']")) should be ('defined)
    find(cssSelector("form input[name='Email']")) should be ('defined)
    find(cssSelector("form input[name='NickName']")) should be ('defined)
    find(cssSelector("form input[name='Password']")) should be ('defined)

    this
  }

  def fillAndSubmitForm(params: Map[String,String]) {
    textField(FORM_FIRST_NAME).value = params("firstName")
    textField(FORM_LAST_NAME).value = params("lastName")
    emailField(FORM_EMAIL).value = params("email")
    textField(FORM_NICKNAME).value = params("nick")
    pwdField(FORM_PASSWORD).value = params("password")
    // submit
  }

  def fillAndSubmitForm(paramsOrdered: String*) {
    findAll(tagName("input"))
  }
}