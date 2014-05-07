package pages;

import org.fluentlenium.core.FluentPage;
import org.openqa.selenium.WebDriver;

import static org.fest.assertions.Assertions.assertThat;
import static org.fluentlenium.core.filter.FilterConstructor.*;
import org.fluentlenium.core.domain.FluentWebElement;

public class SignUpPage extends FluentPage {

    private FluentWebElement joinNow;

    public SignUpPage(WebDriver webDriver, int port, String baseUrl) {
        super(webDriver);
        withDefaultUrl(baseUrl);
    }

    public final static String URL =  "/#/join";

    @Override
    public String getUrl() {
        return URL;
    }

    @Override
    public void isAt() {
        assertThat(title()).isEqualTo("Daily Soccer");
        assertThat($("legend", withText("Start Playing")));
        assertThat($(".form-login", withName("Your first name")));
        assertThat($(".form-login", withName("Your last name")));
        assertThat($(".form-login", withName("Email")));
        assertThat($(".form-login", withName("NickName")));
        assertThat($(".form-login", withName("Password")));
    }

    public void fillAndSubmitForm(String... paramsOrdered) {
        fill("input").with(paramsOrdered);
        click("#joinNow");
        // joinNow.click();
    }
}
