package utils;

import com.google.common.collect.ImmutableMap;
import org.bson.types.ObjectId;
import java.util.*;
import jskills.*;
import model.ContestEntry;
import model.User;
import play.Logger;

public class TrueSkillHelper {
    static public final double INITIAL_MEAN = 25.0;
    static public final double INITIAL_SD = INITIAL_MEAN / 3;

    static final double BETA = 10;                             // Distancia entre los 80%-20% (en cadena)
    static final double DYNAMIC_FACTOR = INITIAL_MEAN / 100;   // Volatilidad de subida / bajada
    static final double DRAW = 0.3;
    static final double CONSERVATIVE_FACTOR = 3;

    static public final double CUTOFF = 20;                    // Valor a partir del cual no queremos considerar el partido matchmakingable
    static public final double MULTIPLIER = 100;               // Para mostrarlo al usuario, multiplicamos el MyConservative

    static public boolean IsJustResult(jskills.Rating ratingPlayer1, jskills.Rating ratingPlayer2, int goalsPlayer1, int goalsPlayer2) {
        boolean result = true;

        if (goalsPlayer1 > goalsPlayer2) {
            if (MyConservativeTrueSkill(ratingPlayer1) - MyConservativeTrueSkill(ratingPlayer2) > CUTOFF)
                result = false;
        }
        else
        if (goalsPlayer1 < goalsPlayer2) {
            if (MyConservativeTrueSkill(ratingPlayer2) - MyConservativeTrueSkill(ratingPlayer1) > CUTOFF)
                result = false;
        }

        return result;
    }

    static public double MyConservativeTrueSkill(User.Rating userRating) {
        jskills.Rating rating = new jskills.Rating(userRating.Mean, userRating.StandardDeviation);
        return MyConservativeTrueSkill(rating);
    }

    static public double MyConservativeTrueSkill(jskills.Rating player) {
        return player.getMean() - (CONSERVATIVE_FACTOR * player.getStandardDeviation());
    }

    static public Map<ObjectId, User> RecomputeRatings(List<ContestEntry> contestEntries) {
        Map<ObjectId, User> result = new HashMap<>();
        SkillCalculator calculator = new jskills.trueskill.FactorGraphTrueSkillCalculator();

        List<ITeam> teams = new ArrayList<>();
        Map<ObjectId, IPlayer> players = new HashMap<>();

        Map<ObjectId, Map> evolution = new HashMap<>();

        // Creamos la lista de equipos que se han enfrentado (únicamente compuesto por un usuario)
        for(ContestEntry contestEntry : contestEntries) {
            User user = User.findOne(contestEntry.userId);

            // Asociamos al player del TrueSkill con el usuario
            IPlayer player = new jskills.Player<>(user.userId);
            jskills.Rating rating = new jskills.Rating(user.rating.Mean, user.rating.StandardDeviation);

            // Creamos el equipo con un único player
            ITeam team = new jskills.Team(player, rating);
            teams.add(team);

            // Registramos los players y el usuario para obtener el nuevo rating
            players.put(contestEntry.userId, player);
            result.put(contestEntry.userId, user);

            evolution.put(user.userId, ImmutableMap.builder()
                    .put("position", contestEntry.position)
                    .put("fantasyPoints", contestEntry.fantasyPoints)
                    .put("trueSkill", user.trueSkill)
                    .put("mean", user.rating.Mean)
                    .put("deviation", user.rating.StandardDeviation)
                    .build());
            /*
            Logger.debug("BEGIN: User: {} Position: {} DFP: {} TrueSkill: {} Mean: {} StandarDeviation: {}",
                    contestEntry.position, contestEntry.fantasyPoints, user.nickName, user.trueSkill, user.rating.Mean, user.rating.StandardDeviation);
            */
        }

        // Los rankings (actualmente sin empates)
        int[] rankings = new int[teams.size()];
        for (int i=0; i<rankings.length; i++) {
            rankings[i] = i+1;
        }

        GameInfo gameInfo = new jskills.GameInfo(INITIAL_MEAN, INITIAL_SD, BETA, DYNAMIC_FACTOR, DRAW);

        Map<IPlayer, Rating> newRatings = calculator.calculateNewRatings(gameInfo, teams, rankings);

        // Actualizamos los usuarios con los nuevos ratings calculados
        for (Map.Entry<ObjectId, IPlayer> player : players.entrySet()) {
            jskills.Rating rating = newRatings.get(player.getValue());

            if (MyConservativeTrueSkill(rating) < 0) {
                rating = new Rating(rating.getStandardDeviation() * CONSERVATIVE_FACTOR, rating.getStandardDeviation());
            }

            User user = result.get(player.getKey());
            user.trueSkill = (int)(MyConservativeTrueSkill(rating) * MULTIPLIER);
            user.rating.Mean = rating.getMean();
            user.rating.StandardDeviation = rating.getStandardDeviation();

            if (evolution.containsKey(user.userId)) {
                Map userEvolution = evolution.get(user.userId);
                Logger.debug("--> User: {} Position: {} DFP: {} TrueSkill: {}/{} Mean: {}/{} StandarDeviation: {}/{}",
                        user.nickName, userEvolution.get("position"), userEvolution.get("fantasyPoints"),
                        userEvolution.get("trueSkill"), user.trueSkill,
                        userEvolution.get("mean"), user.rating.Mean,
                        userEvolution.get("deviation"), user.rating.StandardDeviation);
                // Logger.debug("END: User: {} TrueSkill: {} Mean: {} StandarDeviation: {}", user.nickName, user.trueSkill, user.rating.Mean, user.rating.StandardDeviation);
            }
        }

        return result;
    }

    static public void RecomputeRatings(jskills.Rating ratingPlayer1, jskills.Rating ratingPlayer2, int goalsPlayer1, int goalsPlayer2) {
        SkillCalculator calculator = new jskills.trueskill.TwoPlayerTrueSkillCalculator();

        IPlayer player1 = new jskills.Player<Object>(1);
        IPlayer player2 = new jskills.Player<Object>(2);

        ITeam team1 = new jskills.Team(player1, ratingPlayer1);
        ITeam team2 = new jskills.Team(player2, ratingPlayer2);

        GameInfo gameInfo = new jskills.GameInfo(INITIAL_MEAN, INITIAL_SD, BETA, DYNAMIC_FACTOR, DRAW);

        int rankingPlayer1 = 1;
        int rankingPlayer2 = 1;

        if (goalsPlayer1 > goalsPlayer2)
        {
            rankingPlayer1 = 1;
            rankingPlayer2 = 2;
        }
        else if (goalsPlayer1 < goalsPlayer2)
        {
            rankingPlayer1 = 2;
            rankingPlayer2 = 1;
        }

        Map<IPlayer, Rating> newRatings = calculator.calculateNewRatings(gameInfo, jskills.Team.concat(team1, team2), rankingPlayer1, rankingPlayer2);

        ratingPlayer1 = newRatings.get(player1);
        ratingPlayer2 = newRatings.get(player2);

        if (MyConservativeTrueSkill(ratingPlayer1) < 0) {
            ratingPlayer1 = new Rating(ratingPlayer1.getStandardDeviation() * CONSERVATIVE_FACTOR, ratingPlayer1.getStandardDeviation());
        }

        if (MyConservativeTrueSkill(ratingPlayer2) < 0) {
            ratingPlayer2 = new Rating(ratingPlayer2.getStandardDeviation() * CONSERVATIVE_FACTOR, ratingPlayer2.getStandardDeviation());
        }
    }
}
