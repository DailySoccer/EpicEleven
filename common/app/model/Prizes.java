package model;

import java.util.*;
import com.google.common.collect.ImmutableList;

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
            prizes.addAll(getPrizesApplyingMultipliers(prizePool, ImmutableList.of(0.5f, 0.3f, 0.2f)));
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
            prizes.add(prizePool / mid);
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

    static private List<Integer> getPrizesApplyingMultipliers(int prizePool, List<Float> multipliers) {
        List<Integer> prizes = new ArrayList<>();

        float resto = prizePool;
        for (int i=0; i<multipliers.size(); i++) {
            float premio = multipliers.get(i) * prizePool;
            // premio = Math.round(premio * 2) * 0.5f;
            premio = Math.round(premio+0.5f);
            if (premio > resto) {
                premio = resto;
            }
            if (i == multipliers.size() - 1) {
                if (resto > premio) {
                    premio = resto;
                }
            }

            prizes.add((int)premio);
            resto -= premio;
        }

        return prizes;
    }
}

/*
TABLAS DE PREMIOS DE FANDUEL

{
 "winner": {
    "3":[1],
    "4":[1],
    "5":[1],
    "6":[1],
    "7":[1],
    "8":[1],
    "9":[1],
    "10":[1],
    "11":[1],
    "12":[1],
    "13":[1],
    "14":[1],
    "15":[1],
    "16":[1],
    "17":[1],
    "18":[1],
    "19":[1],
    "20":[1]
 },
 "top3": {
    "3":[0.62963,0.37037],
    "4":[0.38889,0.33333,0.27778],
    "5":[0.44444,0.33333,0.22222],
    "6":[0.48148,0.31481,0.2037],
    "7":[0.50794,0.31746,0.1746],
    "8":[0.5,0.30556,0.19444],
    "9":[0.49383,0.30864,0.19753],
    "10":[0.5,0.3,0.2],
    "11":[0.50505,0.30303,0.19192],
    "12":[0.5,0.2963,0.2037],
    "13":[0.50427,0.2906,0.20513],
    "14":[0.50794,0.28571,0.20635],
    "15":[0.5037,0.28889,0.20741],
    "16":[0.50694,0.28472,0.20833],
    "17":[0.5098,0.28758,0.20261],
    "18":[0.50617,0.2963,0.19753],
    "19":[0.50292,0.30409,0.19298],
    "20":[0.5,0.31111,0.18889]
 },
 "3rd":{
    "3":[1],
    "4":[0.72222,0.27778],
    "5":[0.68889,0.31111],
    "6":[0.66667,0.33333],
    "7":[0.65079,0.34921],
    "8":[0.5,0.30556,0.19444],
    "9":[0.49383,0.30864,0.19753],
    "10":[0.5,0.3,0.2],
    "11":[0.45455,0.28283,0.16162,0.10101],
    "12":[0.44444,0.27778,0.16667,0.11111],
    "13":[0.4359,0.2735,0.17094,0.11966],
    "14":[0.37302,0.22222,0.18254,0.14286,0.07937],
    "15":[0.37037,0.22222,0.18519,0.14074,0.08148],
    "16":[0.36806,0.22222,0.1875,0.13889,0.08333],
    "17":[0.3268,0.21569,0.1634,0.13072,0.09804,0.06536],
    "18":[0.33333,0.22222,0.16049,0.12963,0.09259,0.06173],
    "19":[0.33333,0.22222,0.15789,0.12865,0.09357,0.06433],
    "20":[0.33333,0.22222,0.15556,0.12222,0.1,0.06667]
 }
}

Algoritmo "codificado":

commissionFactor:10
n=this.get("selectedPrivateLeagueSizeId");var j=parseInt(n,10)
p=this.get("selectedPrivateLeagueEntryFee");var g=parseFloat(p)
var h=(1-(this.commissionFactor*0.01))*g;var d=j*h;var m=d
for(k=0;k<l.length;k++)
    o=l[k]*d;o=Math.round(o*2)*0.5;if(o>m){o=m}if(k+1==l.length){if(m>o){o=m}}m-=o

Algoritmo "interpretado":

commissionFactor:10
max_entries (j) = selectedPrivateLeagueSizeId
entry_fee (g) = selectedPrivateLeagueEntryFee

entry_fee_con_comision (h) = (1-(commissionFactor*0.01))*entry_fee;

dinero (d) = max_entries * entry_fee_con_comision
resto (m) = dinero

para cada multiplicador (l[k])
    premio (o) = multiplicador * dinero
    premio = Math.round(premio * 2) * 0.5
    if (premio > resto) {
      premio = resto;
    }
    if (es_ultimo_premio) {
      if (resto > premio) {
        premio = resto;
      }
    }
    resto -= premio

*/