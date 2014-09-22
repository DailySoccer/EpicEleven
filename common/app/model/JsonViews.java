package model;

public class JsonViews {
    static public class Public {}
    static public class Extended extends Public {}
    static public class FullContest extends Extended {}
    static public class NotForClient {}

    static public class ActiveContests extends Public {}
}
