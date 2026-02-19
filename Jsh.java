import java.io.*;
import java.util.*;

public class Jsh {

    private static File currentDirectory = new File("/");
    private static final Set<String> ROOT_VISIBLE = new HashSet<>(Arrays.asList("folder1"));
    private static final Scanner sc = new Scanner(System.in);
    private static final List<String> history = new ArrayList<>();
    private static int jobCounter = 1;

    public static void main(String[] args) {

        boolean running = true;

        while (running) {
            System.out.print("jsh:" + currentDirectory.getAbsolutePath() + ">> ");
            String rawLine = sc.nextLine();
            if (rawLine == null) break;

            String input = rawLine.trim();
            if (input.isEmpty()) continue;

            if (input.equals("exit")) {
                running = false;
                addHistory(input);
                continue;
            }

            if (input.startsWith("!")) {
                String expanded = resolveHistory(input);
                if (expanded == null) {
                    addHistory(input);
                    continue;
                }
                executeLine(expanded);
                addHistory(input);
                continue;
            }

            executeLine(input);
            addHistory(input);
        }

        sc.close();
    }

    private static void addHistory(String line) {
        if (history.size() == 20) {
            history.remove(0);
        }
        history.add(line);
    }

    private static String expandHistoryOnce(String token) {
        if (history.isEmpty()) {
            System.out.println("history: no hay comandos en el historial");
            return null;
        }
        if (token.equals("!#")) {
            return history.get(history.size() - 1);
        }
        if (token.startsWith("!")) {
            String numStr = token.substring(1).trim();
            try {
                int n = Integer.parseInt(numStr);
                if (n < 1 || n > history.size()) {
                    System.out.println("history: numero fuera de rango");
                    return null;
                }
                return history.get(n - 1);
            } catch (NumberFormatException e) {
                System.out.println("history: formato invalido");
                return null;
            }
        }
        return null;
    }

    private static String resolveHistory(String token) {
        String current = token;
        for (int i = 0; i < 10; i++) {
            if (!current.startsWith("!")) {
                return current;
            }
            String next = expandHistoryOnce(current);
            if (next == null) {
                return null;
            }
            current = next.trim();
        }
        System.out.println("history: expansion infinita");
        return null;
    }

    private static void executeLine(String line) {
        if (containsOperator(line, "^^") && containsOperator(line, "=>")) {
            System.out.println("Error: no se pueden mezclar ^^ y => en la misma linea");
            return;
        }

        List<String> backgroundCommands = parseBackgroundCommands(line);
        if (backgroundCommands != null) {
            for (String cmd : backgroundCommands) {
                if (cmd.equals("exit")) {
                    System.out.println("Error: exit no se permite en comandos con ^^");
                    return;
                }
                executeBackground(cmd);
            }
            return;
        }

        List<String> sequence = splitByOperator(line, "=>");
        if (sequence.size() == 1) {
            String cmd = sequence.get(0);
            if (cmd.equals("exit")) {
                System.out.println("Error: exit debe ser un comando unico");
                return;
            }
            executeForeground(cmd);
            return;
        }

        for (String cmd : sequence) {
            if (cmd.equals("exit")) {
                System.out.println("Error: exit no se permite en secuencias =>");
                return;
            }
            executeForeground(cmd);
        }
    }

    private static void executeForeground(String command) {
        String trimmed = command.trim();
        if (trimmed.isEmpty()) return;

        System.out.println(trimmed + ":");

        List<String> tokens;
        try {
            tokens = tokenize(trimmed);
        } catch (IllegalArgumentException e) {
            System.out.println(e.getMessage());
            return;
        }

        if (tokens.isEmpty()) return;

        String cmd = tokens.get(0);

        if (cmd.equals("pwd")) {
            System.out.println(currentDirectory.getAbsolutePath());
            return;
        }
        if (cmd.equals("ls")) {
            listDirectory();
            return;
        }
        if (cmd.equals("cd")) {
            String path = tokens.size() > 1 ? tokens.get(1) : "/";
            changeDirectory(path);
            return;
        }
        if (cmd.equals("history")) {
            printHistory();
            return;
        }
        if (cmd.equals("exit")) {
            System.out.println("Error: exit debe ser un comando unico");
            return;
        }

        executeExternalForeground(tokens);
    }

    private static void executeBackground(String command) {
        String trimmed = command.trim();
        if (trimmed.isEmpty()) return;

        List<String> tokens;
        try {
            tokens = tokenize(trimmed);
        } catch (IllegalArgumentException e) {
            System.out.println(e.getMessage());
            return;
        }

        if (tokens.isEmpty()) return;

        String cmd = tokens.get(0);
        if (cmd.equals("cd") || cmd.equals("pwd") || cmd.equals("history") || cmd.equals("exit")) {
            System.out.println("Error: builtins no se permiten con ^^");
            return;
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(resolveCommand(tokens));
            pb.directory(currentDirectory);
            pb.inheritIO();

            Process process = pb.start();
            System.out.println("[" + jobCounter + "] " + process.pid());
            jobCounter++;

        } catch (IOException e) {
            System.out.println("Error ejecutando comando en background: " + e.getMessage());
        }
    }

    private static void executeExternalForeground(List<String> tokens) {
        ProcessBuilder pb = new ProcessBuilder(resolveCommand(tokens));
        pb.directory(currentDirectory);

        try {
            Process process = pb.start();

            ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
            ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();

            Thread outThread = new Thread(() -> streamToBuffer(process.getInputStream(), outBuffer));
            Thread errThread = new Thread(() -> streamToBuffer(process.getErrorStream(), errBuffer));

            outThread.start();
            errThread.start();

            int exitCode = process.waitFor();
            outThread.join();
            errThread.join();

            String stdout = outBuffer.toString();
            String stderr = errBuffer.toString();

            if (exitCode == 0) {
                if (!stdout.isEmpty()) {
                    System.out.print(stdout);
                }
            } else {
                if (!stderr.isEmpty()) {
                    System.out.print(stderr);
                }
            }

        } catch (IOException | InterruptedException e) {
            System.out.println("Error ejecutando comando: " + e.getMessage());
            Thread.currentThread().interrupt();
        }
    }

    private static void streamToBuffer(InputStream in, ByteArrayOutputStream buffer) {
        try {
            byte[] data = new byte[4096];
            int n;
            while ((n = in.read(data)) != -1) {
                buffer.write(data, 0, n);
            }
        } catch (IOException ignored) {
        }
    }

    private static List<String> resolveCommand(List<String> tokens) {
        if (tokens.isEmpty()) return tokens;

        String cmd = tokens.get(0);
        if (cmd.contains("/") || cmd.contains("\\")) {
            return tokens;
        }

        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if (os.contains("windows")) {
            List<String> withCmd = new ArrayList<>();
            withCmd.add("cmd");
            withCmd.add("/c");
            withCmd.addAll(tokens);
            return withCmd;
        }

        List<String> withBin = new ArrayList<>(tokens);
        withBin.set(0, "/bin/" + cmd);
        if (new File(withBin.get(0)).exists()) {
            return withBin;
        }

        List<String> withUsrBin = new ArrayList<>(tokens);
        withUsrBin.set(0, "/usr/bin/" + cmd);
        if (new File(withUsrBin.get(0)).exists()) {
            return withUsrBin;
        }

        return tokens;
    }

    private static void changeDirectory(String path) {
        try {
            File newDir;

            if (path.equals("..")) {
                newDir = currentDirectory.getParentFile();
                if (newDir == null) return;
            } else if (path.startsWith("/")) {
                newDir = new File(path);
            } else {
                newDir = new File(currentDirectory, path);
            }

            if (newDir.exists() && newDir.isDirectory()) {
                currentDirectory = newDir.getCanonicalFile();
            } else {
                System.out.println("Directorio no valido");
            }

        } catch (IOException e) {
            System.out.println("Error cambiando directorio");
        }
    }

    private static void printHistory() {
        int index = 1;
        for (String entry : history) {
            System.out.println(index + " " + entry);
            index++;
        }
    }

    private static void listDirectory() {
        File[] entries = currentDirectory.listFiles();
        if (entries == null) {
            System.out.println("Error: no se puede listar el directorio");
            return;
        }

        Arrays.sort(entries, Comparator.comparing(File::getName));
        boolean isRoot = currentDirectory.getAbsolutePath().equals("/");

        for (File entry : entries) {
            String name = entry.getName();
            if (isRoot && !ROOT_VISIBLE.contains(name)) {
                continue;
            }
            System.out.println(name);
        }
    }

    private static List<String> tokenize(String input) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        char quote = 0;
        boolean escape = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (escape) {
                current.append(c);
                escape = false;
                continue;
            }

            if (c == '\\') {
                escape = true;
                continue;
            }

            if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                } else {
                    current.append(c);
                }
                continue;
            }

            if (c == '"' || c == '\'') {
                quote = c;
                continue;
            }

            if (Character.isWhitespace(c)) {
                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
                continue;
            }

            current.append(c);
        }

        if (quote != 0) {
            throw new IllegalArgumentException("Error: comillas no cerradas");
        }
        if (escape) {
            throw new IllegalArgumentException("Error: caracter de escape incompleto");
        }

        if (current.length() > 0) {
            tokens.add(current.toString());
        }

        return tokens;
    }

    private static boolean containsOperator(String line, String op) {
        char quote = 0;
        boolean escape = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (escape) {
                escape = false;
                continue;
            }

            if (c == '\\') {
                escape = true;
                continue;
            }

            if (quote != 0) {
                if (c == quote) quote = 0;
                continue;
            }

            if (c == '"' || c == '\'') {
                quote = c;
                continue;
            }

            if (line.startsWith(op, i)) {
                return true;
            }
        }

        return false;
    }

    private static List<String> splitByOperator(String line, String op) {
        List<String> parts = new ArrayList<>();
        char quote = 0;
        boolean escape = false;
        int start = 0;
        int i = 0;

        while (i < line.length()) {
            char c = line.charAt(i);

            if (escape) {
                escape = false;
                i++;
                continue;
            }

            if (c == '\\') {
                escape = true;
                i++;
                continue;
            }

            if (quote != 0) {
                if (c == quote) quote = 0;
                i++;
                continue;
            }

            if (c == '"' || c == '\'') {
                quote = c;
                i++;
                continue;
            }

            if (line.startsWith(op, i)) {
                parts.add(line.substring(start, i).trim());
                start = i + op.length();
                i += op.length();
                continue;
            }

            i++;
        }

        parts.add(line.substring(start).trim());
        return parts;
    }

    private static List<String> parseBackgroundCommands(String line) {
        if (!containsOperator(line, "^^")) {
            return null;
        }

        List<String> parts = splitByOperator(line, "^^");
        if (parts.isEmpty()) {
            System.out.println("Error: sintaxis invalida con ^^");
            return Collections.emptyList();
        }

        String last = parts.get(parts.size() - 1);
        if (!last.isEmpty()) {
            System.out.println("Error: cada comando en background debe terminar con ^^");
            return Collections.emptyList();
        }

        parts.remove(parts.size() - 1);

        for (String part : parts) {
            if (part.isEmpty()) {
                System.out.println("Error: comando vacio en background");
                return Collections.emptyList();
            }
        }

        return parts;
    }
}
