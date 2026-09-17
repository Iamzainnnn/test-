import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public class AmoledThemePatcher {

    private static final List<String> TARGET_FILES = Arrays.asList(
            "BasePrismColorsV2.smali",
            "BasePrismColors.smali"
    );

    private static final Map<String, String> FIELD_PATCHES = new LinkedHashMap<String, String>() {{
        put("GRAY_1600", "0xff000000L");
        put("BLACK", "0xff000000L");
    }};


    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Usage: java AmoledThemePatcher <project_dir>");
            return;
        }

        File projectDir = new File(args[0]);

        if (!projectDir.exists()) {
            System.out.println("Invalid project directory!");
            return;
        }

        System.out.println("Starting AMOLED patch...\n");

        patchNight(projectDir);
        patchDefault(projectDir);
        patchPrismColors(projectDir);

        System.out.println("\nDone ✅");
    }

    // -------------------------------
    // Patch values-night
    // -------------------------------
    private static void patchNight(File baseDir) {
        List<File> files = scanForColors(baseDir, "values-night");

        if (files.isEmpty()) {
            System.out.println("values-night/colors.xml not found");
            return;
        }

        for (File file : files) {
            try {
                Document doc = loadXML(file);
                NodeList colors = doc.getElementsByTagName("color");

                setColor(colors, "igds_secondary_background", "@color/bds_black");
                setColor(colors, "igds_elevated_background", "@color/bds_black");
                setColor(colors, "igds_elevated_highlight_background", "@color/bds_black");

                saveXML(doc, file);
                System.out.println("Night patched: " + file.getAbsolutePath());

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // -------------------------------
    // Patch values
    // -------------------------------
    private static void patchDefault(File baseDir) {
        List<File> files = scanForColors(baseDir, "values");

        if (files.isEmpty()) {
            System.out.println("values/colors.xml not found");
            return;
        }

        for (File file : files) {
            try {
                Document doc = loadXML(file);
                NodeList colors = doc.getElementsByTagName("color");

                setColor(colors, "igds_prism_black", "#ff000000");

                saveXML(doc, file);
                System.out.println("Default patched: " + file.getAbsolutePath());

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // -------------------------------
    // Scan files
    // -------------------------------
    private static List<File> scanForColors(File baseDir, String folder) {
        List<File> result = new ArrayList<>();

        scanRecursive(baseDir, folder, result);

        System.out.println("Found " + result.size() + " colors.xml in " + folder);
        return result;
    }

    private static void scanRecursive(File dir, String folder, List<File> result) {
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            String path = file.getAbsolutePath().replace("\\", "/");

            if (file.isDirectory()) {
                scanRecursive(file, folder, result);
            } else if (
                    file.getName().equals("colors.xml") &&
                    path.contains("/res/") &&
                    path.contains("/" + folder)
            ) {
                result.add(file);
            }
        }
    }

    // -------------------------------
    // XML helpers
    // -------------------------------
    private static Document loadXML(File file) throws Exception {
        return DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(file);
    }

    private static void saveXML(Document doc, File file) throws Exception {
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.transform(new DOMSource(doc), new StreamResult(file));
    }

    private static void setColor(NodeList nodes, String name, String value) {
        for (int i = 0; i < nodes.getLength(); i++) {
            Element el = (Element) nodes.item(i);

            if (el.getAttribute("name").equals(name)) {
                String old = el.getTextContent().trim();
                el.setTextContent(value);

                System.out.println("[" + name + "] " + old + " -> " + value);
                return;
            }
        }

        System.out.println(name + " not found");
    }

    private static void patchPrismColors(File projectDir) {
        try {
            List<File> found = findSmaliFiles(projectDir.getAbsolutePath());
            if(found.isEmpty()){
                System.out.println("No target smali files found");
                return;
            }
            for(File f: found){
                patchFile(f);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void patchFile(File smaliFile) throws Exception {
        List<String> lines = new ArrayList<>(Files.readAllLines(smaliFile.toPath()));
        boolean changed=false;
        for(int i=0;i<lines.size();i++){
            String t=lines.get(i).trim();
            if(!t.startsWith("sput-wide")) continue;

            String fieldName=null;
            String newValue=null;
            for(Map.Entry<String,String> entry: FIELD_PATCHES.entrySet()){
                if(t.contains("->"+entry.getKey()+":J")){
                    fieldName=entry.getKey();
                    newValue=entry.getValue();
                    break;
                }
            }
            if(fieldName==null) continue;

            for(int j=i-1;j>=Math.max(0,i-20);j--){
                String p=lines.get(j).trim();
                if(p.startsWith("const-wide")){
                    String indent=getIndent(lines.get(j));
                    String reg=extractRegister(p);
                    lines.set(j, indent+"const-wide "+reg+", "+newValue);
                    changed=true;
                    System.out.println("[PATCH] "+fieldName+" in "+smaliFile.getName());
                    break;
                }
                if(p.startsWith("sput-wide")||p.startsWith(".method")) break;
            }
        }
        if(changed){
            Files.write(smaliFile.toPath(), lines, java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private static String extractRegister(String constLine){
        String after=constLine.substring(constLine.indexOf(' ')).trim();
        return after.substring(0,after.indexOf(',')).trim();
    }

    private static String getIndent(String line){
        StringBuilder sb=new StringBuilder();
        for(char c: line.toCharArray()){
            if(c==' '||c=='\t') sb.append(c); else break;
        }
        return sb.toString();
    }

    private static List<File> findSmaliFiles(String basePath) throws IOException{
        List<File> result=new ArrayList<>();
        Files.walk(Paths.get(basePath))
            .filter(p->TARGET_FILES.contains(p.getFileName().toString()))
            .forEach(p->result.add(p.toFile()));
        return result;
    }

}