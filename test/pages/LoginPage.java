package pages;

import org.fluentlenium.core.FluentPage;
import org.openqa.selenium.WebDriver;

import static org.fest.assertions.Assertions.assertThat;
import static org.fluentlenium.core.filter.FilterConstructor.*;
import org.fluentlenium.core.domain.FluentWebElement;
import org.fluentlenium.core.annotation.AjaxElement;

public class LoginPage extends FluentPage {

    @AjaxElement
    private FluentWebElement login;

    public LoginPage(WebDriver webDriver, int port, String baseUrl) {
        super(webDriver);
        withDefaultUrl(baseUrl);
    }

    public final static String URL =  "/#/login";

    @Override
    public String getUrl() {
        return URL;
    }

    @Override
    public void isAt() {
        assertThat(title()).isEqualTo("Daily Soccer");
        assertThat($("legend", withText("Login")));
        assertThat($(".form-login", withName("Email")));
        assertThat($(".form-login", withName("Password")));
    }

    public void fillAndSubmitForm(String... paramsOrdered) {
        fill("input").with(paramsOrdered);
        click("#login");
        // login.click();
    }
}
