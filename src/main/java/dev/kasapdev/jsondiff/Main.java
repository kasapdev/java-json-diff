package dev.kasapdev.jsondiff;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Command-line entry point: {@code java dev.kasapdev.jsondiff.Main <fileA.json> <fileB.json>}.
 * Parses both files and prints the structural diff between them.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Usage: java dev.kasapdev.jsondiff.Main <fileA.json> <fileB.json>");
            System.exit(2);
            return;
        }
        try {
            String textA = Files.readString(Path.of(args[0]));
            String textB = Files.readString(Path.of(args[1]));

            Object a = JsonParser.parse(textA);
            Object b = JsonParser.parse(textB);

            List<DiffEntry> diffs = JsonDiff.diff(a, b);

            if (diffs.isEmpty()) {
                System.out.println("No differences found.");
                return;
            }
            System.out.println(diffs.size() + " difference(s) found:");
            for (DiffEntry entry : diffs) {
                System.out.println("  " + entry);
            }
        } catch (IOException e) {
            System.err.println("Error reading file: " + e.getMessage());
            System.exit(1);
        } catch (JsonParseException e) {
            System.err.println("JSON parse error: " + e.getMessage());
            System.exit(1);
        }
    }
}
