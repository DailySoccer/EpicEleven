package model;

import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import utils.ListUtils;

import java.util.*;

public class GenerateLineup {

    static public List<TemplateSoccerPlayer> sillyWay(List<TemplateSoccerPlayer> soccerPlayers, int salaryCap) {
        List<TemplateSoccerPlayer> lineup = new ArrayList<>();

        sortBySalary(soccerPlayers);

        List<TemplateSoccerPlayer> goalkeepers = filterByPosition(soccerPlayers, FieldPos.GOALKEEPER);
        lineup.add(goalkeepers.get(0));

        List<TemplateSoccerPlayer> middles = filterByPosition(soccerPlayers, FieldPos.MIDDLE);
        List<TemplateSoccerPlayer> defenses = filterByPosition(soccerPlayers, FieldPos.DEFENSE);

        for (int c = 0; c < 4; ++c) {
            lineup.add(middles.get(c));
            lineup.add(defenses.get(c));
        }

        List<TemplateSoccerPlayer> forwards = filterByPosition(soccerPlayers, FieldPos.FORWARD);
        for (int c = 0; c < 2; ++c) {
            lineup.add(forwards.get(c));
        }

        return lineup;
    }

    // Una intento rapido de hacerlo un poco mejor. Por ejemplo en el mundial no funciona pq no puede encontrar
    // futbolistas que satisfagan las condiciones. Sin embargo, en los concursos actuales de La Liga/Premier, hasta ahora
    // siempre ha encontrado
    static public List<TemplateSoccerPlayer> quickAndDirty(List<TemplateSoccerPlayer> soccerPlayers, int salaryCap) {
        List<TemplateSoccerPlayer> lineup = new ArrayList<>();

        sortByFantasyPoints(soccerPlayers);

        List<TemplateSoccerPlayer> forwards = filterByPosition(soccerPlayers, FieldPos.FORWARD);
        List<TemplateSoccerPlayer> goalkeepers = filterByPosition(soccerPlayers, FieldPos.GOALKEEPER);
        List<TemplateSoccerPlayer> middles = filterByPosition(soccerPlayers, FieldPos.MIDDLE);
        List<TemplateSoccerPlayer> defenses = filterByPosition(soccerPlayers, FieldPos.DEFENSE);

        // Dos delanteros entre los 8 mejores
        for (int c = 0; c < 2; ++c) {
            int next = _rand.nextInt(Math.min(8, forwards.size()));
            lineup.add(forwards.get(next));
        }

        // Un portero de la mitad para abajo
        lineup.add(goalkeepers.get(_rand.nextInt(goalkeepers.size() / 2) + (goalkeepers.size() / 2)));

        // Medios y defensas repartidos por igual, buscamos varias veces partiendo desde la media y aumentado de 100 en
        // 100 por debajo
        int averageRemainingSalary = (salaryCap - sumSalary(lineup)) / 8;
        int diff = -1;

        for (int tryCounter = 0; tryCounter < 20; ++tryCounter) {
            List<TemplateSoccerPlayer> tempLineup = new ArrayList<>(lineup);

            int maxSal = averageRemainingSalary + 500;
            int minSal = averageRemainingSalary - ((tryCounter+1)*100);
            List<TemplateSoccerPlayer> middlesBySalary = filterBySalary(middles, minSal, maxSal);
            List<TemplateSoccerPlayer> defensesBySalary = filterBySalary(defenses, minSal, maxSal);

            if (middlesBySalary.size() < 4 || defensesBySalary.size() < 4) {
                continue;
            }

            for (int c = 0; c < 4; ++c) {
                int next = _rand.nextInt(Math.min(8, middlesBySalary.size()));
                tempLineup.add(middlesBySalary.remove(next));
                next = _rand.nextInt(Math.min(8, defensesBySalary.size()));
                tempLineup.add(defensesBySalary.remove(next));
            }

            diff = salaryCap - sumSalary(tempLineup);

            if (tempLineup.size() == 11 && diff >= 0) {
                lineup = tempLineup;
                break;
            }
        }

        return lineup;
    }


    static public int sumSalary(List<TemplateSoccerPlayer> sps) {
        int ret = 0;
        for (TemplateSoccerPlayer sp : sps) {
            ret += sp.salary;
        }
        return ret;
    }

    static private List<TemplateSoccerPlayer> filterByPosition(List<TemplateSoccerPlayer> sps, final FieldPos fp) {
        return ListUtils.asList(Collections2.filter(sps, new Predicate<TemplateSoccerPlayer>() {
            @Override
            public boolean apply(TemplateSoccerPlayer templateSoccerPlayer) {
                return (templateSoccerPlayer != null && templateSoccerPlayer.fieldPos == fp);
            }
        }));
    }

    static private List<TemplateSoccerPlayer> filterBySalary(List<TemplateSoccerPlayer> sps, final int salMin, final int salMax) {
        return ListUtils.asList(Collections2.filter(sps, new Predicate<TemplateSoccerPlayer>() {
            @Override
            public boolean apply(TemplateSoccerPlayer templateSoccerPlayer) {
                return (templateSoccerPlayer != null && templateSoccerPlayer.salary >= salMin && templateSoccerPlayer.salary <= salMax);
            }
        }));
    }

    static private void sortByFantasyPoints(List<TemplateSoccerPlayer> sps) {
        Collections.sort(sps, new Comparator<TemplateSoccerPlayer>() {
            @Override
            public int compare(TemplateSoccerPlayer o1, TemplateSoccerPlayer o2) {
                return o1.fantasyPoints - o2.fantasyPoints;
            }
        });
    }

    static private void sortBySalary(List<TemplateSoccerPlayer> sps) {
        Collections.sort(sps, new Comparator<TemplateSoccerPlayer>() {
            @Override
            public int compare(TemplateSoccerPlayer o1, TemplateSoccerPlayer o2) {
                return o1.salary - o2.salary;
            }
        });
    }

    // Instances of java.util.Random are threadsafe. However, the concurrent use of the same java.util.Random instance
    // across threads may encounter contention and consequent poor performance. Consider instead using ThreadLocalRandom
    // in multithreaded designs.
    static final Random _rand = new Random(System.currentTimeMillis());
}
