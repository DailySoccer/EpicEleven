package model.rewards;

import com.fasterxml.jackson.annotation.JsonView;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import model.GlobalDate;
import model.JsonViews;
import org.bson.types.ObjectId;
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
    public Date lastDate;               // La última fecha en la que el usuario recogió la recompensa

    public int consecutiveDays = -1;    // Número de días consecutivos en los que el usuario está volviendo a la aplicación

    public List<Reward> rewards = new ArrayList<>();

    public DailyRewards() {
    }

    public boolean update() {
        boolean updated = false;

        long hours = hoursFromLastDate();
        if (hours >= HOURS_TO_RECEIVE_DAILYREWARDS) {

            if (hours >= HOURS_TO_RESET) {
                rewards = createRewards();
                consecutiveDays = 1;

                updated = true;
            }
            // Si ha recogido la anterior recompensa
            else if (rewards.get(consecutiveDays - 1).pickedUp) {

                // Si ha recogido todas las recompensas
                if (consecutiveDays >= rewards.size()) {
                    // Volvemos a empezar
                    rewards = createRewards();
                    consecutiveDays = 1;
                } else {
                    // Le ofrecemos la siguiente recompensa
                    consecutiveDays++;
                }

                updated = true;
            }
        }

        if (updated) {
            // Inicializamos la fecha con la de generación de los premios,
            // posteriormente cuando el usuario recoja el premio la volveremos a cambiar
            // (para que tomemos como referencia el momento "real" en el que recogió el premio)
            lastDate = GlobalDate.getCurrentDate();

            printDebug();
        }

        return updated;
    }

    public void printDebug() {
        List<String> rewardsStr = rewards.stream().map( Reward::debug ).collect(Collectors.toList());

        Logger.debug("dailyRewards: lastDate: {} consecutiveDays: {} rewards: {}",
                GlobalDate.formatDate(lastDate), consecutiveDays, String.join(", ", rewardsStr));
    }

    public Reward lastReward() {
        return rewards.get(consecutiveDays - 1);
    }

    public void removeReward(ObjectId rewardId) {
        rewards = rewards.stream().filter(reward -> !reward.rewardId.equals(rewardId)).collect(Collectors.toList());
    }

    private List<Reward> createRewards() {
        return ImmutableList.of(
                new GoldReward(Money.of(MoneyUtils.CURRENCY_GOLD, 1)),
                new GoldReward(Money.of(MoneyUtils.CURRENCY_GOLD, 1)),
                new GoldReward(Money.of(MoneyUtils.CURRENCY_GOLD, 2)),
                new GoldReward(Money.of(MoneyUtils.CURRENCY_GOLD, 2)),
                new GoldReward(Money.of(MoneyUtils.CURRENCY_GOLD, 3))
        );
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
