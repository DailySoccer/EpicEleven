package actors;


public class DynamicMsg {
    final public String msg;
    final public Object params;

    public DynamicMsg(String m, Object p) { msg = m; params = p; }
}
