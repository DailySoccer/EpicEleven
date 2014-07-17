package utils;

import java.util.LinkedHashMap;
import java.util.Map;

public class EnumUtils {
    public static <T extends Enum<T>> Map<String, String> toMap(Class<T> aEnum){
        LinkedHashMap<String, String> vals = new LinkedHashMap<>();
        for (T cType : aEnum.getEnumConstants()) {
            vals.put(cType.name(), cType.name());
        }
        return vals;
    }
}
