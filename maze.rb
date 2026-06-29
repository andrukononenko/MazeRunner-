#!/usr/bin/env ruby
# maze.rb
# encoding: UTF-8

require 'json'
require 'io/console'
require 'fileutils'

COLORS = {
  reset: "\e[0m",
  red: "\e[91m",
  green: "\e[92m",
  yellow: "\e[93m",
  blue: "\e[94m",
  cyan: "\e[96m",
  gray: "\e[90m",
  bold: "\e[1m"
}

def colorize(text, color)
  "#{COLORS[color]}#{text}#{COLORS[:reset]}"
end

DEFAULT_SIZE = 10
DEFAULT_COINS = 5
RECORD_FILE = File.join(Dir.home, '.maze_records.json')

def load_records
  File.exist?(RECORD_FILE) ? JSON.parse(File.read(RECORD_FILE)) : {}
end

def save_records(records)
  File.write(RECORD_FILE, JSON.pretty_generate(records))
end

def clear_screen
  system('clear') || system('cls')
end

def generate_maze(size)
  maze = Array.new(size) { Array.new(size, 1) }
  dfs = ->(x, y) {
    maze[x][y] = 0
    dirs = [[0,2], [0,-2], [2,0], [-2,0]].shuffle
    dirs.each do |dx, dy|
      nx, ny = x + dx, y + dy
      if nx > 0 && nx < size-1 && ny > 0 && ny < size-1 && maze[nx][ny] == 1
        maze[x + dx/2][y + dy/2] = 0
        dfs.call(nx, ny)
      end
    end
  }
  dfs.call(1, 1)
  maze[size-2][size-1] = 0
  maze[size-1][size-1] = 0
  maze
end

def place_coins(maze, num_coins, size)
  coins = []
  attempts = 0
  while coins.size < num_coins && attempts < 1000
    x = rand(1..size-2)
    y = rand(1..size-2)
    if maze[x][y] == 0 && !coins.include?([x, y]) && !(x == 1 && y == 1) && !(x == size-2 && y == size-1)
      coins << [x, y]
    end
    attempts += 1
  end
  coins
end

def find_path(maze, start, finish, size)
  visited = Array.new(size) { Array.new(size, false) }
  parent = Array.new(size) { Array.new(size) }
  queue = [start]
  visited[start[0]][start[1]] = true
  dirs = [[0,1], [0,-1], [1,0], [-1,0]]
  until queue.empty?
    cur = queue.shift
    if cur == finish
      path = []
      while cur != start
        path.unshift(cur)
        cur = parent[cur[0]][cur[1]]
      end
      path.unshift(start)
      return path
    end
    dirs.each do |dx, dy|
      nx, ny = cur[0] + dx, cur[1] + dy
      if nx >= 0 && nx < size && ny >= 0 && ny < size && !visited[nx][ny] && maze[nx][ny] == 0
        visited[nx][ny] = true
        parent[nx][ny] = cur
        queue << [nx, ny]
      end
    end
  end
  nil
end

def draw_maze(maze, player, coins, size, show_path, path)
  clear_screen
  puts colorize('┌' + '─' * (size * 2 - 1) + '┐', :gray)
  size.times do |i|
    line = '│'
    size.times do |j|
      if i == player[0] && j == player[1]
        line += colorize('@', :green)
      elsif show_path && path && path.include?([i, j])
        line += colorize('.', :cyan)
      elsif coins.include?([i, j])
        line += colorize('💎', :yellow)
      elsif maze[i][j] == 1
        line += colorize('█', :gray)
      else
        line += ' '
      end
      line += ' ' if j < size - 1
    end
    line += '│'
    puts line
  end
  puts colorize('└' + '─' * (size * 2 - 1) + '┘', :gray)
end

def get_key
  state = `stty -g` rescue nil
  `stty raw -echo -icanon isig` rescue nil
  ch = STDIN.getc
  if ch == "\e"
    seq = STDIN.read_nonblock(3) rescue nil
    if seq && seq == "[A"
      ch = :up
    elsif seq && seq == "[B"
      ch = :down
    elsif seq && seq == "[D"
      ch = :left
    elsif seq && seq == "[C"
      ch = :right
    else
      ch = seq
    end
  end
  ch
ensure
  `stty #{state}` rescue nil
end

def main
  size = DEFAULT_SIZE
  coins_count = DEFAULT_COINS
  show_path = false

  i = 0
  while i < ARGV.length
    case ARGV[i]
    when '-s'
      size = ARGV[i+1].to_i
      i += 2
    when '-c'
      coins_count = ARGV[i+1].to_i
      i += 2
    when '-p'
      show_path = true
      i += 1
    when '-h'
      puts "Usage: ruby maze.rb [options]\n  -s <N>   Size (default 10)\n  -c <N>   Coins (default 5)\n  -p       Show path"
      return
    else
      i += 1
    end
  end
  size += 1 if size.even?
  size = 5 if size < 5

  maze = generate_maze(size)
  coins = place_coins(maze, coins_count, size)
  start = [1, 1]
  finish = [size-2, size-1]
  player = [1, 1]
  steps = 0
  start_time = Time.now
  collected = 0
  records = load_records
  key = "#{size}x#{size}"

  path = find_path(maze, start, finish, size) if show_path

  trap('INT') { puts colorize("\nИгра прервана.", :yellow); exit }

  loop do
    draw_maze(maze, player, coins, size, show_path, path)
    elapsed = (Time.now - start_time).to_i
    puts colorize("Шаги: #{steps}  Время: #{elapsed}с  Монеты: #{collected}/#{coins.size}", :bold)
    puts colorize("Управление: стрелки (WASD), Q - выход", :blue)
    puts colorize("Кратчайший путь показан точками (cyan)", :yellow) if show_path && path

    key = get_key
    dx = dy = 0
    case key
    when 'q', 'Q'
      puts colorize("Выход из игры.", :yellow)
      break
    when 'w', 'W', :up
      dx = -1
    when 's', 'S', :down
      dx = 1
    when 'a', 'A', :left
      dy = -1
    when 'd', 'D', :right
      dy = 1
    end

    if dx != 0 || dy != 0
      nx, ny = player[0] + dx, player[1] + dy
      if nx >= 0 && nx < size && ny >= 0 && ny < size && maze[nx][ny] == 0
        player = [nx, ny]
        steps += 1
        if coins.include?([nx, ny])
          coins.delete([nx, ny])
          collected += 1
        end
        if player == finish
          elapsed = (Time.now - start_time).to_i
          puts colorize("🎉 Поздравляем! Вы вышли за #{steps} шагов и #{elapsed} секунд!", :green)
          puts colorize("Собрано монет: #{collected}", :yellow)
          bonus = collected * 10
          puts colorize("Бонус за монеты: +#{bonus} очков", :yellow)
          total = steps + bonus
          puts colorize("Итоговый счёт: #{total}", :bold)
          if !records[key] || steps < records[key]['steps']
            records[key] = { 'steps' => steps, 'time' => elapsed, 'coins' => collected }
            save_records(records)
            puts colorize("🏆 Новый рекорд!", :green)
          end
          puts colorize("Нажмите любую клавишу для выхода...", :yellow)
          STDIN.getc
          break
        end
      end
    end
    sleep 0.03
  end
end

main if __FILE__ == $0
