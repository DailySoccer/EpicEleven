package model;

import com.google.common.collect.ImmutableList;
import org.joda.money.CurrencyUnit;
import org.joda.money.Money;
import utils.MoneyUtils;

import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class Prizes {
    public PrizeType prizeType;
    public int maxEntries;
    public Money prizePool;
    // public List<Float> multipliers = new ArrayList<>();
    public List<Money> values = new ArrayList<>();

    private Prizes() {
    }

    private Prizes(PrizeType prizeType, int maxEntries, Money prizePool) {
        this.prizeType = prizeType;
        this.maxEntries = maxEntries;
        this.prizePool = prizePool;
        // this.multipliers = getMultipliers(prizeType, maxEntries);

        this.values = getPrizesApplyingMultipliers(prizePool, getMultipliers(prizeType, maxEntries));
    }

    public Money getValue(int position) {
        Money ret;
        if (prizeType.equals(PrizeType.FIFTY_FIFTY)) {
            ret = (position < (maxEntries / 2)) ? values.get(0) : Money.zero(prizePool.getCurrencyUnit());
        }
        else {
            ret = (position < values.size()) ? values.get(position) : Money.zero(prizePool.getCurrencyUnit());
        }
        return ret;
    }

    public List<Money> getAllValues() {
        List<Money> ret = new ArrayList<>();

        if (prizeType == PrizeType.FIFTY_FIFTY) {
            for (int i=0; i<maxEntries / 2; i++) {
                ret.add(values.get(0));
            }
        }
        else {
            ret = values;
        }

        return ret;
    }

    public static Prizes findOne(Contest contest) {
        return findOne(contest.prizeType, contest.maxEntries, contest.getPrizePool());
    }

    public static Prizes findOne(PrizeType prizeType, int maxEntries, Money prizePool) {
        return new Prizes(prizeType, maxEntries, prizePool);
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
        return contest.prizeType.toString() + "_" + contest.maxEntries + "_" + contest.entryFee;
    }

    static private List<Float> getMultipliers(PrizeType prizeType, int maxEntries) {
        List<Float> multipliers = new ArrayList<>();

        if (prizeType == PrizeType.WINNER_TAKES_ALL) {
            multipliers.add(1f);
        }
        else if (prizeType == PrizeType.TOP_3_GET_PRIZES) {
            switch(maxEntries) {
                case 3: multipliers = ImmutableList.of(0.62963f, 0.37037f); break;
                case 4: multipliers = ImmutableList.of(0.44444f,0.33333f,0.22222f); break;
                case 5: multipliers = ImmutableList.of(0.44444f,0.33333f,0.22222f); break;
                case 6: multipliers = ImmutableList.of(0.48148f,0.31481f,0.2037f); break;
                case 7: multipliers = ImmutableList.of(0.50794f,0.31746f,0.1746f); break;
                case 8: multipliers = ImmutableList.of(0.5f,0.30556f,0.19444f); break;
                case 9: multipliers = ImmutableList.of(0.49383f,0.30864f,0.19753f); break;
                case 10: multipliers = ImmutableList.of(0.5f,0.3f,0.2f); break;
                case 11: multipliers = ImmutableList.of(0.50505f,0.30303f,0.19192f); break;
                case 12: multipliers = ImmutableList.of(0.5f,0.2963f,0.2037f); break;
                case 13: multipliers = ImmutableList.of(0.50427f,0.2906f,0.20513f); break;
                case 14: multipliers = ImmutableList.of(0.50794f,0.28571f,0.20635f); break;
                case 15: multipliers = ImmutableList.of(0.5037f,0.28889f,0.20741f); break;
                case 16: multipliers = ImmutableList.of(0.50694f,0.28472f,0.20833f); break;
                case 17: multipliers = ImmutableList.of(0.5098f,0.28758f,0.20261f); break;
                case 18: multipliers = ImmutableList.of(0.50617f,0.2963f,0.19753f); break;
                case 19: multipliers = ImmutableList.of(0.50292f,0.30409f,0.19298f); break;
                case 20: multipliers = ImmutableList.of(0.5f,0.31111f,0.18889f); break;

                default:
                    multipliers = ImmutableList.of(0.5f,0.3f,0.2f);
            }
        }
        else if (prizeType == PrizeType.TOP_THIRD_GET_PRIZES) {
            switch (maxEntries) {
                case 3: multipliers = ImmutableList.of(1f); break;
                case 4: multipliers = ImmutableList.of(0.72222f,0.27778f); break;
                case 5: multipliers = ImmutableList.of(0.68889f,0.31111f); break;
                case 6: multipliers = ImmutableList.of(0.66667f,0.33333f); break;
                case 7: multipliers = ImmutableList.of(0.65079f,0.34921f); break;
                case 8: multipliers = ImmutableList.of(0.5f,0.30556f,0.19444f); break;
                case 9: multipliers = ImmutableList.of(0.49383f,0.30864f,0.19753f); break;
                case 10: multipliers = ImmutableList.of(0.5f,0.3f,0.2f); break;
                case 11: multipliers = ImmutableList.of(0.45455f,0.28283f,0.16162f,0.10101f); break;
                case 12: multipliers = ImmutableList.of(0.44444f,0.27778f,0.16667f,0.11111f); break;
                case 13: multipliers = ImmutableList.of(0.4359f,0.2735f,0.17094f,0.11966f); break;
                case 14: multipliers = ImmutableList.of(0.37302f,0.22222f,0.18254f,0.14286f,0.07937f); break;
                case 15: multipliers = ImmutableList.of(0.37037f,0.22222f,0.18519f,0.14074f,0.08148f); break;
                case 16: multipliers = ImmutableList.of(0.36806f,0.22222f,0.1875f,0.13889f,0.08333f); break;
                case 17: multipliers = ImmutableList.of(0.3268f,0.21569f,0.1634f,0.13072f,0.09804f,0.06536f); break;
                case 18: multipliers = ImmutableList.of(0.33333f,0.22222f,0.16049f,0.12963f,0.09259f,0.06173f); break;
                case 19: multipliers = ImmutableList.of(0.33333f,0.22222f,0.15789f,0.12865f,0.09357f,0.06433f); break;
                case 20: multipliers = ImmutableList.of(0.33333f,0.22222f,0.15556f,0.12222f,0.1f,0.06667f); break;

                default:
                    multipliers = ImmutableList.of(0f);
            }
        }
        else if (prizeType == PrizeType.FIFTY_FIFTY) {
            float mid = maxEntries / 2;
            if (mid > 0f) {
                multipliers  = ImmutableList.of(1f / mid);
            }
        }

        return multipliers;
    }

    static private List<Money> getPrizesApplyingMultipliers(Money prizePool, List<Float> multipliers) {
        List<Money> prizes = new ArrayList<>();

        if (multipliers.size() > 1) {
            Money resto = prizePool;
            for (int i = 0; i < multipliers.size(); i++) {
                Money premio = prizePool.multipliedBy(multipliers.get(i), RoundingMode.HALF_UP);
                // premio = Math.round(premio * 2) * 0.5f;
                if (premio.isGreaterThan(resto)) {
                    premio = resto;
                }
                if (i == multipliers.size() - 1) {
                    if (resto.isGreaterThan(premio)) {
                        premio = resto;
                    }
                }

                if (premio.isPositiveOrZero()) {
                    prizes.add(premio);
                    resto = resto.minus(premio);
                }
            }
        }
        else if (multipliers.size() > 0) {
            prizes.add(prizePool.multipliedBy(multipliers.get(0), RoundingMode.HALF_UP));
        }

        return prizes;
    }

    static public Money getPool(CurrencyUnit currencyUnit, Money entryFee, int maxEntries, float prizeMultiplier) {
        Money money = Money.zero(currencyUnit);
        return money.plus(entryFee.getAmount()).multipliedBy(maxEntries * prizeMultiplier, RoundingMode.HALF_UP);
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