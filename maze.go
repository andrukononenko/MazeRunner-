// maze.go
package main

import (
	"bufio"
	"encoding/json"
	"fmt"
	"math/rand"
	"os"
	"os/exec"
	"runtime"
	"strconv"
	"time"
	"golang.org/x/term"
)

const (
	DEFAULT_SIZE  = 10
	DEFAULT_COINS = 5
)

var (
	reset  = "\033[0m"
	red    = "\033[91m"
	green  = "\033[92m"
	yellow = "\033[93m"
	blue   = "\033[94m"
	cyan   = "\033[96m"
	gray   = "\033[90m"
	bold   = "\033[1m"
)

func colorize(text, color string) string {
	return color + text + reset
}

func clearScreen() {
	cmd := exec.Command("clear")
	if runtime.GOOS == "windows" {
		cmd = exec.Command("cmd", "/c", "cls")
	}
	cmd.Stdout = os.Stdout
	cmd.Run()
}

type Record struct {
	Steps int `json:"steps"`
	Time  int `json:"time"`
	Coins int `json:"coins"`
}

func loadRecords() map[string]Record {
	data, err := os.ReadFile(os.Getenv("HOME") + "/.maze_records.json")
	if err != nil {
		return make(map[string]Record)
	}
	var records map[string]Record
	json.Unmarshal(data, &records)
	return records
}

func saveRecords(records map[string]Record) {
	data, _ := json.MarshalIndent(records, "", "  ")
	os.WriteFile(os.Getenv("HOME")+"/.maze_records.json", data, 0644)
}

func generateMaze(size int) [][]int {
	maze := make([][]int, size)
	for i := range maze {
		maze[i] = make([]int, size)
		for j := range maze[i] {
			maze[i][j] = 1
		}
	}
	// Открываем проходы
	var dfs func(x, y int)
	dfs = func(x, y int) {
		maze[x][y] = 0
		dirs := [][2]int{{0, 2}, {0, -2}, {2, 0}, {-2, 0}}
		rand.Shuffle(len(dirs), func(i, j int) { dirs[i], dirs[j] = dirs[j], dirs[i] })
		for _, d := range dirs {
			nx, ny := x+d[0], y+d[1]
			if nx > 0 && nx < size-1 && ny > 0 && ny < size-1 && maze[nx][ny] == 1 {
				maze[x+d[0]/2][y+d[1]/2] = 0
				dfs(nx, ny)
			}
		}
	}
	dfs(1, 1)
	// Выход
	maze[size-2][size-1] = 0
	maze[size-1][size-1] = 0
	return maze
}

func placeCoins(maze [][]int, numCoins, size int) [][2]int {
	coins := [][2]int{}
	attempts := 0
	for len(coins) < numCoins && attempts < 1000 {
		x := rand.Intn(size-2) + 1
		y := rand.Intn(size-2) + 1
		if maze[x][y] == 0 && !contains(coins, [2]int{x, y}) && !(x == 1 && y == 1) && !(x == size-2 && y == size-1) {
			coins = append(coins, [2]int{x, y})
		}
		attempts++
	}
	return coins
}

func contains(slice [][2]int, item [2]int) bool {
	for _, v := range slice {
		if v == item {
			return true
		}
	}
	return false
}

func findPath(maze [][]int, start, end [2]int, size int) [][2]int {
	type Node struct {
		pos [2]int
		parent *Node
	}
	visited := make([][]bool, size)
	for i := range visited {
		visited[i] = make([]bool, size)
	}
	queue := []*Node{{pos: start, parent: nil}}
	visited[start[0]][start[1]] = true
	dirs := [][2]int{{0, 1}, {0, -1}, {1, 0}, {-1, 0}}
	for len(queue) > 0 {
		cur := queue[0]
		queue = queue[1:]
		if cur.pos == end {
			// Восстанавливаем путь
			path := [][2]int{}
			for cur != nil {
				path = append([][2]int{cur.pos}, path...)
				cur = cur.parent
			}
			return path
		}
		for _, d := range dirs {
			nx, ny := cur.pos[0]+d[0], cur.pos[1]+d[1]
			if nx >= 0 && nx < size && ny >= 0 && ny < size && !visited[nx][ny] && maze[nx][ny] == 0 {
				visited[nx][ny] = true
				queue = append(queue, &Node{pos: [2]int{nx, ny}, parent: cur})
			}
		}
	}
	return nil
}

func drawMaze(maze [][]int, player [2]int, coins [][2]int, size int, showPath bool, path [][2]int) {
	clearScreen()
	// Верх
	fmt.Println(colorize("┌"+stringOf('─', size*2-1)+"┐", gray))
	for i := 0; i < size; i++ {
		line := "│"
		for j := 0; j < size; j++ {
			if (i == player[0] && j == player[1]) {
				line += colorize("@", green)
			} else if showPath && contains(path, [2]int{i, j}) {
				line += colorize(".", cyan)
			} else if contains(coins, [2]int{i, j}) {
				line += colorize("💎", yellow)
			} else if maze[i][j] == 1 {
				line += colorize("█", gray)
			} else {
				line += " "
			}
			if j < size-1 {
				line += " "
			}
		}
		line += "│"
		fmt.Println(line)
	}
	fmt.Println(colorize("└"+stringOf('─', size*2-1)+"┘", gray))
}

func stringOf(ch rune, n int) string {
	s := make([]rune, n)
	for i := range s {
		s[i] = ch
	}
	return string(s)
}

func main() {
	rand.Seed(time.Now().UnixNano())
	size := DEFAULT_SIZE
	coinsCount := DEFAULT_COINS
	showPath := false

	for i := 1; i < len(os.Args); i++ {
		if os.Args[i] == "-s" && i+1 < len(os.Args) {
			size, _ = strconv.Atoi(os.Args[i+1])
			i++
		} else if os.Args[i] == "-c" && i+1 < len(os.Args) {
			coinsCount, _ = strconv.Atoi(os.Args[i+1])
			i++
		} else if os.Args[i] == "-p" {
			showPath = true
		} else if os.Args[i] == "-h" {
			fmt.Println("Usage: maze [options]\n  -s <N>   Size (default 10)\n  -c <N>   Coins (default 5)\n  -p       Show path")
			return
		}
	}
	if size%2 == 0 {
		size++
	}
	if size < 5 {
		size = 5
	}

	maze := generateMaze(size)
	coins := placeCoins(maze, coinsCount, size)
	start := [2]int{1, 1}
	end := [2]int{size - 2, size - 1}
	player := [2]int{1, 1}
	steps := 0
	startTime := time.Now()
	collected := 0
	records := loadRecords()
	keyStr := fmt.Sprintf("%dx%d", size, size)

	var path [][2]int
	if showPath {
		path = findPath(maze, start, end, size)
	}

	oldState, err := term.MakeRaw(int(os.Stdin.Fd()))
	if err != nil {
		panic(err)
	}
	defer term.Restore(int(os.Stdin.Fd()), oldState)
	reader := bufio.NewReader(os.Stdin)

	for {
		drawMaze(maze, player, coins, size, showPath, path)
		elapsed := int(time.Since(startTime).Seconds())
		fmt.Println(colorize(fmt.Sprintf("Шаги: %d  Время: %ds  Монеты: %d/%d", steps, elapsed, collected, len(coins)), bold))
		fmt.Println(colorize("Управление: стрелки (WASD), Q - выход", blue))
		if showPath && path != nil {
			fmt.Println(colorize("Кратчайший путь показан точками (cyan)", yellow))
		}

		key, _, _ := reader.ReadRune()
		dx, dy := 0, 0
		switch key {
		case 'q', 'Q':
			fmt.Println(colorize("Выход из игры.", yellow))
			return
		case 'w', 'W':
			dx, dy = -1, 0
		case 's', 'S':
			dx, dy = 1, 0
		case 'a', 'A':
			dx, dy = 0, -1
		case 'd', 'D':
			dx, dy = 0, 1
		case '\x1b':
			// стрелка
			reader.ReadRune()
			dir, _, _ := reader.ReadRune()
			switch dir {
			case 'A':
				dx, dy = -1, 0
			case 'B':
				dx, dy = 1, 0
			case 'D':
				dx, dy = 0, -1
			case 'C':
				dx, dy = 0, 1
			}
		}
		if dx != 0 || dy != 0 {
			nx, ny := player[0]+dx, player[1]+dy
			if nx >= 0 && nx < size && ny >= 0 && ny < size && maze[nx][ny] == 0 {
				player[0], player[1] = nx, ny
				steps++
				// Проверка монет
				for i, c := range coins {
					if c[0] == nx && c[1] == ny {
						coins = append(coins[:i], coins[i+1:]...)
						collected++
						break
					}
				}
				if player == end {
					elapsed = int(time.Since(startTime).Seconds())
					fmt.Println(colorize(fmt.Sprintf("🎉 Поздравляем! Вы вышли за %d шагов и %d секунд!", steps, elapsed), green))
					fmt.Println(colorize(fmt.Sprintf("Собрано монет: %d", collected), yellow))
					bonus := collected * 10
					fmt.Println(colorize(fmt.Sprintf("Бонус за монеты: +%d очков", bonus), yellow))
					total := steps + bonus
					fmt.Println(colorize(fmt.Sprintf("Итоговый счёт: %d", total), bold))
					// Рекорды
					if rec, ok := records[keyStr]; !ok || steps < rec.Steps {
						records[keyStr] = Record{Steps: steps, Time: elapsed, Coins: collected}
						saveRecords(records)
						fmt.Println(colorize("🏆 Новый рекорд!", green))
					}
					fmt.Println(colorize("Нажмите любую клавишу для выхода...", yellow))
					reader.ReadRune()
					return
				}
			}
		}
	}
}
