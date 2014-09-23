package model;

public class JsonViews {
    static public class Public {}
    static public class Extended extends Public {}
    static public class FullContest extends Extended {}
    static public class NotForClient {}

    static public class AllContests {}
    static public class ActiveContests extends AllContests {}
    static public class MyActiveContests extends ActiveContests {}
    static public class MyLiveContests extends AllContests {}
    static public class MyHistoryContests extends AllContests {}
}
