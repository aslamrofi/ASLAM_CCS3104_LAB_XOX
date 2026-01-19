package com.example.project;

import javafx.application.Platform;
import javafx.geometry.*;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.*;
import javafx.stage.Stage;
import javafx.animation.*;
import javafx.util.Duration;

import java.io.*;
import java.net.Socket;
import java.util.*;

public class GameWindow {

    private Button[][] buttons;
    private char[][] board;
    private int size;
    private Label statusLabel;
    private Label playerLabel;
    private Label scoreLabel;
    private Label timerLabel;
    private TextArea chatArea;
    private TextField chatInput;
    private VBox chatBox;

    private String username;
    private String gameMode;
    private String modeData;

    private long gameStartTime;
    private Timeline timer;
    private int player1Score = 0;
    private int player2Score = 0;

    // Computer mode
    private char playerSymbol = 'X';
    private char aiSymbol = 'O';
    private boolean playerTurn = true;
    private String difficulty = "Medium";

    // Friend mode
    private char currentPlayer = 'X';

    // Online mode
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private char mySymbol;
    private boolean myTurn = false;

    private Stage gameStage;

    public GameWindow(String username, String gameMode, String modeData, int gridSize) {
        this.username = username;
        this.gameMode = gameMode;
        this.modeData = modeData;
        this.size = gridSize;

        if (gameMode.equals("COMPUTER") && modeData != null) {
            this.difficulty = modeData;
        }
    }

    public void showGame(Stage stage) {
        this.gameStage = stage;
        initializeGUI();

        if (gameMode.equals("ONLINE")) {
            connectToServer();
        } else {
            startTimer();
        }
    }

    private void initializeGUI() {
        String title = "Tic-Tac-Toe - ";
        if (gameMode.equals("COMPUTER")) title += "VS Computer (" + difficulty + ", " + size + "x" + size + ")";
        else if (gameMode.equals("FRIEND")) title += "Local Multiplayer (" + size + "x" + size + ")";
        else title += "Online Multiplayer (" + size + "x" + size + ")";

        gameStage.setTitle(title);

        buttons = new Button[size][size];
        board = new char[size][size];
        for (int i = 0; i < size; i++) {
            Arrays.fill(board[i], ' ');
        }

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: linear-gradient(to bottom right, #1e1b4b 0%, #312e81 50%, #1e3a8a 100%);");
        root.setPadding(new Insets(15));

// Add animated gradient overlay
        Pane animatedBg = new Pane();
        animatedBg.setStyle("-fx-background-color: radial-gradient(circle at 20% 50%, rgba(139, 92, 246, 0.3) 0%, transparent 50%);");
        animatedBg.setMouseTransparent(true);
        root.getChildren().add(0, animatedBg);
        animatedBg.toBack();

// Animate the background
        TranslateTransition tt = new TranslateTransition(Duration.seconds(10), animatedBg);
        tt.setFromX(-50);
        tt.setToX(50);
        tt.setAutoReverse(true);
        tt.setCycleCount(Timeline.INDEFINITE);
        tt.play();

        VBox header = createHeader();
        root.setTop(header);

        if (gameMode.equals("ONLINE")) {
            HBox centerContent = new HBox(20);
            centerContent.setAlignment(Pos.CENTER);
            VBox boardContainer = createBoard();
            chatBox = createChatBox();
            centerContent.getChildren().addAll(boardContainer, chatBox);
            root.setCenter(centerContent);
        } else {
            VBox boardContainer = createBoard();
            root.setCenter(boardContainer);
        }

        HBox controls = createControls();
        root.setBottom(controls);

        int sceneHeight = size == 7 ? 850 : 750;
        Scene scene = new Scene(root, gameMode.equals("ONLINE") ? 900 : 750, sceneHeight);
        gameStage.setScene(scene);
        gameStage.setOnCloseRequest(e -> cleanup());
        gameStage.show();

        FadeTransition fade = new FadeTransition(Duration.millis(300), root);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.play();
    }

    private VBox createHeader() {
        VBox header = new VBox(8);
        header.setAlignment(Pos.CENTER);
        header.setPadding(new Insets(10));

        String headerText;
        if (gameMode.equals("COMPUTER")) {
            headerText = username + " (X) VS Computer (O) - " + size + "x" + size;
        } else if (gameMode.equals("FRIEND")) {
            headerText = "Local Multiplayer - " + size + "x" + size;
        } else {
            headerText = "Online - " + username + " - " + size + "x" + size;
        }

        playerLabel = new Label(headerText);
        playerLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 24));
        playerLabel.setTextFill(Color.web("#facc15"));

        statusLabel = new Label(getInitialStatus());
        statusLabel.setFont(Font.font("Segoe UI", 16));
        statusLabel.setTextFill(Color.web("#22c55e"));

        scoreLabel = new Label(getScoreText());
        scoreLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
        scoreLabel.setTextFill(Color.web("#38bdf8"));

        timerLabel = new Label("Time: 0.0s");
        timerLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16));
        timerLabel.setTextFill(Color.web("#fb923c"));

        header.getChildren().addAll(playerLabel, statusLabel, scoreLabel, timerLabel);
        return header;
    }

    private String getInitialStatus() {
        if (gameMode.equals("COMPUTER")) return "Your turn!";
        if (gameMode.equals("FRIEND")) return "Player X's turn";
        return "Connecting...";
    }

    private String getScoreText() {
        if (gameMode.equals("COMPUTER")) {
            return "Score - You: " + player1Score + " | Computer: " + player2Score;
        } else if (gameMode.equals("FRIEND")) {
            return "Score - X: " + player1Score + " | O: " + player2Score;
        } else {
            return "Score - You: " + player1Score + " | Opponent: " + player2Score;
        }
    }

    private VBox createBoard() {
        VBox container = new VBox(15);
        container.setAlignment(Pos.CENTER);

        GridPane boardPane = new GridPane();
        boardPane.setAlignment(Pos.CENTER);
        boardPane.setHgap(6);
        boardPane.setVgap(6);

        int buttonSize = calculateButtonSize();

        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                Button btn = new Button("");
                btn.setMinSize(buttonSize, buttonSize);
                btn.setMaxSize(buttonSize, buttonSize);
                btn.setFont(Font.font("Segoe UI", FontWeight.EXTRA_BOLD, buttonSize / 3));
                btn.setStyle(
                        "-fx-background-color: rgba(30, 41, 59, 0.6); -fx-text-fill: white; " +
                                "-fx-background-radius: 10; -fx-border-radius: 10; -fx-border-color: rgba(148, 163, 184, 0.5); " +
                                "-fx-border-width: 2; -fx-cursor: hand; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.5), 10, 0, 0, 4);");

                if (gameMode.equals("ONLINE")) {
                    btn.setDisable(true);
                }

                int r = i;
                int c = j;

                btn.setOnAction(e -> handleMove(r, c));

                btn.setOnMouseEntered(e -> {
                    if (!btn.isDisabled() && canMakeMove() && board[r][c] == ' ') {
                        btn.setStyle(
                                "-fx-background-color: rgba(51, 65, 85, 0.8); -fx-text-fill: white; " +
                                        "-fx-background-radius: 10; -fx-border-radius: 10; -fx-border-color: rgba(148, 163, 184, 0.8); " +
                                        "-fx-border-width: 2; -fx-cursor: hand; -fx-effect: dropshadow(gaussian, rgba(139,92,246,0.6), 15, 0, 0, 5);");
                    }
                });

                btn.setOnMouseExited(e -> {
                    if (board[r][c] == ' ') {
                        btn.setStyle(
                                "-fx-background-color: rgba(30, 41, 59, 0.6); -fx-text-fill: white; " +
                                        "-fx-background-radius: 10; -fx-border-radius: 10; -fx-border-color: rgba(148, 163, 184, 0.5); " +
                                        "-fx-border-width: 2; -fx-cursor: hand; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.5), 10, 0, 0, 4);");
                    }
                });

                buttons[i][j] = btn;
                boardPane.add(btn, j, i);
            }
        }

        container.getChildren().add(boardPane);
        return container;
    }

    private int calculateButtonSize() {
        if (size == 3) return gameMode.equals("ONLINE") ? 120 : 150;
        if (size == 5) return gameMode.equals("ONLINE") ? 80 : 100;
        return gameMode.equals("ONLINE") ? 60 : 80;
    }

    private VBox createChatBox() {
        VBox chatContainer = new VBox(10);
        chatContainer.setAlignment(Pos.TOP_CENTER);
        chatContainer.setPrefWidth(260);
        chatContainer.setStyle(
                "-fx-background-color: rgba(15, 23, 42, 0.95); -fx-background-radius: 12; -fx-padding: 15;");

        Label chatTitle = new Label("💬 Chat");
        chatTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
        chatTitle.setTextFill(Color.WHITE);

        chatArea = new TextArea();
        chatArea.setEditable(false);
        chatArea.setWrapText(true);
        chatArea.setPrefHeight(420);
        chatArea.setStyle(
                "-fx-control-inner-background: #020617; -fx-text-fill: white; " +
                        "-fx-background-radius: 6; -fx-border-radius: 6; -fx-border-color: #1e293b;");

        chatInput = new TextField();
        chatInput.setPromptText("Type message...");
        chatInput.setStyle(
                "-fx-background-color: #020617; -fx-text-fill: white; " +
                        "-fx-prompt-text-fill: #6b7280; -fx-background-radius: 6;");
        chatInput.setOnAction(e -> sendChat());

        Button sendBtn = new Button("Send");
        sendBtn.setStyle(
                "-fx-background-color: #22c55e; -fx-text-fill: white; " +
                        "-fx-font-weight: bold; -fx-background-radius: 6; -fx-cursor: hand;");
        sendBtn.setOnAction(e -> sendChat());

        HBox chatInputBox = new HBox(5, chatInput, sendBtn);
        chatInputBox.setAlignment(Pos.CENTER);

        chatContainer.getChildren().addAll(chatTitle, chatArea, chatInputBox);
        return chatContainer;
    }

    private HBox createControls() {
        HBox controls = new HBox(15);
        controls.setAlignment(Pos.CENTER);
        controls.setPadding(new Insets(15));

        Button newGameBtn = createStyledButton("🔄 New Game", "#0ea5e9");
        newGameBtn.setOnAction(e -> resetGame());

        Button backBtn = createStyledButton("🏠 Close Window", "#8b5cf6");
        backBtn.setOnAction(e -> {
            cleanup();
            gameStage.close();
        });

        Button exitBtn = createStyledButton("❌ Exit App", "#ef4444");
        exitBtn.setOnAction(e -> {
            cleanup();
            Platform.exit();
        });

        controls.getChildren().addAll(newGameBtn, backBtn, exitBtn);
        return controls;
    }

    private Button createStyledButton(String text, String color) {
        Button btn = new Button(text);
        btn.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 14));
        btn.setStyle(
                "-fx-background-color: " + color + "; -fx-text-fill: white; " +
                        "-fx-padding: 10 22; -fx-background-radius: 999; -fx-cursor: hand;");

        btn.setOnMouseEntered(e ->
                btn.setStyle(
                        "-fx-background-color: derive(" + color + ", 15%); -fx-text-fill: white; " +
                                "-fx-padding: 10 22; -fx-background-radius: 999; -fx-cursor: hand;"));

        btn.setOnMouseExited(e ->
                btn.setStyle(
                        "-fx-background-color: " + color + "; -fx-text-fill: white; " +
                                "-fx-padding: 10 22; -fx-background-radius: 999; -fx-cursor: hand;"));

        return btn;
    }

    private void startTimer() {
        gameStartTime = System.currentTimeMillis();
        timer = new Timeline(new KeyFrame(Duration.millis(100), e -> {
            double elapsed = (System.currentTimeMillis() - gameStartTime) / 1000.0;
            timerLabel.setText(String.format("Time: %.1fs", elapsed));
        }));
        timer.setCycleCount(Timeline.INDEFINITE);
        timer.play();
    }

    private void stopTimer() {
        if (timer != null) {
            timer.stop();
        }
    }

    private boolean canMakeMove() {
        if (gameMode.equals("COMPUTER")) return playerTurn;
        if (gameMode.equals("FRIEND")) return true;
        if (gameMode.equals("ONLINE")) return myTurn;
        return false;
    }

    private void handleMove(int r, int c) {
        if (board[r][c] != ' ') return;

        if (gameMode.equals("COMPUTER")) {
            handleComputerMove(r, c);
        } else if (gameMode.equals("FRIEND")) {
            handleFriendMove(r, c);
        } else if (gameMode.equals("ONLINE")) {
            handleOnlineMove(r, c);
        }
    }

    // ======== COMPUTER MODE ========
    private void handleComputerMove(int r, int c) {
        if (!playerTurn) return;

        placeMove(r, c, playerSymbol);

        if (checkWin(playerSymbol)) {
            player1Score++;
            updateScore();
            stopTimer();
            double gameTime = (System.currentTimeMillis() - gameStartTime) / 1000.0;
            JavaFXLauncher.addLeaderboardEntry(username, 1, gameTime, "Computer", size);
            showGameOver("🎉 You win!");
            return;
        }

        if (isBoardFull()) {
            showGameOver("🤝 It's a draw!");
            return;
        }

        playerTurn = false;
        statusLabel.setText("Computer is thinking...");
        statusLabel.setTextFill(Color.web("#fb923c"));
        disableAllButtons();

        PauseTransition pause = new PauseTransition(Duration.millis(500));
        pause.setOnFinished(e -> {
            makeAIMove();

            if (checkWin(aiSymbol)) {
                player2Score++;
                updateScore();
                stopTimer();
                showGameOver("Computer wins!");
                return;
            }

            if (isBoardFull()) {
                showGameOver("🤝 It's a draw!");
                return;
            }

            playerTurn = true;
            statusLabel.setText("Your turn!");
            statusLabel.setTextFill(Color.web("#22c55e"));
            enableEmptyButtons();
        });
        pause.play();
    }

    private void makeAIMove() {
        int[] move = null;

        switch (difficulty.toLowerCase()) {
            case "easy":
                move = getRandomMove();
                break;
            case "medium":
                move = getMediumMove();
                break;
            case "hard":
                move = getBestMove();
                break;
        }

        if (move != null) {
            placeMove(move[0], move[1], aiSymbol);
        }
    }

    private int[] getRandomMove() {
        List<int[]> emptyCells = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                if (board[i][j] == ' ') {
                    emptyCells.add(new int[]{i, j});
                }
            }
        }
        return emptyCells.isEmpty() ? null : emptyCells.get(new Random().nextInt(emptyCells.size()));
    }

    private int[] getMediumMove() {
        int[] winMove = findWinningMove(aiSymbol);
        if (winMove != null) return winMove;

        int[] blockMove = findWinningMove(playerSymbol);
        if (blockMove != null) return blockMove;

        return getRandomMove();
    }

    private int[] getBestMove() {
        int bestScore = Integer.MIN_VALUE;
        int[] bestMove = null;

        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                if (board[i][j] == ' ') {
                    board[i][j] = aiSymbol;
                    int score = minimax(board, 0, false, Integer.MIN_VALUE, Integer.MAX_VALUE);
                    board[i][j] = ' ';

                    if (score > bestScore) {
                        bestScore = score;
                        bestMove = new int[]{i, j};
                    }
                }
            }
        }

        return bestMove != null ? bestMove : getRandomMove();
    }

    private int minimax(char[][] board, int depth, boolean isMaximizing, int alpha, int beta) {
        if (depth > 6) return 0; // Limit depth for larger grids

        if (checkWin(aiSymbol)) return 10 - depth;
        if (checkWin(playerSymbol)) return depth - 10;
        if (isBoardFull()) return 0;

        if (isMaximizing) {
            int bestScore = Integer.MIN_VALUE;
            for (int i = 0; i < size; i++) {
                for (int j = 0; j < size; j++) {
                    if (board[i][j] == ' ') {
                        board[i][j] = aiSymbol;
                        bestScore = Math.max(bestScore, minimax(board, depth + 1, false, alpha, beta));
                        board[i][j] = ' ';
                        alpha = Math.max(alpha, bestScore);
                        if (beta <= alpha) break;
                    }
                }
            }
            return bestScore;
        } else {
            int bestScore = Integer.MAX_VALUE;
            for (int i = 0; i < size; i++) {
                for (int j = 0; j < size; j++) {
                    if (board[i][j] == ' ') {
                        board[i][j] = playerSymbol;
                        bestScore = Math.min(bestScore, minimax(board, depth + 1, true, alpha, beta));
                        board[i][j] = ' ';
                        beta = Math.min(beta, bestScore);
                        if (beta <= alpha) break;
                    }
                }
            }
            return bestScore;
        }
    }

    private int[] findWinningMove(char symbol) {
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                if (board[i][j] == ' ') {
                    board[i][j] = symbol;
                    boolean wins = checkWin(symbol);
                    board[i][j] = ' ';
                    if (wins) return new int[]{i, j};
                }
            }
        }
        return null;
    }
    // ===============================

    // ======== FRIEND MODE ========
    private void handleFriendMove(int r, int c) {
        placeMove(r, c, currentPlayer);

        if (checkWin(currentPlayer)) {
            if (currentPlayer == 'X') player1Score++;
            else player2Score++;
            updateScore();
            stopTimer();
            double gameTime = (System.currentTimeMillis() - gameStartTime) / 1000.0;
            JavaFXLauncher.addLeaderboardEntry(username, 1, gameTime, "Friend", size);
            showGameOver("🎉 Player " + currentPlayer + " wins!");
            return;
        }

        if (isBoardFull()) {
            showGameOver("🤝 It's a draw!");
            return;
        }

        currentPlayer = (currentPlayer == 'X') ? 'O' : 'X';
        statusLabel.setText("Player " + currentPlayer + "'s turn");
        statusLabel.setTextFill(currentPlayer == 'X'
                ? Color.web("#38bdf8") : Color.web("#f97316"));
    }
    // ==============================

    // ======== ONLINE MODE ========
    private void handleOnlineMove(int r, int c) {
        if (!myTurn) {
            statusLabel.setText("⚠ Wait for your turn!");
            statusLabel.setTextFill(Color.web("#ef4444"));
            return;
        }

        if (out != null) {
            out.println("MOVE:" + r + "," + c);
        }
    }

    private void sendChat() {
        if (gameMode.equals("ONLINE") && out != null) {
            String message = chatInput.getText().trim();
            if (!message.isEmpty()) {
                out.println("CHAT:" + message);
                chatInput.clear();
            }
        }
    }

    private void connectToServer() {
        new Thread(() -> {
            try {
                Platform.runLater(() -> {
                    statusLabel.setText("Connecting to server...");
                    statusLabel.setTextFill(Color.web("#e5e7eb"));
                });

                String[] parts = modeData.split(":");
                String host = parts[0];
                int port = Integer.parseInt(parts[1]);

                socket = new Socket(host, port);
                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                out.println("NAME:" + username);
                out.println("GRIDSIZE:" + size);

                Platform.runLater(() -> {
                    statusLabel.setText("✓ Connected! Waiting for opponent...");
                    statusLabel.setTextFill(Color.web("#22c55e"));
                });

                String msg;
                while ((msg = in.readLine()) != null) {
                    String finalMsg = msg;
                    Platform.runLater(() -> processServerMessage(finalMsg));
                }
            } catch (IOException ex) {
                Platform.runLater(() -> {
                    statusLabel.setText("❌ Connection lost");
                    statusLabel.setTextFill(Color.web("#ef4444"));
                    disableAllButtons();

                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Connection Error");
                    alert.setHeaderText("Failed to connect");
                    alert.setContentText("Could not connect to server. Please check IP, port, and grid size.");
                    alert.showAndWait();
                });
            }
        }).start();
    }

    private void processServerMessage(String msg) {
        if (msg.startsWith("SYMBOL:")) {
            mySymbol = msg.charAt(7);
            playerLabel.setText(username + " (" + mySymbol + ") - " + size + "x" + size);
            playerLabel.setTextFill(mySymbol == 'X'
                    ? Color.web("#38bdf8") : Color.web("#f97316"));

        } else if (msg.startsWith("MESSAGE:")) {
            String message = msg.substring(8);
            statusLabel.setText(message);
            if (message.contains("started")) {
                startTimer();
            }

        } else if (msg.equals("YOUR_TURN")) {
            myTurn = true;
            statusLabel.setText("🎯 Your turn!");
            statusLabel.setTextFill(Color.web("#22c55e"));
            enableEmptyButtons();

        } else if (msg.equals("WAIT_TURN")) {
            myTurn = false;
            statusLabel.setText("⏳ Opponent's turn...");
            statusLabel.setTextFill(Color.web("#facc15"));
            disableAllButtons();

        } else if (msg.startsWith("UPDATE:")) {
            String[] p = msg.substring(7).split(",");
            int row = Integer.parseInt(p[0].trim());
            int col = Integer.parseInt(p[1].trim());
            char symbol = p[2].trim().charAt(0);
            placeMove(row, col, symbol);

        } else if (msg.startsWith("GAME_OVER:")) {
            String result = msg.substring(10);
            stopTimer();
            if (result.contains("wins") && result.contains(String.valueOf(mySymbol))) {
                player1Score++;
                double gameTime = (System.currentTimeMillis() - gameStartTime) / 1000.0;
                JavaFXLauncher.addLeaderboardEntry(username, 1, gameTime, "Online", size);
            }
            showGameOver(result);

        } else if (msg.startsWith("SCORES:")) {
            String[] scores = msg.substring(7).split(",");
            int xScore = Integer.parseInt(scores[0].trim());
            int oScore = Integer.parseInt(scores[1].trim());

            if (mySymbol == 'X') {
                player1Score = xScore;
                player2Score = oScore;
            } else {
                player1Score = oScore;
                player2Score = xScore;
            }
            updateScore();

        } else if (msg.startsWith("CHAT:")) {
            String[] parts = msg.substring(5).split(":", 2);
            if (parts.length == 2 && chatArea != null) {
                chatArea.appendText(parts[0] + ": " + parts[1] + "\n");
            }

        } else if (msg.equals("REMATCH_START")) {
            resetGame();
            if (chatArea != null) {
                chatArea.appendText("System: Rematch started!\n");
            }

        } else if (msg.equals("GRIDSIZE_MISMATCH")) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Grid Size Mismatch");
            alert.setHeaderText("Cannot join game");
            alert.setContentText("The game room uses a different grid size.\n" +
                    "Please select the same grid size as the host ("
                    + size + "x" + size + ").");
            alert.showAndWait();
            cleanup();
            gameStage.close();
        }
    }
    // =============================

    private void placeMove(int r, int c, char symbol) {
        board[r][c] = symbol;
        buttons[r][c].setText(String.valueOf(symbol));
        buttons[r][c].setDisable(true);

        String color = (symbol == 'X' || symbol == playerSymbol) ? "#38bdf8" : "#f97316";
        buttons[r][c].setStyle(
                "-fx-background-color: #020617; -fx-text-fill: " + color + "; " +
                        "-fx-background-radius: 10; -fx-border-radius: 10; -fx-border-color: " + color + "; -fx-border-width: 2;");

        ScaleTransition st = new ScaleTransition(Duration.millis(180), buttons[r][c]);
        st.setFromX(0.85);
        st.setFromY(0.85);
        st.setToX(1.0);
        st.setToY(1.0);
        st.play();
    }

    private void updateScore() {
        scoreLabel.setText(getScoreText());
    }

    private void showGameOver(String result) {
        disableAllButtons();
        statusLabel.setText(result);
        statusLabel.setTextFill(Color.web("#facc15"));

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Game Over");
        alert.setHeaderText(result);
        alert.setContentText(getScoreText());
        alert.show();
    }

    private boolean checkWin(char symbol) {
        int winLength = size == 3 ? 3 : (size == 5 ? 4 : 5);

        // Rows
        for (int i = 0; i < size; i++) {
            for (int j = 0; j <= size - winLength; j++) {
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

        // Columns
        for (int i = 0; i <= size - winLength; i++) {
            for (int j = 0; j < size; j++) {
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

        // Diagonals (top-left to bottom-right)
        for (int i = 0; i <= size - winLength; i++) {
            for (int j = 0; j <= size - winLength; j++) {
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

        // Diagonals (top-right to bottom-left)
        for (int i = 0; i <= size - winLength; i++) {
            for (int j = winLength - 1; j < size; j++) {
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
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                if (board[i][j] == ' ') return false;
            }
        }
        return true;
    }

    private void resetGame() {
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                board[i][j] = ' ';
                buttons[i][j].setText("");
                buttons[i][j].setDisable(gameMode.equals("ONLINE"));
                buttons[i][j].setStyle(
                        "-fx-background-color: #020617; -fx-text-fill: white; " +
                                "-fx-background-radius: 10; -fx-border-radius: 10; -fx-border-color: #1e293b; " +
                                "-fx-border-width: 2; -fx-cursor: hand;");
            }
        }

        if (gameMode.equals("COMPUTER")) {
            playerTurn = true;
            statusLabel.setText("Your turn!");
        } else if (gameMode.equals("FRIEND")) {
            currentPlayer = 'X';
            statusLabel.setText("Player X's turn");
        } else if (gameMode.equals("ONLINE") && out != null) {
            out.println("REMATCH");
        }

        statusLabel.setTextFill(Color.web("#22c55e"));
        startTimer();
    }

    private void enableEmptyButtons() {
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                if (board[i][j] == ' ') {
                    buttons[i][j].setDisable(false);
                }
            }
        }
    }

    private void disableAllButtons() {
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                buttons[i][j].setDisable(true);
            }
        }
    }

    private void cleanup() {
        stopTimer();
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
            } catch (IOException ignored) {}
        }
    }
}