package utils;

import com.fasterxml.jackson.annotation.JsonView;
import org.jongo.marshall.jackson.oid.Id;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class ViewProjection {
    static public String get(Class<?> viewClass, Class<?> pojoClass) {
        String ret = getCached(viewClass, pojoClass);
        if (ret == null) {
            ret = asProjection(getFieldNames("", viewClass, pojoClass));

            registerCache(viewClass, pojoClass, ret);
        }
        return ret;
    }

    static public String get(Class<?> viewClass, List<String> projections, Class<?> pojoClass) {
        String ret = getCached(viewClass, projections, pojoClass);
        if (ret == null) {
            List<String> fields = getFieldNames("", viewClass, pojoClass);
            fields.addAll(projections);

            ret = asProjection(fields);

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
            String fieldName = field.getName();

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
                // Los campos con annotation "@Id" pueden cambiar el nombre del identificador
                if (field.isAnnotationPresent(Id.class)) {
                    /*
                    Id id = field.getAnnotation(Id.class);
                    JsonProperty jsonProperty = id.annotationType().getAnnotation(JsonProperty.class);
                    if (jsonProperty != null) {
                        fieldName = jsonProperty.value();
                    }
                    */
                    fieldName = "_id";
                }
                fieldValid = true;
            }

            if (fieldValid) {
                String fieldNameFull = path.isEmpty() ? fieldName : String.format("%s.%s", path, fieldName);

                Class<?> fieldType = getFieldType(field);
                if (hasFieldWithAnnotation(fieldType)) {
                    fieldNames.addAll(getFieldNames(fieldNameFull, viewClass, fieldType));
                } else {
                    fieldNames.add(fieldNameFull);
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
        _cache.putIfAbsent(key, value);
    }

    static private String getCached(Class<?> viewClass, Class<?> pojoClass) {
        String key = viewClass.getName().concat(pojoClass.getName());
        return _cache.get(key);
    }

    static private String getCached(Class<?> viewClass, List<String> projections, Class<?> pojoClass) {
        String key = viewClass.getName().concat(pojoClass.getName()).concat( String.join("", projections));
        return _cache.get(key);
    }

    static private ConcurrentMap<String, String> _cache = new ConcurrentHashMap<>(16, 0.9f, 1);
}
