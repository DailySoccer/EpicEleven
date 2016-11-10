package model.rewards;

import com.fasterxml.jackson.annotation.JsonView;
import model.GlobalDate;
import model.JsonViews;
import org.joda.money.Money;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import play.Logger;
import utils.MoneyUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

public class DailyRewards {
    public static final long HOURS_TO_RECEIVE_DAILYREWARDS = 20;
    public static final long HOURS_TO_RESET = 48;

    @JsonView(JsonViews.NotForClient.class)
    public Date lastDate;               // La última fecha en la que se recompensó por entrar

    @JsonView(JsonViews.NotForClient.class)
    public int consecutiveDays = -1;    // Número de días consecutivos en los que el usuario está volviendo a la aplicación

    public List<Reward> rewards = new ArrayList<>();

    public DailyRewards() {
    }

    public boolean update() {
        boolean updated = false;

        long hours = hoursFromLastDate();
        if (hours >= HOURS_TO_RECEIVE_DAILYREWARDS) {

            consecutiveDays = (hours >= HOURS_TO_RESET) ? 1 : consecutiveDays + 1;

            // TODO: Entregar una recompensa
            rewards.add(new GoldReward(Money.of(MoneyUtils.CURRENCY_GOLD, consecutiveDays), consecutiveDays));

            lastDate = GlobalDate.getCurrentDate();

            updated = true;

            printDebug();
        }

        return updated;
    }

    public void printDebug() {
        List<String> rewardsStr = rewards.stream().map( Reward::debug ).collect(Collectors.toList());

        Logger.debug("dailyRewards: lastDate: {} consecutiveDays: {} rewards: {}",
                GlobalDate.formatDate(lastDate), consecutiveDays, String.join(", ", rewardsStr));
    }

    private long hoursFromLastDate() {
        // Asumimos que ha pasado mucho tiempo desde la última vez
        long hours = HOURS_TO_RESET;

        if (lastDate != null) {
            // Calculamos cuántas horas han pasado
            DateTime lastDateTime = new DateTime(lastDate);
            DateTime now = new DateTime(GlobalDate.getCurrentDate());
            Duration duration = new Duration(lastDateTime, now);

            hours = duration.getStandardHours();
        }

        return hours;
    }
}
