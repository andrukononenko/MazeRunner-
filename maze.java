// maze.java
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

public class maze {
    private static final String RESET = "\u001B[0m";
    private static final String RED = "\u001B[91m";
    private static final String GREEN = "\u001B[92m";
    private static final String YELLOW = "\u001B[93m";
    private static final String BLUE = "\u001B[94m";
    private static final String CYAN = "\u001B[96m";
    private static final String GRAY = "\u001B[90m";
    private static final String BOLD = "\u001B[1m";

    private static String colorize(String text, String color) {
        return color + text + RESET;
    }

    private static final int DEFAULT_SIZE = 10;
    private static final int DEFAULT_COINS = 5;
    private static String configFile = System.getProperty("user.home") + "/.maze_records.json";

    static class Record {
        int steps;
        int time;
        int coins;
    }

    private static Map<String, Record> loadRecords() throws IOException {
        Path path = Paths.get(configFile);
        if (!Files.exists(path)) return new HashMap<>();
        String json = new String(Files.readAllBytes(path));
        // Используем простой парсинг без библиотек (для упрощения)
        // В реальном проекте лучше использовать Gson.
        // Для демонстрации реализуем вручную (упрощённо)
        Map<String, Record> records = new HashMap<>();
        // Парсим JSON вручную (это небезопасно, но для демонстрации сойдёт)
        // Лучше использовать библиотеку, но здесь мы опустим для краткости.
        return records;
    }

    private static void saveRecords(Map<String, Record> records) throws IOException {
        // Для простоты сохраняем пустой JSON
        Files.write(Paths.get(configFile), "{}".getBytes());
    }

    private static void clearScreen() {
        System.out.print("\033[H\033[2J");
        System.out.flush();
    }

    private static int[][] generateMaze(int size) {
        int[][] maze = new int[size][size];
        for (int i = 0; i < size; i++) Arrays.fill(maze[i], 1);
        Random rand = new Random();
        class DFS {
            void visit(int x, int y) {
                maze[x][y] = 0;
                int[][] dirs = {{0,2},{0,-2},{2,0},{-2,0}};
                // перемешиваем
                for (int i = dirs.length-1; i > 0; i--) {
                    int j = rand.nextInt(i+1);
                    int[] tmp = dirs[i]; dirs[i] = dirs[j]; dirs[j] = tmp;
                }
                for (int[] d : dirs) {
                    int nx = x + d[0], ny = y + d[1];
                    if (nx > 0 && nx < size-1 && ny > 0 && ny < size-1 && maze[nx][ny] == 1) {
                        maze[x + d[0]/2][y + d[1]/2] = 0;
                        visit(nx, ny);
                    }
                }
            }
        }
        new DFS().visit(1, 1);
        maze[size-2][size-1] = 0;
        maze[size-1][size-1] = 0;
        return maze;
    }

    private static List<int[]> placeCoins(int[][] maze, int numCoins, int size) {
        List<int[]> coins = new ArrayList<>();
        Random rand = new Random();
        int attempts = 0;
        while (coins.size() < numCoins && attempts < 1000) {
            int x = rand.nextInt(size-2) + 1;
            int y = rand.nextInt(size-2) + 1;
            boolean exists = false;
            for (int[] c : coins) if (c[0] == x && c[1] == y) { exists = true; break; }
            if (maze[x][y] == 0 && !exists && !(x == 1 && y == 1) && !(x == size-2 && y == size-1)) {
                coins.add(new int[]{x, y});
            }
            attempts++;
        }
        return coins;
    }

    private static List<int[]> findPath(int[][] maze, int[] start, int[] end, int size) {
        boolean[][] visited = new boolean[size][size];
        int[][] parent = new int[size][size];
        Queue<int[]> queue = new LinkedList<>();
        queue.add(start);
        visited[start[0]][start[1]] = true;
        int[][] dirs = {{0,1},{0,-1},{1,0},{-1,0}};
        while (!queue.isEmpty()) {
            int[] cur = queue.poll();
            if (cur[0] == end[0] && cur[1] == end[1]) {
                List<int[]> path = new ArrayList<>();
                int[] c = cur;
                while (c != start) {
                    path.add(c);
                    int[] p = new int[2];
                    p[0] = parent[c[0]][c[1]];
                    p[1] = parent[c[0]][c[1]];
                    c = p;
                }
                path.add(start);
                Collections.reverse(path);
                return path;
            }
            for (int[] d : dirs) {
                int nx = cur[0] + d[0], ny = cur[1] + d[1];
                if (nx >= 0 && nx < size && ny >= 0 && ny < size && !visited[nx][ny] && maze[nx][ny] == 0) {
                    visited[nx][ny] = true;
                    parent[nx][ny] = cur[0];
                    parent[nx][ny] = cur[1];
                    queue.add(new int[]{nx, ny});
                }
            }
        }
        return null;
    }

    private static void drawMaze(int[][] maze, int[] player, List<int[]> coins, int size, boolean showPath, List<int[]> path) {
        clearScreen();
        System.out.println(colorize("┌" + "─".repeat(size*2-1) + "┐", GRAY));
        for (int i = 0; i < size; i++) {
            StringBuilder line = new StringBuilder("│");
            for (int j = 0; j < size; j++) {
                boolean isPlayer = (i == player[0] && j == player[1]);
                boolean isPath = showPath && path != null && path.stream().anyMatch(p -> p[0] == i && p[1] == j);
                boolean isCoin = coins.stream().anyMatch(c -> c[0] == i && c[1] == j);
                if (isPlayer) {
                    line.append(colorize("@", GREEN));
                } else if (isPath) {
                    line.append(colorize(".", CYAN));
                } else if (isCoin) {
                    line.append(colorize("💎", YELLOW));
                } else if (maze[i][j] == 1) {
                    line.append(colorize("█", GRAY));
                } else {
                    line.append(" ");
                }
                if (j < size-1) line.append(" ");
            }
            line.append("│");
            System.out.println(line);
        }
        System.out.println(colorize("└" + "─".repeat(size*2-1) + "┘", GRAY));
    }

    private static int getKey() throws IOException {
        // Упрощённый ввод с чтением одного символа (работает в Unix)
        // В Windows может не работать.
        if (System.in.available() > 0) {
            return System.in.read();
        }
        return -1;
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        int size = DEFAULT_SIZE;
        int coinsCount = DEFAULT_COINS;
        boolean showPath = false;

        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-s") && i+1 < args.length) size = Integer.parseInt(args[++i]);
            else if (args[i].equals("-c") && i+1 < args.length) coinsCount = Integer.parseInt(args[++i]);
            else if (args[i].equals("-p")) showPath = true;
            else if (args[i].equals("-h")) {
                System.out.println("Usage: java maze [options]\n  -s <N>   Size (default 10)\n  -c <N>   Coins (default 5)\n  -p       Show path");
                return;
            }
        }
        if (size % 2 == 0) size++;
        if (size < 5) size = 5;

        int[][] maze = generateMaze(size);
        List<int[]> coins = placeCoins(maze, coinsCount, size);
        int[] start = {1, 1};
        int[] end = {size-2, size-1};
        int[] player = {1, 1};
        int steps = 0;
        long startTime = System.currentTimeMillis();
        int collected = 0;
        Map<String, Record> records = loadRecords();
        String key = size + "x" + size;

        List<int[]> path = null;
        if (showPath) path = findPath(maze, start, end, size);

        // Настраиваем терминал для неблокирующего ввода (только Unix)
        // В Windows потребуется использовать JNA, но для демонстрации оставим так.

        while (true) {
            drawMaze(maze, player, coins, size, showPath, path);
            int elapsed = (int)((System.currentTimeMillis() - startTime) / 1000);
            System.out.println(colorize(String.format("Шаги: %d  Время: %ds  Монеты: %d/%d", steps, elapsed, collected, coins.size()), BOLD));
            System.out.println(colorize("Управление: стрелки (WASD), Q - выход", BLUE));
            if (showPath && path != null) System.out.println(colorize("Кратчайший путь показан точками (cyan)", YELLOW));

            int keyCode = -1;
            try {
                keyCode = getKey();
            } catch (IOException e) {}
            int dx = 0, dy = 0;
            if (keyCode == 'q' || keyCode == 'Q') {
                System.out.println(colorize("Выход из игры.", YELLOW));
                return;
            }
            // В реальной игре нужно обрабатывать escape-последовательности, но здесь упростим.
            // Для простоты будем использовать WASD (без стрелок).
            if (keyCode == 'w' || keyCode == 'W') dx = -1;
            else if (keyCode == 's' || keyCode == 'S') dx = 1;
            else if (keyCode == 'a' || keyCode == 'A') dy = -1;
            else if (keyCode == 'd' || keyCode == 'D') dy = 1;

            if (dx != 0 || dy != 0) {
                int nx = player[0] + dx;
                int ny = player[1] + dy;
                if (nx >= 0 && nx < size && ny >= 0 && ny < size && maze[nx][ny] == 0) {
                    player[0] = nx;
                    player[1] = ny;
                    steps++;
                    // Проверка монет
                    for (Iterator<int[]> it = coins.iterator(); it.hasNext(); ) {
                        int[] c = it.next();
                        if (c[0] == nx && c[1] == ny) {
                            it.remove();
                            collected++;
                            break;
                        }
                    }
                    if (player[0] == end[0] && player[1] == end[1]) {
                        elapsed = (int)((System.currentTimeMillis() - startTime) / 1000);
                        System.out.println(colorize("🎉 Поздравляем! Вы вышли за " + steps + " шагов и " + elapsed + " секунд!", GREEN));
                        System.out.println(colorize("Собрано монет: " + collected, YELLOW));
                        int bonus = collected * 10;
                        System.out.println(colorize("Бонус за монеты: +" + bonus + " очков", YELLOW));
                        int total = steps + bonus;
                        System.out.println(colorize("Итоговый счёт: " + total, BOLD));
                        // Рекорды
                        Record rec = records.get(key);
                        if (rec == null || steps < rec.steps) {
                            Record r = new Record();
                            r.steps = steps;
                            r.time = elapsed;
                            r.coins = collected;
                            records.put(key, r);
                            saveRecords(records);
                            System.out.println(colorize("🏆 Новый рекорд!", GREEN));
                        }
                        System.out.println(colorize("Нажмите любую клавишу для выхода...", YELLOW));
                        System.in.read();
                        return;
                    }
                }
            }
            Thread.sleep(30);
        }
    }
}
