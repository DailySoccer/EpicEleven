package pages;

import static org.fest.assertions.Assertions.*;
import org.fluentlenium.core.FluentPage;
import org.openqa.selenium.WebDriver;
import static org.fluentlenium.core.filter.FilterConstructor.*;

public class HomePage extends FluentPage {

    public HomePage(WebDriver webDriver, int port, String baseUrl) {
        super(webDriver);
        withDefaultUrl(baseUrl);
    }

    @Override
    public String getUrl() { return ""; }

    @Override
    public void isAt() {
        assertThat(title()).isEqualTo("Daily Soccer");
    }

    public void clickOnLogin() {
        find(".navbar-right").find("li a", withText("Login")).click();
    }

    public void clickOnSignUp() {
        find(".navbar-right").find("li a", withText("Join")).click();
    }
}
