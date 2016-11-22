package model;

public class JsonViews {
    static public class Public {}
    static public class ContestInfo extends Public {}
    static public class CreateContest extends Public {}
    static public class Template extends Public {}
    static public class Extended extends Public {}
    static public class FullContest extends Extended {}
    static public class Statistics extends Extended {}
    static public class CheckChanges extends Public {}

    static public class Leaderboard {}
    static public class NotForClient {}

    static public class AllContests {}
    static public class ActiveContests extends AllContests {}
    static public class MyActiveContests extends ActiveContests {}
    static public class MyLiveContests extends AllContests {}
    static public class MyHistoryContests extends AllContests {}
    static public class InstanceSoccerPlayers {}
}
