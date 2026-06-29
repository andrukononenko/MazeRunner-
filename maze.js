// maze.js
#!/usr/bin/env node
'use strict';

const readline = require('readline');
const fs = require('fs');
const path = require('path');
const os = require('os');

const COLORS = {
    reset: '\x1b[0m',
    red: '\x1b[91m',
    green: '\x1b[92m',
    yellow: '\x1b[93m',
    blue: '\x1b[94m',
    cyan: '\x1b[96m',
    gray: '\x1b[90m',
    bold: '\x1b[1m'
};

function colorize(text, color) {
    return COLORS[color] + text + COLORS.reset;
}

const DEFAULT_SIZE = 10;
const DEFAULT_COINS = 5;
const RECORD_FILE = path.join(os.homedir(), '.maze_records.json');

function loadRecords() {
    try {
        return JSON.parse(fs.readFileSync(RECORD_FILE, 'utf8'));
    } catch { return {}; }
}

function saveRecords(records) {
    fs.writeFileSync(RECORD_FILE, JSON.stringify(records, null, 2));
}

function clearScreen() {
    console.clear();
}

function generateMaze(size) {
    const maze = Array.from({ length: size }, () => Array(size).fill(1));
    const dfs = (x, y) => {
        maze[x][y] = 0;
        const dirs = [[0, 2], [0, -2], [2, 0], [-2, 0]];
        for (let i = dirs.length - 1; i > 0; i--) {
            const j = Math.floor(Math.random() * (i + 1));
            [dirs[i], dirs[j]] = [dirs[j], dirs[i]];
        }
        for (const [dx, dy] of dirs) {
            const nx = x + dx;
            const ny = y + dy;
            if (nx > 0 && nx < size-1 && ny > 0 && ny < size-1 && maze[nx][ny] === 1) {
                maze[x + dx/2][y + dy/2] = 0;
                dfs(nx, ny);
            }
        }
    };
    dfs(1, 1);
    maze[size-2][size-1] = 0;
    maze[size-1][size-1] = 0;
    return maze;
}

function placeCoins(maze, numCoins, size) {
    const coins = [];
    let attempts = 0;
    while (coins.length < numCoins && attempts < 1000) {
        const x = Math.floor(Math.random() * (size-2)) + 1;
        const y = Math.floor(Math.random() * (size-2)) + 1;
        if (maze[x][y] === 0 && !coins.some(c => c[0] === x && c[1] === y) &&
            !(x === 1 && y === 1) && !(x === size-2 && y === size-1)) {
            coins.push([x, y]);
        }
        attempts++;
    }
    return coins;
}

function findPath(maze, start, end, size) {
    const visited = Array.from({ length: size }, () => Array(size).fill(false));
    const parent = Array.from({ length: size }, () => Array(size).fill(null));
    const queue = [start];
    visited[start[0]][start[1]] = true;
    const dirs = [[0, 1], [0, -1], [1, 0], [-1, 0]];
    while (queue.length) {
        const [x, y] = queue.shift();
        if (x === end[0] && y === end[1]) {
            const path = [];
            let cur = [x, y];
            while (cur) {
                path.unshift(cur);
                cur = parent[cur[0]][cur[1]];
            }
            return path;
        }
        for (const [dx, dy] of dirs) {
            const nx = x + dx;
            const ny = y + dy;
            if (nx >= 0 && nx < size && ny >= 0 && ny < size && !visited[nx][ny] && maze[nx][ny] === 0) {
                visited[nx][ny] = true;
                parent[nx][ny] = [x, y];
                queue.push([nx, ny]);
            }
        }
    }
    return null;
}

function drawMaze(maze, player, coins, size, showPath, path) {
    clearScreen();
    console.log(colorize('┌' + '─'.repeat(size * 2 - 1) + '┐', 'gray'));
    for (let i = 0; i < size; i++) {
        let line = '│';
        for (let j = 0; j < size; j++) {
            if (i === player[0] && j === player[1]) {
                line += colorize('@', 'green');
            } else if (showPath && path && path.some(p => p[0] === i && p[1] === j)) {
                line += colorize('.', 'cyan');
            } else if (coins.some(c => c[0] === i && c[1] === j)) {
                line += colorize('💎', 'yellow');
            } else if (maze[i][j] === 1) {
                line += colorize('█', 'gray');
            } else {
                line += ' ';
            }
            if (j < size - 1) line += ' ';
        }
        line += '│';
        console.log(line);
    }
    console.log(colorize('└' + '─'.repeat(size * 2 - 1) + '┘', 'gray'));
}

function getKey() {
    return new Promise(resolve => {
        readline.emitKeypressEvents(process.stdin);
        process.stdin.setRawMode(true);
        process.stdin.once('keypress', (str, key) => {
            resolve({ str, key });
        });
    });
}

async function main() {
    const args = process.argv.slice(2);
    let size = DEFAULT_SIZE;
    let coinsCount = DEFAULT_COINS;
    let showPath = false;

    for (let i = 0; i < args.length; i++) {
        if (args[i] === '-s' && i+1 < args.length) {
            size = parseInt(args[++i]);
        } else if (args[i] === '-c' && i+1 < args.length) {
            coinsCount = parseInt(args[++i]);
        } else if (args[i] === '-p') {
            showPath = true;
        } else if (args[i] === '-h') {
            console.log('Usage: node maze.js [options]\n  -s <N>   Size (default 10)\n  -c <N>   Coins (default 5)\n  -p       Show path');
            process.exit(0);
        }
    }
    if (size % 2 === 0) size++;
    if (size < 5) size = 5;

    const maze = generateMaze(size);
    const coins = placeCoins(maze, coinsCount, size);
    const start = [1, 1];
    const end = [size-2, size-1];
    let player = [1, 1];
    let steps = 0;
    const startTime = Date.now();
    let collected = 0;
    const records = loadRecords();
    const keyStr = `${size}x${size}`;

    let path = null;
    if (showPath) {
        path = findPath(maze, start, end, size);
    }

    process.stdin.setRawMode(true);
    process.stdin.resume();

    while (true) {
        drawMaze(maze, player, coins, size, showPath, path);
        const elapsed = Math.floor((Date.now() - startTime) / 1000);
        console.log(colorize(`Шаги: ${steps}  Время: ${elapsed}с  Монеты: ${collected}/${coins.length}`, 'bold'));
        console.log(colorize('Управление: стрелки (WASD), Q - выход', 'blue'));
        if (showPath && path) console.log(colorize('Кратчайший путь показан точками (cyan)', 'yellow'));

        const { str, key } = await getKey();
        let dx = 0, dy = 0;
        const name = key ? key.name : null;
        if (name === 'q' || name === 'Q' || str === 'q' || str === 'Q') {
            console.log(colorize('Выход из игры.', 'yellow'));
            process.exit(0);
        }
        if (name === 'up' || str === 'w' || str === 'W') dx = -1;
        else if (name === 'down' || str === 's' || str === 'S') dx = 1;
        else if (name === 'left' || str === 'a' || str === 'A') dy = -1;
        else if (name === 'right' || str === 'd' || str === 'D') dy = 1;

        if (dx !== 0 || dy !== 0) {
            const nx = player[0] + dx;
            const ny = player[1] + dy;
            if (nx >= 0 && nx < size && ny >= 0 && ny < size && maze[nx][ny] === 0) {
                player = [nx, ny];
                steps++;
                // монеты
                const idx = coins.findIndex(c => c[0] === nx && c[1] === ny);
                if (idx !== -1) {
                    coins.splice(idx, 1);
                    collected++;
                }
                if (player[0] === end[0] && player[1] === end[1]) {
                    const elapsedFinal = Math.floor((Date.now() - startTime) / 1000);
                    console.log(colorize(`🎉 Поздравляем! Вы вышли за ${steps} шагов и ${elapsedFinal} секунд!`, 'green'));
                    console.log(colorize(`Собрано монет: ${collected}`, 'yellow'));
                    const bonus = collected * 10;
                    console.log(colorize(`Бонус за монеты: +${bonus} очков`, 'yellow'));
                    const total = steps + bonus;
                    console.log(colorize(`Итоговый счёт: ${total}`, 'bold'));
                    if (!records[keyStr] || steps < records[keyStr].steps) {
                        records[keyStr] = { steps, time: elapsedFinal, coins: collected };
                        saveRecords(records);
                        console.log(colorize('🏆 Новый рекорд!', 'green'));
                    }
                    console.log(colorize('Нажмите любую клавишу для выхода...', 'yellow'));
                    await getKey();
                    process.exit(0);
                }
            }
        }
    }
}

main().catch(console.error);
