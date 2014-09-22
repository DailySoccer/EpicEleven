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
            ret = asProjection(getFieldNames("", viewClass, pojoClass));

            registerCache(viewClass, pojoClass, ret);
        }
        return ret;
    }

    static private List<String> getFieldNames(String path, Class<?> viewClass, Class<?> pojoClass) {
        List<String> fieldNames = new ArrayList<>();
        for(Field field: pojoClass.getFields()) {
            // No aceptamos campos estáticos
            if (Modifier.isStatic(field.getModifiers()))
                continue;

            boolean fieldValid = false;

            // Si tiene la annotation JsonView...
            if (field.isAnnotationPresent(JsonView.class)) {
                // ... buscar si la viewClass hereda de alguna de las clases incluidas en el JsonView
                JsonView annotation = field.getAnnotation(JsonView.class);
                for (Class<?> clazz: annotation.value()) {
                    if (clazz.isAssignableFrom(viewClass)) {
                        fieldValid = true;
                        break;
                    }
                }
            }
            else {
                fieldValid = true;
            }

            if (fieldValid) {
                Class<?> fieldType = getFieldType(field);
                if (hasFieldWithAnnotation(fieldType)) {
                    fieldNames.addAll(getFieldNames(field.getName(), viewClass, fieldType));
                } else {
                    fieldNames.add(path.isEmpty() ? field.getName() : String.format("%s.%s", path, field.getName()));
                }
            }
        }
        return fieldNames;
    }

    static private String asProjection(List<String> fields) {
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
        return buffer != null ? buffer.toString() : "";
    }

    static private Class<?> getFieldType(Field field) {
        Class<?> fieldType = field.getType();
        if (fieldType.equals(List.class) || fieldType.equals(ArrayList.class)) {
            ParameterizedType listType = (ParameterizedType) field.getGenericType();
            fieldType = (Class<?>) listType.getActualTypeArguments()[0];
        }
        return fieldType;
    }

    static private boolean hasFieldWithAnnotation(Class<?> pojoClass) {
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
