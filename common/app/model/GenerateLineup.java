package model;

import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import utils.ListUtils;

import java.util.*;
import java.util.stream.Collectors;

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

        // Devolvemos la alineación ordenada por Goalkeeper, Defense, Middle y Forward
        List<TemplateSoccerPlayer> result = new ArrayList<>();
        result.addAll( lineup.stream().filter( templateSoccerPlayer -> templateSoccerPlayer.fieldPos.equals(FieldPos.GOALKEEPER) ).collect(Collectors.toList()) );
        result.addAll( lineup.stream().filter( templateSoccerPlayer -> templateSoccerPlayer.fieldPos.equals(FieldPos.DEFENSE) ).collect(Collectors.toList()) );
        result.addAll( lineup.stream().filter( templateSoccerPlayer -> templateSoccerPlayer.fieldPos.equals(FieldPos.MIDDLE) ).collect(Collectors.toList()) );
        result.addAll( lineup.stream().filter( templateSoccerPlayer -> templateSoccerPlayer.fieldPos.equals(FieldPos.FORWARD) ).collect(Collectors.toList()) );
        return result;
    }

    // Una intento rapido de hacerlo un poco mejor.
    static public List<TemplateSoccerPlayer> quickAndDirty(List<TemplateSoccerPlayer> soccerPlayers, int salaryCap) {
        List<TemplateSoccerPlayer> lineup = new ArrayList<>();

        sortByFantasyPoints(soccerPlayers, true);

        List<TemplateSoccerPlayer> forwards = filterByPosition(soccerPlayers, FieldPos.FORWARD);
        List<TemplateSoccerPlayer> goalkeepers = filterByPosition(soccerPlayers, FieldPos.GOALKEEPER);
        List<TemplateSoccerPlayer> middles = filterByPosition(soccerPlayers, FieldPos.MIDDLE);
        List<TemplateSoccerPlayer> defenses = filterByPosition(soccerPlayers, FieldPos.DEFENSE);

        // Dos delanteros entre los 8 mejores
        for (int c = 0; c < 2; ++c) {
            lineup.add(forwards.remove(_rand.nextInt(Math.min(8, forwards.size()))));
        }

        // Un portero de la mitad para abajo
        lineup.add(goalkeepers.get(_rand.nextInt(goalkeepers.size() / 2) + (goalkeepers.size() / 2)));

        // 4 y 4 cogidos al azar entre los mejores sin pasarnos del salario medio restante
        selectSoccerPlayers(lineup, middles, 4, salaryCap);
        selectSoccerPlayers(lineup, defenses, 4, salaryCap);

        // Devolvemos la alineación ordenada por Goalkeeper, Defense, Middle y Forward
        List<TemplateSoccerPlayer> result = new ArrayList<>();
        result.addAll( lineup.stream().filter( templateSoccerPlayer -> templateSoccerPlayer.fieldPos.equals(FieldPos.GOALKEEPER) ).collect(Collectors.toList()) );
        result.addAll( lineup.stream().filter( templateSoccerPlayer -> templateSoccerPlayer.fieldPos.equals(FieldPos.DEFENSE) ).collect(Collectors.toList()) );
        result.addAll( lineup.stream().filter( templateSoccerPlayer -> templateSoccerPlayer.fieldPos.equals(FieldPos.MIDDLE) ).collect(Collectors.toList()) );
        result.addAll( lineup.stream().filter( templateSoccerPlayer -> templateSoccerPlayer.fieldPos.equals(FieldPos.FORWARD) ).collect(Collectors.toList()) );
        return result;
    }


    static private void selectSoccerPlayers(List<TemplateSoccerPlayer> lineup, List<TemplateSoccerPlayer> from, int howMany, int salaryCap) {
        for (int c = 0; c < howMany; ++c) {
            int averageRemainingSalary = (salaryCap - sumSalary(lineup)) / (11 - lineup.size());

            List<TemplateSoccerPlayer> filtered = filterBySalary(from, 0, averageRemainingSalary);
            if (!filtered.isEmpty()) {
                sortByFantasyPoints(filtered, true);

                // Entre los 8 mejores... esto podia ser un parametro para ajustar mas, por ejemplo dependiendo del numero de maxEntries
                TemplateSoccerPlayer selected = filtered.get(_rand.nextInt(Math.min(8, filtered.size())));
                lineup.add(selected);
                from.remove(selected);
            }
        }
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

    static private void sortByFantasyPoints(List<TemplateSoccerPlayer> sps, boolean desc) {
        if (desc) {
            Collections.sort(sps, new Comparator<TemplateSoccerPlayer>() {
                @Override
                public int compare(TemplateSoccerPlayer o1, TemplateSoccerPlayer o2) {
                    return o2.fantasyPoints - o1.fantasyPoints;
                }
            });
        }
        else {
            Collections.sort(sps, new Comparator<TemplateSoccerPlayer>() {
                @Override
                public int compare(TemplateSoccerPlayer o1, TemplateSoccerPlayer o2) {
                    return o1.fantasyPoints - o2.fantasyPoints;
                }
            });
        }
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
