package utils;

import play.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class ProcessExec {

    static public String exec(String command) throws IOException {
        return readLineFromInputStream(Runtime.getRuntime().exec(command));
    }

    static private String readLineFromInputStream(Process p) throws IOException {
        String line;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            line = reader.readLine();
            Logger.info(line);
        }

        return line;
    }

}
