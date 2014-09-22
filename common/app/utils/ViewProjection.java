package utils;

import com.fasterxml.jackson.annotation.JsonView;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ViewProjection {
    static public String get(Class<?> viewClass, Class<?> pojoClass) {
        String ret = getCached(viewClass, pojoClass);
        if (ret == null) {
            List<String> fields = new ArrayList<>();
            putFieldNames(fields, "", viewClass, pojoClass);

            StringBuffer buffer = null;
            for (String field : fields) {
                if (field.equals("_id")) {
                    continue;
                }
                if (buffer == null) {
                    buffer = new StringBuffer();
                    buffer.append("{");
                } else {
                    buffer.append(",");
                }
                buffer.append(String.format("%s:1", field));
            }
            if (buffer != null) {
                buffer.append("}");
            }

            ret = buffer.toString();
            registerCache(viewClass, pojoClass, ret);
        }
        return ret;
    }

    static private void putFieldNames(List<String> fieldNames, String path, Class<?> viewClass, Class<?> pojoClass) {
        for(Field field: pojoClass.getFields()) {
            if (Modifier.isStatic(field.getModifiers()))
                continue;

            if (field.isAnnotationPresent(JsonView.class)) {
                JsonView annotation = field.getAnnotation(JsonView.class);
                for (Class<?> clazz: annotation.value()) {
                    if (clazz.isAssignableFrom(viewClass)) {
                        Class<?> fieldType = getFieldType(field);
                        if (hasFieldWithAnnotation(viewClass, fieldType)) {
                            putFieldNames(fieldNames, field.getName(), viewClass, fieldType);
                        } else {
                            fieldNames.add(path.isEmpty() ? field.getName() : String.format("%s.%s", path, field.getName()));
                        }
                        break;
                    }
                }

            }
            else {
                fieldNames.add(path.isEmpty() ? field.getName() : String.format("%s.%s", path, field.getName()));
            }
        }
    }

    static Class<?> getFieldType(Field field) {
        Class<?> fieldType = field.getType();
        if (fieldType.equals(List.class) || fieldType.equals(ArrayList.class)) {
            ParameterizedType listType = (ParameterizedType) field.getGenericType();
            fieldType = (Class<?>) listType.getActualTypeArguments()[0];
        }
        return fieldType;
    }

    static private boolean hasFieldWithAnnotation(Class<?> viewClass, Class<?> pojoClass) {
        for(Field field: pojoClass.getFields()) {
            if (Modifier.isStatic(field.getModifiers()))
                continue;

            if (field.isAnnotationPresent(JsonView.class))
                return true;
        }
        return false;
    }

    static private void registerCache(Class<?> viewClass, Class<?> pojoClass, String value) {
        String key = viewClass.getName().concat(pojoClass.getName());
        cache.put(key, value);
    }

    static private String getCached(Class<?> viewClass, Class<?> pojoClass) {
        String key = viewClass.getName().concat(pojoClass.getName());
        return cache.containsKey(key) ? cache.get(key) : null;
    }

    static private Map<String, String> cache = new HashMap<>();
}
