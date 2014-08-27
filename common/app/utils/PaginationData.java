package utils;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jongo.Find;
import org.jongo.MongoCollection;
import play.Logger;
import play.libs.Json;
import play.mvc.Result;
import play.mvc.Results;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class PaginationData {
    public List<String> getFieldNames() { return null; }
    public String getFieldByIndex(Object data, Integer index) { return null; }
    public String getFieldHtmlByIndex(Object data, Integer index) { return null; }

    public static <T> Result withAjax(Map<String, String[]> params, MongoCollection collection, final Class<T> clazz, PaginationData paginationData) {
        long startTime = System.currentTimeMillis();

        long iTotalRecords = collection.count();
        long iTotalDisplayRecords = iTotalRecords;
        String filter = params.get("sSearch")[0];
        Integer pageSize = Integer.valueOf(params.get("iDisplayLength")[0]);
        Integer page = Integer.valueOf(params.get("iDisplayStart")[0]) / pageSize;
        Integer iSort = Integer.valueOf(params.get("iSortCol_0")[0]);

        List<String> fieldNames = paginationData.getFieldNames();
        String sortBy = fieldNames.get(iSort);
        String order = params.get("sSortDir_0")[0];

        // Aqui iremos añadiendo los registros válidos
        List<T> dataList = new ArrayList<>();

        Find find;

        // Si no hay filtro
        if (filter.isEmpty()) {
            // Devolvemos la página en el orden indicado
            find = collection.find()
                    .sort(String.format("{%s : %d}", sortBy, order.equals("asc") ? 1 : -1))
                    .skip(page * pageSize)
                    .limit(pageSize);

            dataList = ListUtils.asList(find.as(clazz));
        }
        else {
            // Hay filtro...

            // Obtenemos todos los elementos ordenados
            //   no hacemos directamente el filtro dado que no permitiría buscar con los resultados reales, sino como están almacenados en la bdd)
            find = collection.find()
                    .sort(String.format("{%s : %d}", sortBy, order.equals("asc") ? 1 : -1));

            // Recorremos los registros
            Iterable<T> results = find.as(clazz);
            iTotalDisplayRecords = 0;

            Iterator<T> it = results.iterator();
            while (it.hasNext()) {
                T data = it.next();

                // Lo consideramos válido si alguno de sus campos incluye el valor del filtro
                boolean valid = false;
                for (int i=0; i<fieldNames.size() && !valid; i++) {
                    String fieldValue = paginationData.getFieldByIndex(data, i);
                    valid = (fieldValue != null) && fieldValue.contains(filter);
                }
                if (valid) {
                    if ((iTotalDisplayRecords > (page * pageSize)) && (dataList.size() < pageSize)) {
                        dataList.add(data);
                    }
                    iTotalDisplayRecords++;
                }
            }
        }

        Logger.info("elapsed 0: {}", System.currentTimeMillis() - startTime);

        startTime = System.currentTimeMillis();

        // Devolvemos los datos como lo espera DataTable
        ObjectNode result = Json.newObject();

        result.put("sEcho", Integer.valueOf(params.get("sEcho")[0]));
        result.put("iTotalRecords", iTotalRecords);
        result.put("iTotalDisplayRecords", iTotalDisplayRecords);

        ArrayNode an = result.putArray("aaData");

        for(T data : dataList) {
            ObjectNode row = Json.newObject();
            for (int i=0; i<fieldNames.size(); i++) {
                row.put(String.valueOf(i), paginationData.getFieldHtmlByIndex(data, i));
            }
            an.add(row);
        }

        Logger.info("elapsed 1: {}", System.currentTimeMillis() - startTime);
        return Results.ok(result);
    }
}
