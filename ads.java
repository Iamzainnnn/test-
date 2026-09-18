import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

public class RemoveAdsPatch {

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.out.println("Usage: java RemoveAdsPatch <path-to-decompiled-apk>");
            return;
        }

        Path projectRoot = Paths.get(args[0]);
        if (!Files.exists(projectRoot)) {
            System.out.println("❌ Directory not found: " + projectRoot);
            return;
        }

        System.out.println("🔍 Searching for ad-pod gatekeeper class...");

        Path targetFilePath = findPathContaining(projectRoot, "Is ad pod");

        if (targetFilePath == null) {
            System.out.println("❌ 'Is ad pod' not found in any smali folder.");
            return;
        }

        System.out.println("✅ Found file: " + targetFilePath.toAbsolutePath());

        List<String> lines = Files.readAllLines(targetFilePath);
        int methodLine = -1;

        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains("Is ad pod")) {
                for (int j = i; j >= 0; j--) {
                    if (lines.get(j).trim().startsWith(".method")) {
                        methodLine = j;
                        break;
                    }
                }
                break;
            }
        }

        if (methodLine == -1) {
            System.out.println("❌ .method start not found for 'Is ad pod'.");
            return;
        }

        List<String> injection = List.of(
            "",
            "    const/4 v0, 0x1",
            "",
            "    return v0"
        );

        lines.add(methodLine + 2, String.join("\n", injection));
        Files.write(targetFilePath, lines);

        System.out.println("✅ Ad pod gatekeeper patched successfully!");
    }

    private static Path findPathContaining(Path root, String keyword) throws IOException {
        try (Stream<Path> stream = Files.walk(root)) {
            return stream.filter(Files::isRegularFile)
                         .filter(p -> p.toString().endsWith(".smali"))
                         .filter(p -> {
                             try {
                                 return Files.readString(p).contains(keyword);
                             } catch (IOException e) {
                                 return false;
                             }
                         })
                         .findFirst()
                         .orElse(null);
        }
    }
}