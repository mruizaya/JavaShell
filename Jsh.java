import java.io.*;
import java.util.*;

public class Jsh {

    private static File currentDirectory = new File("/");
    private static Scanner sc = new Scanner(System.in);

    public static void main(String[] args) {

        boolean running = true;

        while (running) {

            System.out.print("jsh:" + currentDirectory.getAbsolutePath() + ">> ");
            String input = sc.nextLine().trim();

            if (input.isEmpty()) continue;

            // ===== BUILTINS =====

            if (input.equals("exit")) {
                running = false;
                continue;
            }

            if (input.equals("pwd")) {
                System.out.println(currentDirectory.getAbsolutePath());
                continue;
            }

            if (input.startsWith("cd")) {
                String[] parts = input.split("\\s+", 2);

                if (parts.length == 1) {
                    changeDirectory("/");
                } else {
                    changeDirectory(parts[1]);
                }
                continue;
            }

            // ===== EJECUTAR COMANDO =====
            executeCommand(input);
        }

        sc.close();
    }

    private static void changeDirectory(String path) {
        try {

            File newDir;

            if (path.equals("..")) {
                newDir = currentDirectory.getParentFile();
                if (newDir == null) return;
            }
            else if (path.startsWith("/")) {
                newDir = new File(path);
            } 
            else {
                newDir = new File(currentDirectory, path);
            }

            if (newDir.exists() && newDir.isDirectory()) {
                currentDirectory = newDir.getCanonicalFile();
            } else {
                System.out.println("Directorio no válido");
            }

        } catch (IOException e) {
            System.out.println("Error cambiando directorio");
        }
    }

    private static void executeCommand(String command) {

        try {

            // ===== PIPES =====
            if (command.contains("|")) {
                executePipe(command);
                return;
            }

            // ===== REDIRECCIONES (>, >>, <) =====
            if (command.contains(">") || command.contains("<")) {
                executeRedirection(command);
                return;
            }

            // ===== BACKGROUND =====
            boolean background = command.endsWith("&");

            if (background) {
                command = command.substring(0, command.length() - 1).trim();
            }

            String[] cmdParts = command.split("\\s+");

            ProcessBuilder pb = new ProcessBuilder(cmdParts);
            pb.directory(currentDirectory);
            pb.redirectErrorStream(true);

            Process process = pb.start();

            if (!background) {

                BufferedReader reader =
                        new BufferedReader(new InputStreamReader(process.getInputStream()));

                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println(line);
                }

                int exitCode = process.waitFor();
                System.out.println("[Proceso terminado con código " + exitCode + "]");

            } else {
                System.out.println("[Proceso ejecutándose en background]");
            }

        } catch (Exception e) {
            System.out.println("Error ejecutando comando: " + e.getMessage());
        }
    }

    // ===== REDIRECCIONES =====

    // ===== REDIRECCIONES =====

private static void executeRedirection(String command) {

    try {

        boolean append = command.contains(">>");
        String[] parts;

        if (append) {
            parts = command.split(">>", 2);
        } 
        else if (command.contains(">")) {
            parts = command.split(">", 2);
        } 
        else {
            parts = command.split("<", 2);
        }

        String left = parts[0].trim();
        String right = parts[1].trim();

        String[] cmdParts = left.split("\\s+");

        ProcessBuilder pb = new ProcessBuilder(cmdParts);
        pb.directory(currentDirectory);
        pb.redirectErrorStream(true);

        File file = new File(currentDirectory, right);

        // ===== OUTPUT REDIRECTION =====
        if (command.contains(">")) {

            if (append) {
                pb.redirectOutput(ProcessBuilder.Redirect.appendTo(file));
            } else {
                pb.redirectOutput(file);
            }
        }
        // ===== INPUT REDIRECTION =====
        else if (command.contains("<")) {

            pb.redirectInput(file);
        }

        Process process = pb.start();

        // 🔥 SOLUCIÓN CLAVE:
        // Si NO hay redirección de entrada, cerrar STDIN del proceso
        if (!command.contains("<")) {
            process.getOutputStream().close();
        }

        process.waitFor();

        System.out.println("[Redirección completada]");

    } catch (Exception e) {
        System.out.println("Error en redirección: " + e.getMessage());
    }
}


    // ===== PIPES CON REDIRECCIONES CORREGIDO =====

private static void executePipe(String command) {

    try {

        String[] commands = command.split("\\|");
        Process previousProcess = null;
        Process currentProcess = null;

        for (int i = 0; i < commands.length; i++) {

            String cmd = commands[i].trim();

            boolean append = false;
            boolean outputRedirect = false;
            boolean inputRedirect = false;

            File redirectFile = null;

            String actualCommand = cmd;

            // ===== DETECTAR >> PRIMERO =====
            if (cmd.contains(">>")) {
                append = true;
                outputRedirect = true;

                String[] parts = cmd.split(">>", 2);
                actualCommand = parts[0].trim();
                redirectFile = new File(currentDirectory, parts[1].trim());
            }

            // ===== DETECTAR > =====
            else if (cmd.contains(">")) {
                outputRedirect = true;

                String[] parts = cmd.split(">", 2);
                actualCommand = parts[0].trim();
                redirectFile = new File(currentDirectory, parts[1].trim());
            }

            // ===== DETECTAR < =====
            else if (cmd.contains("<")) {
                inputRedirect = true;

                String[] parts = cmd.split("<", 2);
                actualCommand = parts[0].trim();
                redirectFile = new File(currentDirectory, parts[1].trim());
            }

            String[] cmdParts = actualCommand.split("\\s+");

            ProcessBuilder pb = new ProcessBuilder(cmdParts);
            pb.directory(currentDirectory);
            pb.redirectErrorStream(true);

            // ===== INPUT REDIRECTION =====
            if (inputRedirect && redirectFile != null) {
                pb.redirectInput(redirectFile);
            }

            // ===== OUTPUT REDIRECTION SOLO EN ÚLTIMO =====
            if (i == commands.length - 1 && outputRedirect && redirectFile != null) {

                if (append) {
                    pb.redirectOutput(ProcessBuilder.Redirect.appendTo(redirectFile));
                } else {
                    pb.redirectOutput(redirectFile);
                }
            }

            currentProcess = pb.start();

            // ===== CONECTAR PIPE =====
            if (previousProcess != null) {

                InputStream prevOut = previousProcess.getInputStream();
                OutputStream currIn = currentProcess.getOutputStream();

                Thread pipeThread = new Thread(() -> {
                    try {
                        prevOut.transferTo(currIn);
                        currIn.close();
                    } catch (IOException ignored) {}
                });

                pipeThread.start();
            }

            previousProcess = currentProcess;
        }

        // Mostrar salida solo si no hay redirección final
        BufferedReader reader =
                new BufferedReader(new InputStreamReader(previousProcess.getInputStream()));

        String line;
        while ((line = reader.readLine()) != null) {
            System.out.println(line);
        }

        previousProcess.waitFor();

    } catch (Exception e) {
        System.out.println("Error ejecutando pipe: " + e.getMessage());
    }
}


}
