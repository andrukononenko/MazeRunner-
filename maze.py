# maze.py
#!/usr/bin/env python3
# -*- coding: utf-8 -*-

import sys
import os
import random
import time
import json
import argparse
from pathlib import Path
import termios
import tty
import fcntl

# ANSI colors
COLORS = {
    'reset': '\033[0m',
    'red': '\033[91m',
    'green': '\033[92m',
    'yellow': '\033[93m',
    'blue': '\033[94m',
    'magenta': '\033[95m',
    'cyan': '\033[96m',
    'gray': '\033[90m',
    'bold': '\033[1m'
}

def colorize(text, color):
    return f"{COLORS.get(color, '')}{text}{COLORS['reset']}"

# Конфигурация
DEFAULT_SIZE = 10
DEFAULT_COINS = 5

RECORD_FILE = Path.home() / '.maze_records.json'

def load_records():
    if RECORD_FILE.exists():
        try:
            with open(RECORD_FILE, 'r') as f:
                return json.load(f)
        except:
            pass
    return {}

def save_records(records):
    with open(RECORD_FILE, 'w') as f:
        json.dump(records, f, indent=2)

def clear_screen():
    os.system('clear' if os.name == 'posix' else 'cls')

def get_key():
    fd = sys.stdin.fileno()
    old = termios.tcgetattr(fd)
    try:
        tty.setraw(fd)
        fcntl.fcntl(fd, fcntl.F_SETFL, os.O_NONBLOCK)
        ch = sys.stdin.read(1)
        if ch == '\x1b':
            # Стрелка
            ch2 = sys.stdin.read(2)
            if ch2 == '[A':
                return 'up'
            elif ch2 == '[B':
                return 'down'
            elif ch2 == '[D':
                return 'left'
            elif ch2 == '[C':
                return 'right'
            else:
                return ch + ch2
        return ch
    finally:
        termios.tcsetattr(fd, termios.TCSADRAIN, old)
        fcntl.fcntl(fd, fcntl.F_SETFL, 0)

def generate_maze(size):
    """Генерирует лабиринт размером (size+1)x(size+1) с использованием DFS."""
    # Создаём сетку: 1 - стена, 0 - проход
    maze = [[1] * (size + 1) for _ in range(size + 1)]
    # Убираем рамку
    for i in range(1, size, 2):
        for j in range(1, size, 2):
            maze[i][j] = 0

    # DFS
    def dfs(x, y):
        maze[x][y] = 0
        dirs = [(0, 2), (0, -2), (2, 0), (-2, 0)]
        random.shuffle(dirs)
        for dx, dy in dirs:
            nx, ny = x + dx, y + dy
            if 0 <= nx < size and 0 <= ny < size and maze[nx][ny] == 1:
                # Убираем стену между текущей и соседней клеткой
                maze[x + dx//2][y + dy//2] = 0
                dfs(nx, ny)

    # Запускаем из (1,1)
    dfs(1, 1)

    # Выход в правом нижнем углу
    maze[size-2][size-1] = 0  # проход к выходу
    maze[size-1][size-1] = 0  # выход

    return maze

def place_coins(maze, num_coins, size):
    coins = []
    max_attempts = 1000
    attempts = 0
    while len(coins) < num_coins and attempts < max_attempts:
        x = random.randint(1, size-2)
        y = random.randint(1, size-2)
        if maze[x][y] == 0 and (x, y) not in coins and (x, y) != (1, 1) and (x, y) != (size-2, size-1):
            coins.append((x, y))
        attempts += 1
    return coins

def find_path(maze, start, end, size):
    """BFS поиск кратчайшего пути (возвращает список координат)."""
    from collections import deque
    visited = [[False] * (size) for _ in range(size)]
    parent = [[None] * (size) for _ in range(size)]
    q = deque()
    q.append(start)
    visited[start[0]][start[1]] = True
    dirs = [(0, 1), (0, -1), (1, 0), (-1, 0)]
    while q:
        x, y = q.popleft()
        if (x, y) == end:
            # Восстанавливаем путь
            path = []
            cur = end
            while cur != start:
                path.append(cur)
                cur = parent[cur[0]][cur[1]]
            path.append(start)
            path.reverse()
            return path
        for dx, dy in dirs:
            nx, ny = x + dx, y + dy
            if 0 <= nx < size and 0 <= ny < size and not visited[nx][ny] and maze[nx][ny] == 0:
                visited[nx][ny] = True
                parent[nx][ny] = (x, y)
                q.append((nx, ny))
    return None

def draw_maze(maze, player_pos, coins, size, show_path=False, path=None):
    clear_screen()
    # Верхняя граница
    print(colorize('┌' + '─' * (size * 2 - 1) + '┐', 'gray'))
    for i in range(size):
        line = '│'
        for j in range(size):
            if (i, j) == player_pos:
                line += colorize('@', 'green')
            elif path and (i, j) in path and show_path:
                line += colorize('.', 'cyan')
            elif (i, j) in coins:
                line += colorize('💎', 'yellow')
            elif maze[i][j] == 1:
                line += colorize('█', 'gray')
            else:
                line += ' '
            if j < size - 1:
                line += ' '
        line += '│'
        print(line)
    print(colorize('└' + '─' * (size * 2 - 1) + '┘', 'gray'))

def main():
    parser = argparse.ArgumentParser(description="Maze Runner – игра Лабиринт")
    parser.add_argument('-s', '--size', type=int, default=DEFAULT_SIZE, help='Размер лабиринта (чётное)')
    parser.add_argument('-c', '--coins', type=int, default=DEFAULT_COINS, help='Количество монет')
    parser.add_argument('-p', '--show-path', action='store_true', help='Показать кратчайший путь')
    args = parser.parse_args()

    size = args.size
    if size % 2 == 0:
        size += 1  # делаем нечётным для правильной генерации
    if size < 5:
        size = 5

    maze = generate_maze(size)
    coins = place_coins(maze, args.coins, size)
    start = (1, 1)
    end = (size-2, size-1)
    player = list(start)
    steps = 0
    start_time = time.time()
    collected = 0

    records = load_records()
    key = f"{size}x{size}"

    path = find_path(maze, start, end, size)

    while True:
        draw_maze(maze, tuple(player), coins, size, args.show_path, path)
        elapsed = int(time.time() - start_time)
        print(colorize(f"Шаги: {steps}  Время: {elapsed}с  Монеты: {collected}/{len(coins)}", 'bold'))
        print(colorize("Управление: стрелки (WASD), Q - выход", 'blue'))
        if args.show_path and path:
            print(colorize("Кратчайший путь показан точками (cyan)", 'yellow'))

        key = get_key()
        if key == 'q' or key == 'Q':
            print(colorize("Выход из игры.", 'yellow'))
            break

        dx, dy = 0, 0
        if key == 'up' or key == 'w' or key == 'W':
            dx, dy = -1, 0
        elif key == 'down' or key == 's' or key == 'S':
            dx, dy = 1, 0
        elif key == 'left' or key == 'a' or key == 'A':
            dx, dy = 0, -1
        elif key == 'right' or key == 'd' or key == 'D':
            dx, dy = 0, 1

        if dx != 0 or dy != 0:
            nx, ny = player[0] + dx, player[1] + dy
            if 0 <= nx < size and 0 <= ny < size and maze[nx][ny] == 0:
                player[0], player[1] = nx, ny
                steps += 1
                # Проверка сбора монет
                if (nx, ny) in coins:
                    coins.remove((nx, ny))
                    collected += 1
                # Проверка выхода
                if (nx, ny) == end:
                    elapsed = int(time.time() - start_time)
                    print(colorize(f"🎉 Поздравляем! Вы вышли из лабиринта за {steps} шагов и {elapsed} секунд!", 'green'))
                    print(colorize(f"Собрано монет: {collected}", 'yellow'))
                    bonus = collected * 10
                    print(colorize(f"Бонус за монеты: +{bonus} очков", 'yellow'))
                    total = steps + bonus
                    print(colorize(f"Итоговый счёт: {total}", 'bold'))
                    # Сохранение рекорда
                    if key not in records:
                        records[key] = {'steps': steps, 'time': elapsed, 'coins': collected}
                    else:
                        if steps < records[key]['steps']:
                            records[key]['steps'] = steps
                            records[key]['time'] = elapsed
                            records[key]['coins'] = collected
                    save_records(records)
                    print(colorize("Нажмите любую клавишу для продолжения...", 'yellow'))
                    get_key()
                    sys.exit(0)

if __name__ == '__main__':
    try:
        main()
    except KeyboardInterrupt:
        print(colorize("\nИгра прервана.", 'yellow'))
        sys.exit(0)
