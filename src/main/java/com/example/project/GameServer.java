package com.example.project;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class GameServer {

    private static final int PORT = 6000;
    private static final List<GameRoom> gameRooms = Collections.synchronizedList(new ArrayList<>());
    private static int roomCounter = 0;
    private static ExecutorService threadPool = Executors.newCachedThreadPool();
    private static ServerSocket serverSocket;
    private static volatile boolean running = true;

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("      Tic-Tac-Toe Game Server");
        System.out.println("           Port: " + PORT);
        System.out.println("========================================");
        System.out.println("Server starting...");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down server...");
            stopServer();
        }));

        try {
            serverSocket = new ServerSocket(PORT);
            System.out.println("Server started successfully on port " + PORT);
            System.out.println("Local IP: " + InetAddress.getLocalHost().getHostAddress());
            System.out.println("Waiting for players to connect...");

            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    String clientAddress = clientSocket.getInetAddress().getHostAddress();
                    System.out.println("New client connected from: " + clientAddress);

                    ClientHandler handler = new ClientHandler(clientSocket);
                    threadPool.execute(handler);

                } catch (SocketException e) {
                    if (running) {
                        System.err.println("Socket error: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            stopServer();
        }
    }

    public static void stopServer() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }

            synchronized (gameRooms) {
                for (GameRoom room : gameRooms) {
                    room.broadcastToPlayers("MESSAGE:Server is shutting down");
                    room.cleanup();
                }
                gameRooms.clear();
            }

            threadPool.shutdown();
            if (!threadPool.awaitTermination(5, TimeUnit.SECONDS)) {
                threadPool.shutdownNow();
            }

            System.out.println("Server stopped gracefully");
        } catch (Exception e) {
            System.err.println("Error during server shutdown: " + e.getMessage());
        }
    }

    static class ClientHandler implements Runnable {
        private final Socket socket;
        private PrintWriter out;
        private BufferedReader in;
        private GameRoom currentRoom;
        private char playerSymbol;
        private volatile boolean running = true;
        private String playerName = "Player";
        private int gridSize = 3;
        private final String clientId;

        public ClientHandler(Socket socket) {
            this.socket = socket;
            this.clientId = socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
        }

        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                send("MESSAGE:Connected to Tic-Tac-Toe Server");
                send("MESSAGE:Waiting for opponent...");

                String message;
                while (running && (message = in.readLine()) != null) {
                    System.out.println("[" + playerSymbol + " @ Room " +
                            (currentRoom != null ? currentRoom.id : "none") + "] " + message);

                    if (message.startsWith("NAME:")) {
                        handleName(message);
                    } else if (message.startsWith("GRIDSIZE:")) {
                        handleGridSize(message);
                    } else if (message.startsWith("MOVE:")) {
                        handleMove(message);
                    } else if (message.startsWith("CHAT:")) {
                        handleChat(message);
                    } else if (message.equals("REMATCH")) {
                        handleRematch();
                    } else if (message.equals("PING")) {
                        send("PONG");
                    }
                }
            } catch (IOException e) {
                System.out.println("Client " + playerSymbol + " disconnected: " + e.getMessage());
            } catch (Exception e) {
                System.err.println("Error handling client " + playerSymbol + ": " + e.getMessage());
                e.printStackTrace();
            } finally {
                disconnect();
            }
        }

        private void handleName(String message) {
            String newName = message.substring(5).trim();
            if (!newName.isEmpty()) {
                this.playerName = newName;
            }
        }

        private void handleGridSize(String message) {
            try {
                int size = Integer.parseInt(message.substring(9).trim());
                this.gridSize = size;

                // Try to join or create a room
                joinOrCreateRoom();
            } catch (NumberFormatException e) {
                send("MESSAGE:Invalid grid size");
            }
        }

        private void joinOrCreateRoom() {
            synchronized (gameRooms) {
                GameRoom availableRoom = null;

                // Find a room with matching grid size
                for (GameRoom room : gameRooms) {
                    if (room.needsPlayer() && room.isActive() && room.gridSize == this.gridSize) {
                        availableRoom = room;
                        break;
                    }
                }

                if (availableRoom == null) {
                    // Create new room
                    availableRoom = new GameRoom(++roomCounter, this.gridSize);
                    gameRooms.add(availableRoom);
                    playerSymbol = 'X';
                    availableRoom.addPlayer(this, 'X');
                    System.out.println("Player X joined room " + availableRoom.id +
                            " (Grid: " + gridSize + "×" + gridSize + ") (" + clientId + ")");
                } else {
                    // Join existing room
                    if (availableRoom.gridSize != this.gridSize) {
                        send("GRIDSIZE_MISMATCH");
                        disconnect();
                        return;
                    }
                    playerSymbol = 'O';
                    availableRoom.addPlayer(this, 'O');
                    System.out.println("Player O joined room " + availableRoom.id +
                            " (Grid: " + gridSize + "×" + gridSize + ") (" + clientId + ")");
                    availableRoom.startGame();
                }
                currentRoom = availableRoom;
            }
        }

        private void handleMove(String message) {
            try {
                String[] parts = message.substring(5).split(",");
                if (parts.length == 2) {
                    int row = Integer.parseInt(parts[0].trim());
                    int col = Integer.parseInt(parts[1].trim());
                    if (currentRoom != null) {
                        currentRoom.makeMove(row, col, this);
                    }
                }
            } catch (NumberFormatException e) {
                send("MESSAGE:Invalid move format");
            }
        }

        private void handleChat(String message) {
            String chatMsg = message.substring(5);
            if (!chatMsg.trim().isEmpty() && currentRoom != null) {
                currentRoom.broadcastChat(playerSymbol, playerName, chatMsg);
            }
        }

        private void handleRematch() {
            if (currentRoom != null) {
                currentRoom.handleRematch(this);
            }
        }

        public void send(String message) {
            if (out != null && !socket.isClosed() && running) {
                out.println(message);
            }
        }

        public void disconnect() {
            running = false;
            try {
                if (in != null) in.close();
                if (out != null) out.close();
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            } catch (IOException e) {
                System.err.println("Error closing client connection: " + e.getMessage());
            }

            if (currentRoom != null) {
                currentRoom.removePlayer(this);
                currentRoom = null;
            }

            System.out.println("Client " + playerSymbol + " disconnected (" + clientId + ")");
        }

        public char getSymbol() { return playerSymbol; }
        public String getPlayerName() { return playerName; }
        public String getClientId() { return clientId; }
        public boolean isConnected() { return running && !socket.isClosed(); }
    }

    static class GameRoom {
        public final int id;
        public final int gridSize;
        private ClientHandler playerX;
        private ClientHandler playerO;
        private char[][] board;
        private char currentTurn = 'X';
        private boolean gameActive = false;
        private int scoreX = 0;
        private int scoreO = 0;
        private boolean xWantsRematch = false;
        private boolean oWantsRematch = false;

        public GameRoom(int id, int gridSize) {
            this.id = id;
            this.gridSize = gridSize;
            this.board = new char[gridSize][gridSize];
            resetBoard();
            System.out.println("Room " + id + " created with grid size " + gridSize + "×" + gridSize);
        }

        private void resetBoard() {
            for (int i = 0; i < gridSize; i++) {
                Arrays.fill(board[i], ' ');
            }
            currentTurn = 'X';
            xWantsRematch = false;
            oWantsRematch = false;
        }

        public synchronized boolean needsPlayer() {
            return playerX == null || playerO == null;
        }

        public synchronized boolean isActive() {
            return gameActive || needsPlayer();
        }

        public synchronized void addPlayer(ClientHandler player, char symbol) {
            if (symbol == 'X') {
                playerX = player;
                player.send("SYMBOL:X");
            } else {
                playerO = player;
                player.send("SYMBOL:O");
            }
        }

        public synchronized void removePlayer(ClientHandler player) {
            System.out.println("Player " + player.getSymbol() + " leaving room " + id);

            if (player == playerX) {
                playerX = null;
                if (playerO != null && playerO.isConnected()) {
                    playerO.send("MESSAGE:Opponent disconnected");
                    playerO.send("GAME_OVER:Opponent left the game");
                    playerO.send("WAIT_TURN");
                }
            } else if (player == playerO) {
                playerO = null;
                if (playerX != null && playerX.isConnected()) {
                    playerX.send("MESSAGE:Opponent disconnected");
                    playerX.send("GAME_OVER:Opponent left the game");
                    playerX.send("WAIT_TURN");
                }
            }

            gameActive = false;

            if (playerX == null && playerO == null) {
                gameRooms.remove(this);
                System.out.println("Room " + id + " removed (empty)");
            }
        }

        public synchronized void startGame() {
            if (playerX == null || playerO == null) {
                return;
            }

            System.out.println("Starting game in room " + id);
            gameActive = true;
            resetBoard();

            String xName = playerX.getPlayerName();
            String oName = playerO.getPlayerName();
            broadcastToPlayers("MESSAGE:Game started! " + xName + " (X) vs " + oName + " (O)");
            sendScores();

            currentTurn = 'X';
            if (playerX.isConnected()) {
                playerX.send("YOUR_TURN");
            }
            if (playerO.isConnected()) {
                playerO.send("WAIT_TURN");
            }
        }

        private void sendScores() {
            String scores = "SCORES:" + scoreX + "," + scoreO;
            if (playerX != null && playerX.isConnected()) playerX.send(scores);
            if (playerO != null && playerO.isConnected()) playerO.send(scores);
        }

        public synchronized void makeMove(int row, int col, ClientHandler player) {
            if (!gameActive) {
                player.send("MESSAGE:Game is not active");
                return;
            }

            if (player.getSymbol() != currentTurn) {
                player.send("MESSAGE:Not your turn!");
                return;
            }

            if (row < 0 || row >= gridSize || col < 0 || col >= gridSize) {
                player.send("MESSAGE:Invalid position!");
                return;
            }

            if (board[row][col] != ' ') {
                player.send("MESSAGE:Cell already occupied!");
                return;
            }

            board[row][col] = currentTurn;
            System.out.println("Player " + currentTurn + " moved to [" + row + "," + col + "]");

            String update = "UPDATE:" + row + "," + col + "," + currentTurn;
            broadcastToPlayers(update);

            if (checkWin(currentTurn)) {
                if (currentTurn == 'X') scoreX++;
                else scoreO++;
                sendScores();

                String winMsg = "GAME_OVER:Player " + currentTurn + " wins!";
                broadcastToPlayers(winMsg);
                gameActive = false;
                System.out.println("Player " + currentTurn + " wins in room " + id);
                return;
            }

            if (isBoardFull()) {
                broadcastToPlayers("GAME_OVER:Draw!");
                gameActive = false;
                System.out.println("Game draw in room " + id);
                return;
            }

            currentTurn = (currentTurn == 'X') ? 'O' : 'X';
            if (currentTurn == 'X') {
                if (playerX != null && playerX.isConnected()) playerX.send("YOUR_TURN");
                if (playerO != null && playerO.isConnected()) playerO.send("WAIT_TURN");
            } else {
                if (playerO != null && playerO.isConnected()) playerO.send("YOUR_TURN");
                if (playerX != null && playerX.isConnected()) playerX.send("WAIT_TURN");
            }
        }

        private boolean checkWin(char symbol) {
            int winLength = gridSize == 3 ? 3 : (gridSize == 5 ? 4 : 5);

            // Check rows
            for (int i = 0; i < gridSize; i++) {
                for (int j = 0; j <= gridSize - winLength; j++) {
                    boolean win = true;
                    for (int k = 0; k < winLength; k++) {
                        if (board[i][j + k] != symbol) {
                            win = false;
                            break;
                        }
                    }
                    if (win) return true;
                }
            }

            // Check columns
            for (int i = 0; i <= gridSize - winLength; i++) {
                for (int j = 0; j < gridSize; j++) {
                    boolean win = true;
                    for (int k = 0; k < winLength; k++) {
                        if (board[i + k][j] != symbol) {
                            win = false;
                            break;
                        }
                    }
                    if (win) return true;
                }
            }

            // Check diagonals (top-left to bottom-right)
            for (int i = 0; i <= gridSize - winLength; i++) {
                for (int j = 0; j <= gridSize - winLength; j++) {
                    boolean win = true;
                    for (int k = 0; k < winLength; k++) {
                        if (board[i + k][j + k] != symbol) {
                            win = false;
                            break;
                        }
                    }
                    if (win) return true;
                }
            }

            // Check diagonals (top-right to bottom-left)
            for (int i = 0; i <= gridSize - winLength; i++) {
                for (int j = winLength - 1; j < gridSize; j++) {
                    boolean win = true;
                    for (int k = 0; k < winLength; k++) {
                        if (board[i + k][j - k] != symbol) {
                            win = false;
                            break;
                        }
                    }
                    if (win) return true;
                }
            }

            return false;
        }

        private boolean isBoardFull() {
            for (char[] row : board) {
                for (char cell : row) {
                    if (cell == ' ') return false;
                }
            }
            return true;
        }

        public synchronized void broadcastToPlayers(String message) {
            if (playerX != null && playerX.isConnected()) playerX.send(message);
            if (playerO != null && playerO.isConnected()) playerO.send(message);
        }

        public synchronized void broadcastChat(char senderSymbol, String senderName, String message) {
            String chatMsg = "CHAT:" + senderName + " (" + senderSymbol + "):" + message;
            broadcastToPlayers(chatMsg);
        }

        public synchronized void handleRematch(ClientHandler player) {
            if (player.getSymbol() == 'X') {
                xWantsRematch = true;
                if (playerO != null && playerO.isConnected()) {
                    playerO.send("MESSAGE:Opponent wants a rematch!");
                }
            } else {
                oWantsRematch = true;
                if (playerX != null && playerX.isConnected()) {
                    playerX.send("MESSAGE:Opponent wants a rematch!");
                }
            }

            if (xWantsRematch && oWantsRematch) {
                broadcastToPlayers("MESSAGE:Starting new game...");
                broadcastToPlayers("REMATCH_START");
                startGame();
            }
        }

        public synchronized void cleanup() {
            gameActive = false;
            if (playerX != null) {
                try { playerX.disconnect(); } catch (Exception ignored) {}
            }
            if (playerO != null) {
                try { playerO.disconnect(); } catch (Exception ignored) {}
            }
        }
    }
}