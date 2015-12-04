package utils;

import java.io.*;
import java.util.List;

public class FileUtils {
    public static void generateCsv(String sFileName, List<String> headers, List<String> body) {
        generateCsv(sFileName, headers, body, ",");
    }

    public static void generateCsv(String sFileName, List<String> headers, List<String> body, String separator) {
        try {
            Writer out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(sFileName), "UTF-8"));

            for (int i=0; i<headers.size(); i++) {
                out.append(headers.get(i));
                out.append( ((i+1) < headers.size()) ? separator : "\n" );
            }

            for (int i=0; i<body.size(); i++) {
                out.append(body.get(i));
                out.append( ((i+1) % headers.size()) != 0 ? separator : "\n" );
            }

            out.flush();
            out.close();
        }
        catch(IOException e) {
            e.printStackTrace();
        }
    }

}
