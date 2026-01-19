package com.example.project;

import javafx.application.Application;
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

public class OnlineGameClient {

    private Button[][] buttons;
    private char[][] board;
    private int size = 3;
    private Label statusLabel;
    private Label playerLabel;
    private Label scoreLabel;
    private Label timerLabel;
    private TextArea chatArea;
    private TextField chatInput;

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;

    private char mySymbol;
    private boolean myTurn = false;
    private int myScore = 0;
    private int opponentScore = 0;

    private String username;
    private String serverAddress;
    private int port;
    private long gameStartTime;
    private Timeline timer;

    private Stage primaryStage;

    public OnlineGameClient(String username, String serverAddress, int port, int gridSize) {
        this.username = username;
        this.serverAddress = serverAddress;
        this.port = port;
        this.size = gridSize;
    }

    public void start(Stage stage) {
        this.primaryStage = stage;
        initializeGUI();
        connectToServer();
    }

    private void initializeGUI() {
        primaryStage.setTitle("Tic-Tac-Toe - Online Multiplayer (" + size + "×" + size + ")");

        buttons = new Button[size][size];
        board = new char[size][size];

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #2d3436;");
        root.setPadding(new Insets(15));

        VBox header = createHeader();
        root.setTop(header);

        HBox centerContent = new HBox(20);
        centerContent.setAlignment(Pos.CENTER);

        VBox boardContainer = createBoard();
        VBox chatBox = createChatBox();

        centerContent.getChildren().addAll(boardContainer, chatBox);
        root.setCenter(centerContent);

        HBox controls = createControls();
        root.setBottom(controls);

        Scene scene = new Scene(root, 900, 750);
        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest(e -> {
            stopTimer();
            disconnect();
        });
        primaryStage.show();
    }

    private VBox createHeader() {
        VBox header = new VBox(8);
        header.setAlignment(Pos.CENTER);
        header.setPadding(new Insets(10));

        playerLabel = new Label("Online Multiplayer - " + username + " (" + size + "×" + size + ")");
        playerLabel.setFont(Font.font("Arial", FontWeight.BOLD, 24));
        playerLabel.setTextFill(Color.web("#f39c12"));

        statusLabel = new Label("Connecting...");
        statusLabel.setFont(Font.font("Arial", 16));
        statusLabel.setTextFill(Color.web("#95a5a6"));

        scoreLabel = new Label("Score - You: 0 | Opponent: 0");
        scoreLabel.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        scoreLabel.setTextFill(Color.web("#3498db"));

        timerLabel = new Label("Time: 0.0s");
        timerLabel.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        timerLabel.setTextFill(Color.web("#e67e22"));

        header.getChildren().addAll(playerLabel, statusLabel, scoreLabel, timerLabel);
        return header;
    }

    private VBox createBoard() {
        VBox container = new VBox(15);
        container.setAlignment(Pos.CENTER);

        GridPane boardPane = new GridPane();
        boardPane.setAlignment(Pos.CENTER);
        boardPane.setHgap(8);
        boardPane.setVgap(8);

        int buttonSize = calculateButtonSize();

        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                Button btn = new Button("");
                btn.setMinSize(buttonSize, buttonSize);
                btn.setMaxSize(buttonSize, buttonSize);
                btn.setFont(Font.font("Arial", FontWeight.BOLD, buttonSize / 3));
                btn.setStyle("-fx-background-color: #353b48; -fx-text-fill: white; " +
                        "-fx-background-radius: 10; -fx-cursor: hand;");
                btn.setDisable(true);

                int r = i;
                int c = j;

                btn.setOnAction(e -> makeMove(r, c));

                btn.setOnMouseEntered(e -> {
                    if (!btn.isDisabled() && myTurn) {
                        btn.setStyle("-fx-background-color: #636e72; -fx-text-fill: white; " +
                                "-fx-background-radius: 10; -fx-cursor: hand;");
                    }
                });

                btn.setOnMouseExited(e -> {
                    if (board[r][c] == ' ') {
                        btn.setStyle("-fx-background-color: #353b48; -fx-text-fill: white; " +
                                "-fx-background-radius: 10; -fx-cursor: hand;");
                    }
                });

                buttons[i][j] = btn;
                board[i][j] = ' ';
                boardPane.add(btn, j, i);
            }
        }

        container.getChildren().add(boardPane);
        return container;
    }

    private int calculateButtonSize() {
        if (size == 3) return 120;
        if (size == 5) return 80;
        return 60; // for 7x7
    }

    private VBox createChatBox() {
        VBox chatContainer = new VBox(10);
        chatContainer.setAlignment(Pos.TOP_CENTER);
        chatContainer.setPrefWidth(250);
        chatContainer.setStyle("-fx-background-color: #353b48; -fx-background-radius: 10; -fx-padding: 15;");

        Label chatTitle = new Label("💬 Chat");
        chatTitle.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        chatTitle.setTextFill(Color.WHITE);

        chatArea = new TextArea();
        chatArea.setEditable(false);
        chatArea.setWrapText(true);
        chatArea.setPrefHeight(400);
        chatArea.setStyle("-fx-control-inner-background: #2d3436; -fx-text-fill: white; " +
                "-fx-background-radius: 5;");

        chatInput = new TextField();
        chatInput.setPromptText("Type message...");
        chatInput.setStyle("-fx-background-color: #2d3436; -fx-text-fill: white; " +
                "-fx-prompt-text-fill: #95a5a6; -fx-background-radius: 5;");
        chatInput.setOnAction(e -> sendChat());

        Button sendBtn = new Button("Send");
        sendBtn.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; " +
                "-fx-font-weight: bold; -fx-cursor: hand;");
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

        Button rematchBtn = createStyledButton("🔃 Rematch", "#9b59b6");
        rematchBtn.setOnAction(e -> requestRematch());

        Button backBtn = createStyledButton("🏠 Back to Menu", "#3498db");
        backBtn.setOnAction(e -> {
            stopTimer();
            disconnect();
            primaryStage.close();
        });

        Button exitBtn = createStyledButton("❌ Exit", "#e74c3c");
        exitBtn.setOnAction(e -> {
            stopTimer();
            disconnect();
            Platform.exit();
        });

        controls.getChildren().addAll(rematchBtn, backBtn, exitBtn);
        return controls;
    }

    private Button createStyledButton(String text, String color) {
        Button btn = new Button(text);
        btn.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        btn.setStyle("-fx-background-color: " + color + "; -fx-text-fill: white; " +
                "-fx-padding: 10 20; -fx-background-radius: 5; -fx-cursor: hand;");

        btn.setOnMouseEntered(e ->
                btn.setStyle("-fx-background-color: derive(" + color + ", 20%); -fx-text-fill: white; " +
                        "-fx-padding: 10 20; -fx-background-radius: 5; -fx-cursor: hand;"));

        btn.setOnMouseExited(e ->
                btn.setStyle("-fx-background-color: " + color + "; -fx-text-fill: white; " +
                        "-fx-padding: 10 20; -fx-background-radius: 5; -fx-cursor: hand;"));

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

    private void connectToServer() {
        new Thread(() -> {
            try {
                Platform.runLater(() -> {
                    statusLabel.setText("Connecting to server...");
                    statusLabel.setTextFill(Color.web("#95a5a6"));
                });

                socket = new Socket(serverAddress, port);
                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                // Send username and grid size
                out.println("NAME:" + username);
                out.println("GRIDSIZE:" + size);

                Platform.runLater(() -> {
                    statusLabel.setText("✓ Connected! Waiting for game...");
                    statusLabel.setTextFill(Color.web("#2ecc71"));
                });

                String msg;
                while ((msg = in.readLine()) != null) {
                    String finalMsg = msg;
                    Platform.runLater(() -> processMessage(finalMsg));
                }
            } catch (IOException ex) {
                Platform.runLater(() -> {
                    statusLabel.setText("❌ Connection lost");
                    statusLabel.setTextFill(Color.web("#e74c3c"));
                    disableAllButtons();

                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Connection Error");
                    alert.setHeaderText("Lost connection to server");
                    alert.setContentText("The connection was interrupted.");
                    alert.showAndWait();
                });
            }
        }).start();
    }

    private void processMessage(String msg) {
        if (msg.startsWith("SYMBOL:")) {
            mySymbol = msg.charAt(7);
            playerLabel.setText(username + " (" + mySymbol + ") - " + size + "×" + size);
            playerLabel.setTextFill(mySymbol == 'X' ? Color.web("#3498db") : Color.web("#e74c3c"));

        } else if (msg.startsWith("MESSAGE:")) {
            String message = msg.substring(8);
            statusLabel.setText(message);
            if (message.contains("started")) {
                startTimer();
            }

        } else if (msg.equals("YOUR_TURN")) {
            myTurn = true;
            statusLabel.setText("🎯 Your turn!");
            statusLabel.setTextFill(Color.web("#2ecc71"));
            enableEmptyButtons();

        } else if (msg.equals("WAIT_TURN")) {
            myTurn = false;
            statusLabel.setText("⏳ Opponent's turn...");
            statusLabel.setTextFill(Color.web("#f39c12"));
            disableAllButtons();

        } else if (msg.startsWith("UPDATE:")) {
            updateBoard(msg);

        } else if (msg.startsWith("GAME_OVER:")) {
            showGameOver(msg.substring(10));

        } else if (msg.startsWith("SCORES:")) {
            updateScores(msg);

        } else if (msg.startsWith("CHAT:")) {
            displayChat(msg);

        } else if (msg.equals("REMATCH_START")) {
            resetBoard();
            addChatMessage("System", "Rematch started!");
            startTimer();
        } else if (msg.equals("GRIDSIZE_MISMATCH")) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Grid Size Mismatch");
            alert.setHeaderText("Cannot join game");
            alert.setContentText("The game room uses a different grid size. Please select the same grid size as the host.");
            alert.showAndWait();
            disconnect();
            primaryStage.close();
        }
    }

    private void updateBoard(String msg) {
        String[] p = msg.substring(7).split(",");
        int row = Integer.parseInt(p[0].trim());
        int col = Integer.parseInt(p[1].trim());
        char symbol = p[2].trim().charAt(0);

        board[row][col] = symbol;
        buttons[row][col].setText(String.valueOf(symbol));
        buttons[row][col].setDisable(true);

        String color = (symbol == 'X') ? "#3498db" : "#e74c3c";
        buttons[row][col].setStyle("-fx-background-color: #353b48; -fx-text-fill: " + color + "; " +
                "-fx-background-radius: 10; -fx-font-size: " + (calculateButtonSize() / 3) + ";");

        ScaleTransition st = new ScaleTransition(Duration.millis(200), buttons[row][col]);
        st.setFromX(0.8);
        st.setFromY(0.8);
        st.setToX(1.0);
        st.setToY(1.0);
        st.play();
    }

    private void updateScores(String msg) {
        String[] scores = msg.substring(7).split(",");
        int xScore = Integer.parseInt(scores[0].trim());
        int oScore = Integer.parseInt(scores[1].trim());

        if (mySymbol == 'X') {
            myScore = xScore;
            opponentScore = oScore;
        } else {
            myScore = oScore;
            opponentScore = xScore;
        }

        scoreLabel.setText("Score - You: " + myScore + " | Opponent: " + opponentScore);
    }

    private void showGameOver(String result) {
        disableAllButtons();
        stopTimer();

        if (result.contains("wins") && result.contains(String.valueOf(mySymbol))) {
            statusLabel.setText("🎉 " + result);
            statusLabel.setTextFill(Color.web("#2ecc71"));

            double gameTime = (System.currentTimeMillis() - gameStartTime) / 1000.0;
            JavaFXLauncher.addLeaderboardEntry(username, myScore, gameTime, "Online", size);
        } else if (result.contains("wins")) {
            statusLabel.setText("😔 " + result);
            statusLabel.setTextFill(Color.web("#e74c3c"));
        } else {
            statusLabel.setText("🤝 " + result);
            statusLabel.setTextFill(Color.web("#f39c12"));
        }

        PauseTransition pause = new PauseTransition(Duration.millis(500));
        pause.setOnFinished(e -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Game Over");
            alert.setHeaderText(result);
            alert.setContentText("Score - You: " + myScore + " | Opponent: " + opponentScore);
            alert.show();
        });
        pause.play();
    }

    private void sendChat() {
        String message = chatInput.getText().trim();
        if (!message.isEmpty() && out != null) {
            out.println("CHAT:" + message);
            chatInput.clear();
        }
    }

    private void displayChat(String msg) {
        String[] parts = msg.substring(5).split(":", 2);
        if (parts.length == 2) {
            String sender = parts[0];
            String message = parts[1];
            addChatMessage(sender, message);
        }
    }

    private void addChatMessage(String sender, String message) {
        chatArea.appendText(sender + ": " + message + "\n");
    }

    private void makeMove(int r, int c) {
        if (!myTurn) {
            statusLabel.setText("⚠ Wait for your turn!");
            statusLabel.setTextFill(Color.web("#e74c3c"));
            return;
        }

        if (board[r][c] != ' ') {
            return;
        }

        if (out != null) {
            out.println("MOVE:" + r + "," + c);
        }
    }

    private void requestRematch() {
        if (out != null) {
            out.println("REMATCH");
            addChatMessage("You", "Requested rematch");
        }
    }

    private void resetBoard() {
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                buttons[i][j].setText("");
                buttons[i][j].setDisable(true);
                buttons[i][j].setStyle("-fx-background-color: #353b48; -fx-text-fill: white; " +
                        "-fx-background-radius: 10; -fx-cursor: hand;");
                board[i][j] = ' ';
            }
        }
        myTurn = false;
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

    private void disconnect() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (Exception ignored) {}
    }
}