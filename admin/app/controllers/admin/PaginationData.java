package controllers.admin;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jongo.Find;
import org.jongo.MongoCollection;
import play.libs.Json;
import play.mvc.Result;
import play.mvc.Results;

import java.util.*;


class FieldComparable implements Comparator<Map<String,Object>>{
    public int sortedByDir() { return 1; }
    public String sortedByField() { return "0"; }

    @Override
    public int compare(Map<String,Object> o1, Map<String,Object> o2) {
        return String.valueOf(o1.get(sortedByField())).compareTo(String.valueOf(o2.get(sortedByField()))) * sortedByDir();
    }
}

public class PaginationData {
    public String projection() { return null; }
    public List<String> getFieldNames() { return null; }
    public String getFieldByIndex(Object data, Integer index) { return null; }
    public String getRenderFieldByIndex(Object data, String fieldValue, Integer index) { return fieldValue; }

    public static <T> Result withAjax(Map<String, String[]> params, MongoCollection collection, final Class<T> clazz, PaginationData paginationData) {
        //long startTime = System.currentTimeMillis();

        long iTotalRecords = collection.count();
        long iTotalDisplayRecords = iTotalRecords;
        String filter = params.get("sSearch")[0];
        Integer pageSize = Integer.valueOf(params.get("iDisplayLength")[0]);
        Integer page = Integer.valueOf(params.get("iDisplayStart")[0]) / pageSize;
        final Integer iSort = Integer.valueOf(params.get("iSortCol_0")[0]);

        List<String> fieldNames = paginationData.getFieldNames();
        String sortBy = fieldNames.get(iSort);
        String order = params.get("sSortDir_0")[0];
        Boolean sortedByBDD = false; //!sortBy.equals("");

        // Aqui iremos añadiendo los registros válidos
        List<Map<String, Object>> dataList = new ArrayList<>();

        Find find = collection.find();

        if (paginationData.projection() != null) {
            find.projection(paginationData.projection());
        }

        List<Map<String, Object>> dataRow = new ArrayList<>();

        // Si el campo para ordenar está incluido en la BDD, puede ser procesado de forma más rápida
        if (sortedByBDD) {
            find = find.sort(String.format("{%s : %d}", sortBy, order.equals("asc") ? 1 : -1));

            // Si no hay filtro, podremos obtener la página de la propia BDD
            if (filter.isEmpty()) {
                find = find.skip(page * pageSize)
                           .limit(pageSize);
            }
        }

        // Obtener los campos "en bruto" que queremos visualizar en la tabla
        for (T data : find.as(clazz)) {
            Map<String, Object> values = new HashMap<>();
            values.put("object", data);

            for (int i = 0; i < fieldNames.size(); i++) {
                values.put(String.valueOf(i), paginationData.getFieldByIndex(data, i));
            }

            dataRow.add(values);
        }

        if (!sortedByBDD) {
            // Ordenar los registros "a mano", por el campo indicado (no incluido en la BDD)
            Collections.sort(dataRow,
                    order.equals("asc")
                            ? new FieldComparable() {
                                public String sortedByField() { return String.valueOf(iSort); }
                            }
                            :  new FieldComparable() {
                                public int sortedByDir() { return -1; }
                                public String sortedByField() { return String.valueOf(iSort); }
                            });
        }

        // No hay filtro?
        if (filter.isEmpty()) {
            if (sortedByBDD) {
                // Todos los registros son válidos
                for (Map<String, Object> aDataRow : dataRow) {
                    dataList.add(aDataRow);
                }
            } else {
                // Los registros válidos son los correspondientes a la página solicitada
                int startIndex = page * pageSize;
                for (int i = startIndex; i < (startIndex + pageSize) && i < dataRow.size(); i++) {
                    dataList.add(dataRow.get(i));
                }
            }
        }
        else {
            iTotalDisplayRecords = 0;

            // Los registros serán válidos si incluyen el valor del filtro
            for (Map<String, Object> values : dataRow) {
                boolean valid = false;
                for (int fieldIndex = 0; fieldIndex < fieldNames.size() && !valid; fieldIndex++) {
                    String fieldValue = String.valueOf(values.get(String.valueOf(fieldIndex)));
                    valid = (fieldValue != null) && fieldValue.contains(filter);
                }
                if (valid) {
                    if ((iTotalDisplayRecords >= (page * pageSize)) && (dataList.size() < pageSize)) {
                        dataList.add(values);
                    }
                    iTotalDisplayRecords++;
                }
            }
        }

        // Devolvemos los datos como lo espera DataTable
        ObjectNode result = Json.newObject();

        result.put("sEcho", Integer.valueOf(params.get("sEcho")[0]));
        result.put("iTotalRecords", iTotalRecords);
        result.put("iTotalDisplayRecords", iTotalDisplayRecords);

        ArrayNode an = result.putArray("aaData");

        for(Map<String, Object> data : dataList) {
            ObjectNode row = Json.newObject();
            for (int i=0; i<fieldNames.size(); i++) {
                row.put(String.valueOf(i), paginationData.getRenderFieldByIndex(data.get("object"), String.valueOf(data.get(String.valueOf(i))), i));
            }
            an.add(row);
        }

        //play.Logger.info("elapsed: {}", System.currentTimeMillis() - startTime);
        return Results.ok(result);
    }
}
