// maze.cs
using System;
using System.Collections.Generic;
using System.IO;
using System.Text.Json;
using System.Threading;
using System.Diagnostics;

class MazeGame
{
    static string Colorize(string text, string color)
    {
        string col = color switch
        {
            "red" => "\x1b[91m",
            "green" => "\x1b[92m",
            "yellow" => "\x1b[93m",
            "blue" => "\x1b[94m",
            "cyan" => "\x1b[96m",
            "gray" => "\x1b[90m",
            "bold" => "\x1b[1m",
            _ => "\x1b[0m"
        };
        return col + text + "\x1b[0m";
    }

    const int DEFAULT_SIZE = 10;
    const int DEFAULT_COINS = 5;

    static string RecordFile => Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), ".maze_records.json");

    static Dictionary<string, Record> LoadRecords()
    {
        try
        {
            string json = File.ReadAllText(RecordFile);
            return JsonSerializer.Deserialize<Dictionary<string, Record>>(json) ?? new Dictionary<string, Record>();
        }
        catch { return new Dictionary<string, Record>(); }
    }

    static void SaveRecords(Dictionary<string, Record> records)
    {
        string json = JsonSerializer.Serialize(records, new JsonSerializerOptions { WriteIndented = true });
        File.WriteAllText(RecordFile, json);
    }

    class Record
    {
        public int Steps { get; set; }
        public int Time { get; set; }
        public int Coins { get; set; }
    }

    static void ClearScreen() => Console.Clear();

    static int[,] GenerateMaze(int size)
    {
        var maze = new int[size, size];
        for (int i = 0; i < size; i++)
            for (int j = 0; j < size; j++)
                maze[i, j] = 1;

        var rand = new Random();
        void Dfs(int x, int y)
        {
            maze[x, y] = 0;
            var dirs = new (int dx, int dy)[] { (0, 2), (0, -2), (2, 0), (-2, 0) };
            for (int i = dirs.Length - 1; i > 0; i--)
            {
                int j = rand.Next(i + 1);
                var tmp = dirs[i];
                dirs[i] = dirs[j];
                dirs[j] = tmp;
            }
            foreach (var d in dirs)
            {
                int nx = x + d.dx, ny = y + d.dy;
                if (nx > 0 && nx < size - 1 && ny > 0 && ny < size - 1 && maze[nx, ny] == 1)
                {
                    maze[x + d.dx / 2, y + d.dy / 2] = 0;
                    Dfs(nx, ny);
                }
            }
        }
        Dfs(1, 1);
        maze[size - 2, size - 1] = 0;
        maze[size - 1, size - 1] = 0;
        return maze;
    }

    static List<(int, int)> PlaceCoins(int[,] maze, int numCoins, int size)
    {
        var coins = new List<(int, int)>();
        var rand = new Random();
        int attempts = 0;
        while (coins.Count < numCoins && attempts < 1000)
        {
            int x = rand.Next(1, size - 1);
            int y = rand.Next(1, size - 1);
            if (maze[x, y] == 0 && !coins.Contains((x, y)) && !(x == 1 && y == 1) && !(x == size - 2 && y == size - 1))
            {
                coins.Add((x, y));
            }
            attempts++;
        }
        return coins;
    }

    static List<(int, int)> FindPath(int[,] maze, (int, int) start, (int, int) end, int size)
    {
        var visited = new bool[size, size];
        var parent = new (int, int)[size, size];
        var queue = new Queue<(int, int)>();
        queue.Enqueue(start);
        visited[start.Item1, start.Item2] = true;
        var dirs = new (int, int)[] { (0, 1), (0, -1), (1, 0), (-1, 0) };
        while (queue.Count > 0)
        {
            var cur = queue.Dequeue();
            if (cur == end)
            {
                var path = new List<(int, int)>();
                var c = cur;
                while (c != start)
                {
                    path.Add(c);
                    c = parent[c.Item1, c.Item2];
                }
                path.Add(start);
                path.Reverse();
                return path;
            }
            foreach (var d in dirs)
            {
                int nx = cur.Item1 + d.Item1, ny = cur.Item2 + d.Item2;
                if (nx >= 0 && nx < size && ny >= 0 && ny < size && !visited[nx, ny] && maze[nx, ny] == 0)
                {
                    visited[nx, ny] = true;
                    parent[nx, ny] = cur;
                    queue.Enqueue((nx, ny));
                }
            }
        }
        return null;
    }

    static void DrawMaze(int[,] maze, (int, int) player, List<(int, int)> coins, int size, bool showPath, List<(int, int)> path)
    {
        ClearScreen();
        Console.WriteLine(Colorize("┌" + new string('─', size * 2 - 1) + "┐", "gray"));
        for (int i = 0; i < size; i++)
        {
            string line = "│";
            for (int j = 0; j < size; j++)
            {
                if (i == player.Item1 && j == player.Item2)
                    line += Colorize("@", "green");
                else if (showPath && path != null && path.Contains((i, j)))
                    line += Colorize(".", "cyan");
                else if (coins.Contains((i, j)))
                    line += Colorize("💎", "yellow");
                else if (maze[i, j] == 1)
                    line += Colorize("█", "gray");
                else
                    line += " ";
                if (j < size - 1) line += " ";
            }
            line += "│";
            Console.WriteLine(line);
        }
        Console.WriteLine(Colorize("└" + new string('─', size * 2 - 1) + "┘", "gray"));
    }

    static int Main(string[] args)
    {
        int size = DEFAULT_SIZE;
        int coinsCount = DEFAULT_COINS;
        bool showPath = false;

        for (int i = 0; i < args.Length; i++)
        {
            if (args[i] == "-s" && i + 1 < args.Length) size = int.Parse(args[++i]);
            else if (args[i] == "-c" && i + 1 < args.Length) coinsCount = int.Parse(args[++i]);
            else if (args[i] == "-p") showPath = true;
            else if (args[i] == "-h")
            {
                Console.WriteLine("Usage: maze [options]\n  -s <N>   Size (default 10)\n  -c <N>   Coins (default 5)\n  -p       Show path");
                return 0;
            }
        }
        if (size % 2 == 0) size++;
        if (size < 5) size = 5;

        var maze = GenerateMaze(size);
        var coins = PlaceCoins(maze, coinsCount, size);
        var start = (1, 1);
        var end = (size - 2, size - 1);
        var player = (1, 1);
        int steps = 0;
        var stopwatch = Stopwatch.StartNew();
        int collected = 0;
        var records = LoadRecords();
        string key = $"{size}x{size}";

        List<(int, int)> path = null;
        if (showPath) path = FindPath(maze, start, end, size);

        Console.CancelKeyPress += (sender, e) =>
        {
            Console.WriteLine(Colorize("\nИгра прервана.", "yellow"));
            Environment.Exit(0);
        };

        while (true)
        {
            DrawMaze(maze, player, coins, size, showPath, path);
            int elapsed = (int)stopwatch.Elapsed.TotalSeconds;
            Console.WriteLine(Colorize($"Шаги: {steps}  Время: {elapsed}с  Монеты: {collected}/{coins.Count}", "bold"));
            Console.WriteLine(Colorize("Управление: стрелки (WASD), Q - выход", "blue"));
            if (showPath && path != null) Console.WriteLine(Colorize("Кратчайший путь показан точками (cyan)", "yellow"));

            var keyInfo = Console.ReadKey(true);
            var keyChar = char.ToLower(keyInfo.KeyChar);
            int dx = 0, dy = 0;
            if (keyChar == 'q')
            {
                Console.WriteLine(Colorize("Выход из игры.", "yellow"));
                break;
            }
            if (keyInfo.Key == ConsoleKey.UpArrow || keyChar == 'w') dx = -1;
            else if (keyInfo.Key == ConsoleKey.DownArrow || keyChar == 's') dx = 1;
            else if (keyInfo.Key == ConsoleKey.LeftArrow || keyChar == 'a') dy = -1;
            else if (keyInfo.Key == ConsoleKey.RightArrow || keyChar == 'd') dy = 1;

            if (dx != 0 || dy != 0)
            {
                int nx = player.Item1 + dx;
                int ny = player.Item2 + dy;
                if (nx >= 0 && nx < size && ny >= 0 && ny < size && maze[nx, ny] == 0)
                {
                    player = (nx, ny);
                    steps++;
                    if (coins.Contains((nx, ny)))
                    {
                        coins.Remove((nx, ny));
                        collected++;
                    }
                    if (player == end)
                    {
                        elapsed = (int)stopwatch.Elapsed.TotalSeconds;
                        Console.WriteLine(Colorize($"🎉 Поздравляем! Вы вышли за {steps} шагов и {elapsed} секунд!", "green"));
                        Console.WriteLine(Colorize($"Собрано монет: {collected}", "yellow"));
                        int bonus = collected * 10;
                        Console.WriteLine(Colorize($"Бонус за монеты: +{bonus} очков", "yellow"));
                        int total = steps + bonus;
                        Console.WriteLine(Colorize($"Итоговый счёт: {total}", "bold"));
                        if (!records.ContainsKey(key) || steps < records[key].Steps)
                        {
                            records[key] = new Record { Steps = steps, Time = elapsed, Coins = collected };
                            SaveRecords(records);
                            Console.WriteLine(Colorize("🏆 Новый рекорд!", "green"));
                        }
                        Console.WriteLine(Colorize("Нажмите любую клавишу для выхода...", "yellow"));
                        Console.ReadKey(true);
                        return 0;
                    }
                }
            }
        }
        return 0;
    }
}
