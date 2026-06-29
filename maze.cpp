// maze.cpp
#include <iostream>
#include <vector>
#include <random>
#include <chrono>
#include <thread>
#include <string>
#include <fstream>
#include <json/json.h>
#include <termios.h>
#include <unistd.h>
#include <fcntl.h>

using namespace std;

const string RESET = "\033[0m";
const string RED = "\033[91m";
const string GREEN = "\033[92m";
const string YELLOW = "\033[93m";
const string BLUE = "\033[94m";
const string CYAN = "\033[96m";
const string GRAY = "\033[90m";
const string BOLD = "\033[1m";

string colorize(const string& text, const string& color) {
    return color + text + RESET;
}

const int DEFAULT_SIZE = 10;
const int DEFAULT_COINS = 5;

string getHomeDir() {
    const char* home = getenv("HOME");
    if (!home) home = getenv("USERPROFILE");
    return string(home);
}

Json::Value loadRecords() {
    ifstream f(getHomeDir() + "/.maze_records.json");
    Json::Value root;
    if (!f) return root;
    f >> root;
    return root;
}

void saveRecords(const Json::Value& records) {
    ofstream f(getHomeDir() + "/.maze_records.json");
    f << records.toStyledString();
}

void clearScreen() {
    cout << "\033[2J\033[1;1H";
}

vector<vector<int>> generateMaze(int size) {
    vector<vector<int>> maze(size, vector<int>(size, 1));
    random_device rd;
    mt19937 g(rd());

    function<void(int,int)> dfs = [&](int x, int y) {
        maze[x][y] = 0;
        vector<pair<int,int>> dirs = {{0,2},{0,-2},{2,0},{-2,0}};
        shuffle(dirs.begin(), dirs.end(), g);
        for (auto& d : dirs) {
            int nx = x + d.first, ny = y + d.second;
            if (nx > 0 && nx < size-1 && ny > 0 && ny < size-1 && maze[nx][ny] == 1) {
                maze[x + d.first/2][y + d.second/2] = 0;
                dfs(nx, ny);
            }
        }
    };
    dfs(1, 1);
    maze[size-2][size-1] = 0;
    maze[size-1][size-1] = 0;
    return maze;
}

vector<pair<int,int>> placeCoins(const vector<vector<int>>& maze, int numCoins, int size) {
    vector<pair<int,int>> coins;
    random_device rd;
    mt19937 g(rd());
    uniform_int_distribution<int> dist(1, size-2);
    int attempts = 0;
    while ((int)coins.size() < numCoins && attempts < 1000) {
        int x = dist(g), y = dist(g);
        if (maze[x][y] == 0 && find(coins.begin(), coins.end(), make_pair(x,y)) == coins.end() &&
            !(x == 1 && y == 1) && !(x == size-2 && y == size-1)) {
            coins.push_back({x, y});
        }
        attempts++;
    }
    return coins;
}

vector<pair<int,int>> findPath(const vector<vector<int>>& maze, pair<int,int> start, pair<int,int> end, int size) {
    vector<vector<bool>> visited(size, vector<bool>(size, false));
    vector<vector<pair<int,int>>> parent(size, vector<pair<int,int>>(size, {-1,-1}));
    queue<pair<int,int>> q;
    q.push(start);
    visited[start.first][start.second] = true;
    vector<pair<int,int>> dirs = {{0,1},{0,-1},{1,0},{-1,0}};
    while (!q.empty()) {
        auto cur = q.front(); q.pop();
        if (cur == end) {
            vector<pair<int,int>> path;
            while (cur != start) {
                path.push_back(cur);
                cur = parent[cur.first][cur.second];
            }
            path.push_back(start);
            reverse(path.begin(), path.end());
            return path;
        }
        for (auto& d : dirs) {
            int nx = cur.first + d.first, ny = cur.second + d.second;
            if (nx >= 0 && nx < size && ny >= 0 && ny < size && !visited[nx][ny] && maze[nx][ny] == 0) {
                visited[nx][ny] = true;
                parent[nx][ny] = cur;
                q.push({nx, ny});
            }
        }
    }
    return {};
}

void drawMaze(const vector<vector<int>>& maze, pair<int,int> player, const vector<pair<int,int>>& coins, int size, bool showPath, const vector<pair<int,int>>& path) {
    clearScreen();
    cout << colorize("┌" + string(size*2-1, '─') + "┐", GRAY) << endl;
    for (int i = 0; i < size; ++i) {
        string line = "│";
        for (int j = 0; j < size; ++j) {
            if (i == player.first && j == player.second) {
                line += colorize("@", GREEN);
            } else if (showPath && find(path.begin(), path.end(), make_pair(i,j)) != path.end()) {
                line += colorize(".", CYAN);
            } else if (find(coins.begin(), coins.end(), make_pair(i,j)) != coins.end()) {
                line += colorize("💎", YELLOW);
            } else if (maze[i][j] == 1) {
                line += colorize("█", GRAY);
            } else {
                line += " ";
            }
            if (j < size-1) line += " ";
        }
        line += "│";
        cout << line << endl;
    }
    cout << colorize("└" + string(size*2-1, '─') + "┘", GRAY) << endl;
}

char getch() {
    struct termios oldt, newt;
    char ch;
    tcgetattr(STDIN_FILENO, &oldt);
    newt = oldt;
    newt.c_lflag &= ~(ICANON | ECHO);
    tcsetattr(STDIN_FILENO, TCSANOW, &newt);
    ch = getchar();
    tcsetattr(STDIN_FILENO, TCSANOW, &oldt);
    return ch;
}

bool kbhit() {
    struct termios oldt, newt;
    int oldf;
    tcgetattr(STDIN_FILENO, &oldt);
    newt = oldt;
    newt.c_lflag &= ~(ICANON | ECHO);
    tcsetattr(STDIN_FILENO, TCSANOW, &newt);
    oldf = fcntl(STDIN_FILENO, F_GETFL, 0);
    fcntl(STDIN_FILENO, F_SETFL, oldf | O_NONBLOCK);
    int ch = getchar();
    tcsetattr(STDIN_FILENO, TCSANOW, &oldt);
    fcntl(STDIN_FILENO, F_SETFL, oldf);
    if (ch != EOF) {
        ungetc(ch, stdin);
        return true;
    }
    return false;
}

int main(int argc, char* argv[]) {
    int size = DEFAULT_SIZE;
    int coinsCount = DEFAULT_COINS;
    bool showPath = false;

    for (int i = 1; i < argc; ++i) {
        string arg = argv[i];
        if (arg == "-s" && i+1 < argc) {
            size = stoi(argv[++i]);
        } else if (arg == "-c" && i+1 < argc) {
            coinsCount = stoi(argv[++i]);
        } else if (arg == "-p") {
            showPath = true;
        } else if (arg == "-h") {
            cout << "Usage: maze [options]\n  -s <N>   Size (default 10)\n  -c <N>   Coins (default 5)\n  -p       Show path\n";
            return 0;
        }
    }
    if (size % 2 == 0) size++;
    if (size < 5) size = 5;

    auto maze = generateMaze(size);
    auto coins = placeCoins(maze, coinsCount, size);
    pair<int,int> start = {1,1};
    pair<int,int> end = {size-2, size-1};
    pair<int,int> player = {1,1};
    int steps = 0;
    auto startTime = chrono::steady_clock::now();
    int collected = 0;
    auto records = loadRecords();
    string key = to_string(size) + "x" + to_string(size);

    vector<pair<int,int>> path;
    if (showPath) {
        path = findPath(maze, start, end, size);
    }

    while (true) {
        drawMaze(maze, player, coins, size, showPath, path);
        auto now = chrono::steady_clock::now();
        int elapsed = chrono::duration_cast<chrono::seconds>(now - startTime).count();
        cout << colorize("Шаги: " + to_string(steps) + "  Время: " + to_string(elapsed) + "с  Монеты: " + to_string(collected) + "/" + to_string(coins.size()), BOLD) << endl;
        cout << colorize("Управление: стрелки (WASD), Q - выход", BLUE) << endl;
        if (showPath && !path.empty()) cout << colorize("Кратчайший путь показан точками (cyan)", YELLOW) << endl;

        int dx = 0, dy = 0;
        if (kbhit()) {
            char ch = getch();
            if (ch == 'q' || ch == 'Q') {
                cout << colorize("Выход из игры.", YELLOW) << endl;
                return 0;
            }
            if (ch == 'w' || ch == 'W') dx = -1;
            else if (ch == 's' || ch == 'S') dx = 1;
            else if (ch == 'a' || ch == 'A') dy = -1;
            else if (ch == 'd' || ch == 'D') dy = 1;
            else if (ch == 27) { // стрелка
                char buf[2];
                if (getchar() == '[') {
                    char c = getchar();
                    if (c == 'A') dx = -1;
                    else if (c == 'B') dx = 1;
                    else if (c == 'D') dy = -1;
                    else if (c == 'C') dy = 1;
                }
            }
        }
        if (dx != 0 || dy != 0) {
            int nx = player.first + dx;
            int ny = player.second + dy;
            if (nx >= 0 && nx < size && ny >= 0 && ny < size && maze[nx][ny] == 0) {
                player = {nx, ny};
                steps++;
                auto it = find(coins.begin(), coins.end(), make_pair(nx, ny));
                if (it != coins.end()) {
                    coins.erase(it);
                    collected++;
                }
                if (player == end) {
                    elapsed = chrono::duration_cast<chrono::seconds>(chrono::steady_clock::now() - startTime).count();
                    cout << colorize("🎉 Поздравляем! Вы вышли за " + to_string(steps) + " шагов и " + to_string(elapsed) + " секунд!", GREEN) << endl;
                    cout << colorize("Собрано монет: " + to_string(collected), YELLOW) << endl;
                    int bonus = collected * 10;
                    cout << colorize("Бонус за монеты: +" + to_string(bonus) + " очков", YELLOW) << endl;
                    int total = steps + bonus;
                    cout << colorize("Итоговый счёт: " + to_string(total), BOLD) << endl;
                    if (!records.isMember(key) || steps < records[key]["steps"].asInt()) {
                        records[key]["steps"] = steps;
                        records[key]["time"] = elapsed;
                        records[key]["coins"] = collected;
                        saveRecords(records);
                        cout << colorize("🏆 Новый рекорд!", GREEN) << endl;
                    }
                    cout << colorize("Нажмите любую клавишу для выхода...", YELLOW) << endl;
                    getch();
                    return 0;
                }
            }
        }
        this_thread::sleep_for(chrono::milliseconds(30));
    }
    return 0;
}
