package model;

import java.util.*;

public class Prizes {
    public PrizeType prizeType;
    public int maxEntries;
    public int prizePool;
    public List<Integer> values = new ArrayList<>();

    private Prizes() {
    }

    private Prizes(PrizeType prizeType, int maxEntries, int prizePool, List<Integer> values) {
        this.prizeType = prizeType;
        this.maxEntries = maxEntries;
        this.prizePool = prizePool;
        this.values = values;
    }

    public static Prizes findOne(Contest contest) {
        return findOne(contest.prizeType, contest.maxEntries, contest.getPrizePool());
    }

    public static Prizes findOne(PrizeType prizeType, int maxEntries, int prizePool) {
        List<Integer> prizes = new ArrayList<>();

        if (prizeType.equals(PrizeType.FREE)) {

        } else if (prizeType.equals(PrizeType.WINNER_TAKES_ALL)) {
            prizes.add(prizePool);
        } else if (prizeType.equals(PrizeType.TOP_3_GET_PRIZES)) {
            prizes.add((int) (prizePool * 0.5f));
            prizes.add((int) (prizePool * 0.3f));
            prizes.add((int) (prizePool * 0.2));
        } else if (prizeType.equals(PrizeType.TOP_THIRD_GET_PRIZES)) {
            // A cuantos repartiremos premios?
            int third = maxEntries / 3;

            // Para hacer el reparto proporcional asignaremos puntos inversamente a la posición
            // Más puntos cuanto más baja su posición. Para repartir a "n" usuarios: 1º = (n) pts / 2º = (n-1) pts / 3º = (n-2) pts / ... / nº = 1 pts

            // Averiguar los puntos totales a repartir para saber cuánto vale el punto: n * (n+1) / 2  (suma el valor de "n" numeros)
            int totalPoints = third * (third + 1) / 2;
            int prizeByPoint = prizePool / totalPoints;

            // A cada posición le damos el premio (sus puntos se corresponden con su posición "invertida": p.ej. para repartir a 6 usuarios: el 1º tiene 6 puntos, el 2º tiene 5 puntos, etc)
            int totalPrize = prizePool;
            for (int i = third; i > 0; i--) {
                int prize = prizeByPoint * i;
                prizes.add(prize);
                totalPrize -= prize;
            }
            // Si queda algo, ¿se lo damos al primero?
            if (totalPrize > 0) {
                prizes.set(0, prizes.get(0) + totalPrize);
            }
        } else if (prizeType.equals(PrizeType.FIFTY_FIFTY)) {
            int mid = maxEntries / 2;
            for (int i = 0; i < mid; i++) {
                prizes.add(prizePool / mid);
            }
        }

        return new Prizes(prizeType, maxEntries, prizePool, prizes);
    }

    public static List<Prizes> findAllByContests(List<Contest>... elements) {
        Set<String> prizesIncluded = new HashSet<>();

        List<Prizes> prizes = new ArrayList<>();

        for (List<Contest> contests : elements) {
            for (Contest contest : contests) {
                String key = getKeyFromContest(contest);
                if (!prizesIncluded.contains(key)) {
                    prizesIncluded.add(key);

                    prizes.add(Prizes.findOne(contest));
                }
            }
        }

        return prizes;
    }

    private static String getKeyFromContest(Contest contest) {
        return contest.prizeType.toString() + "_" + contest.maxEntries + "_" + contest.getPrizePool();
    }
}
