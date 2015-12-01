package utils;

import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

public class FileUtils {
    public static void generateCsv(String sFileName, List<String> headers, List<String> body) {
        try {
            FileWriter writer = new FileWriter(sFileName);

            for (int i=0; i<headers.size(); i++) {
                writer.append(headers.get(i));
                writer.append( ((i+1) < headers.size()) ? "," : "\n" );
            }

            for (int i=0; i<body.size(); i++) {
                writer.append(body.get(i));
                writer.append( ((i+1) % headers.size()) != 0 ? "," : "\n" );
            }

            writer.flush();
            writer.close();
        }
        catch(IOException e) {
            e.printStackTrace();
        }
    }

}
