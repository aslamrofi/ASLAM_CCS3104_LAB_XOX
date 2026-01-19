package com.example.project;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.*;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory; // Added for explicit cell factories if needed
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.*;
import javafx.stage.Stage;
import javafx.scene.effect.*;
import javafx.animation.*;
import javafx.util.Duration;
import javafx.beans.property.*;

import java.io.*;
import java.net.InetAddress;
import java.sql.*; // Added for Oracle DB
import java.util.*;

public class JavaFXLauncher extends Application {

    private Stage primaryStage;
    private String username = "";
    private VBox mainContainer;
    private int selectedGridSize = 3;

    // ================= ORACLE DATABASE CONFIGURATION =================
    private static final String DB_URL = "jdbc:oracle:thin:@fsktmdbora.upm.edu.my:1521:fsktm";
    private static final String DB_USER = "A226920";
    private static final String DB_PASS = "226920";

    // Initialize Database (Create table if it doesn't exist)
    static {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             Statement stmt = conn.createStatement()) {

            // Simple table creation - catches exception if table already exists
            try {
                String sql = "CREATE TABLE GAME_LEADERBOARD (" +
                        "PLAYER_NAME VARCHAR2(50) PRIMARY KEY, " +
                        "TOTAL_WINS INT DEFAULT 0, " +
                        "TOTAL_GAMES INT DEFAULT 0, " +
                        "BEST_TIME NUMBER(10, 2) DEFAULT 9999.99, " +
                        "LAST_MODE VARCHAR2(20), " +
                        "GRID_SIZE INT)";
                stmt.executeUpdate(sql);
                System.out.println("Database table checked/created successfully.");
            } catch (SQLException e) {
                // Table likely exists, safe to ignore
                // System.out.println("Table already exists or error: " + e.getMessage());
            }
        } catch (SQLException e) {
            e.printStackTrace();
            System.err.println("CRITICAL: Database Connection Failed! Check VPN/Network.");
        }
    }

    // Data structure for the TableView (Logic handled by DB now, but this maps the result)
    public static class LeaderboardEntry {
        int rank;
        String playerName;
        int wins;
        String bestTime;
        String mode;
        int totalGames;
        int gridSize;

        public LeaderboardEntry(int rank, String playerName, int wins, String bestTime, String mode, int totalGames, int gridSize) {
            this.rank = rank;
            this.playerName = playerName;
            this.wins = wins;
            this.bestTime = bestTime;
            this.mode = mode;
            this.totalGames = totalGames;
            this.gridSize = gridSize;
        }

        // Getters are required for PropertyValueFactory if you use it,
        // but here we used lambdas in the TableView setup which access fields directly.
    }

    // ================= DATABASE METHODS =================

    public static void addLeaderboardEntry(String playerName, int wonThisGame, double timeInSeconds, String mode, int gridSize) {
        String checkSql = "SELECT TOTAL_WINS, TOTAL_GAMES, BEST_TIME FROM GAME_LEADERBOARD WHERE PLAYER_NAME = ?";
        String insertSql = "INSERT INTO GAME_LEADERBOARD (PLAYER_NAME, TOTAL_WINS, TOTAL_GAMES, BEST_TIME, LAST_MODE, GRID_SIZE) VALUES (?, ?, 1, ?, ?, ?)";
        String updateSql = "UPDATE GAME_LEADERBOARD SET TOTAL_WINS = ?, TOTAL_GAMES = ?, BEST_TIME = ?, LAST_MODE = ?, GRID_SIZE = ? WHERE PLAYER_NAME = ?";

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
            // 1. Check if player exists
            try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                checkStmt.setString(1, playerName);
                ResultSet rs = checkStmt.executeQuery();

                if (rs.next()) {
                    // 2. Update existing player
                    int currentWins = rs.getInt("TOTAL_WINS");
                    int currentGames = rs.getInt("TOTAL_GAMES");
                    double currentBest = rs.getDouble("BEST_TIME");

                    try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                        updateStmt.setInt(1, currentWins + wonThisGame);
                        updateStmt.setInt(2, currentGames + 1);
                        // Update best time only if new time is faster (and they won, optionally, usually time matters on win)
                        // Here we just take the lowest time seen
                        updateStmt.setDouble(3, Math.min(currentBest, timeInSeconds));
                        updateStmt.setString(4, mode);
                        updateStmt.setInt(5, gridSize);
                        updateStmt.setString(6, playerName);
                        updateStmt.executeUpdate();
                    }
                } else {
                    // 3. Insert new player
                    try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                        insertStmt.setString(1, playerName);
                        insertStmt.setInt(2, wonThisGame);
                        insertStmt.setDouble(3, timeInSeconds);
                        insertStmt.setString(4, mode);
                        insertStmt.setInt(5, gridSize);
                        insertStmt.executeUpdate();
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("DB Error Adding Entry: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static List<LeaderboardEntry> getTopPlayers() {
        List<LeaderboardEntry> entries = new ArrayList<>();
        // Fetch top 10 players sorted by Wins (Desc) then Best Time (Asc)
        String query = "SELECT * FROM GAME_LEADERBOARD ORDER BY TOTAL_WINS DESC, BEST_TIME ASC FETCH FIRST 10 ROWS ONLY";

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {

            int rank = 1;
            while (rs.next()) {
                entries.add(new LeaderboardEntry(
                        rank++,
                        rs.getString("PLAYER_NAME"),
                        rs.getInt("TOTAL_WINS"),
                        String.format("%.1fs", rs.getDouble("BEST_TIME")),
                        rs.getString("LAST_MODE"),
                        rs.getInt("TOTAL_GAMES"),
                        rs.getInt("GRID_SIZE")
                ));
            }
        } catch (SQLException e) {
            System.err.println("DB Error Fetching Leaderboard: " + e.getMessage());
        }
        return entries;
    }

    public static void clearLeaderboard() {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DELETE FROM GAME_LEADERBOARD");
        } catch (SQLException e) {
            System.err.println("Error clearing database: " + e.getMessage());
        }
    }

    // ================= GUI CODE STARTS HERE =================

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        stage.setTitle("Tic-Tac-Toe");

        // Auto-start server in background
        startServerInBackground();

        // Small delay to let server start
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        showUsernameDialog();
    }

    private void showUsernameDialog() {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Welcome");
        dialog.setHeaderText(null);

        VBox content = new VBox(20);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(40));
        content.setStyle("-fx-background: linear-gradient(to bottom right, #667eea 0%, #764ba2 100%);");

        Label welcomeLabel = new Label("🎮 TIC-TAC-TOE");
        welcomeLabel.setFont(Font.font("Arial", FontWeight.BOLD, 36));
        welcomeLabel.setTextFill(Color.WHITE);

        DropShadow glow = new DropShadow();
        glow.setColor(Color.web("#ffffff", 0.8));
        glow.setRadius(25);
        welcomeLabel.setEffect(glow);

        Label subLabel = new Label("Enter your username to begin");
        subLabel.setFont(Font.font("Arial", FontWeight.NORMAL, 16));
        subLabel.setTextFill(Color.web("#ffffff", 0.95));

        TextField usernameField = new TextField();
        usernameField.setPromptText("Enter username...");
        usernameField.setStyle("-fx-font-size: 16; -fx-padding: 15; -fx-background-radius: 15; " +
                "-fx-border-radius: 15; -fx-border-color: white; -fx-border-width: 2;");
        usernameField.setMaxWidth(350);

        content.getChildren().addAll(welcomeLabel, subLabel, usernameField);

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.getDialogPane().setStyle("-fx-background-color: transparent;");

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                return usernameField.getText().trim();
            }
            return null;
        });

        dialog.showAndWait().ifPresentOrElse(name -> {
            if (name.isEmpty()) {
                showAlert("Error", "Username cannot be empty!", Alert.AlertType.ERROR);
                showUsernameDialog();
            } else {
                username = name;
                showMainMenu();
            }
        }, Platform::exit);
    }

    private void showMainMenu() {
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background: linear-gradient(to bottom right, #667eea 0%, #764ba2 100%);");

        mainContainer = new VBox(0);
        mainContainer.setAlignment(Pos.CENTER);
        mainContainer.setFillWidth(true);

        showPlayView();

        ScrollPane scrollPane = new ScrollPane(mainContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        root.setCenter(scrollPane);

        HBox navBar = createNavigationBar();
        root.setBottom(navBar);

        Scene scene = new Scene(root, 950, 750);
        primaryStage.setScene(scene);
        primaryStage.setResizable(false);
        primaryStage.show();

        FadeTransition fade = new FadeTransition(Duration.millis(600), root);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.play();
    }

    private HBox createNavigationBar() {
        HBox navBar = new HBox(0);
        navBar.setAlignment(Pos.CENTER);
        navBar.setStyle("-fx-background-color: rgba(0, 0, 0, 0.4); -fx-padding: 15;");

        Button playBtn = createNavButton("🎮 Play", e -> showPlayView());
        Button leaderboardBtn = createNavButton("🏆 Leaderboard", e -> showLeaderboardView());
        Button settingsBtn = createNavButton("⚙ Settings", e -> showSettingsView());

        navBar.getChildren().addAll(playBtn, leaderboardBtn, settingsBtn);

        // Add hover animation
        Timeline pulse = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(navBar.opacityProperty(), 0.9)),
                new KeyFrame(Duration.seconds(2), new KeyValue(navBar.opacityProperty(), 1.0))
        );
        pulse.setAutoReverse(true);
        pulse.setCycleCount(Timeline.INDEFINITE);
        pulse.play();

        return navBar;
    }

    private Button createNavButton(String text, javafx.event.EventHandler<javafx.event.ActionEvent> handler) {
        Button btn = new Button(text);
        btn.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        btn.setMinWidth(300);
        btn.setMinHeight(55);
        btn.setStyle("-fx-background-color: transparent; -fx-text-fill: white; " +
                "-fx-border-color: white; -fx-border-width: 2; -fx-border-radius: 12; " +
                "-fx-background-radius: 12; -fx-cursor: hand;");

        btn.setOnAction(handler);

        btn.setOnMouseEntered(e -> {
            btn.setStyle("-fx-background-color: rgba(255, 255, 255, 0.25); -fx-text-fill: white; " +
                    "-fx-border-color: white; -fx-border-width: 2; -fx-border-radius: 12; " +
                    "-fx-background-radius: 12; -fx-cursor: hand;");
            ScaleTransition st = new ScaleTransition(Duration.millis(150), btn);
            st.setToX(1.08);
            st.setToY(1.08);
            st.play();
        });

        btn.setOnMouseExited(e -> {
            btn.setStyle("-fx-background-color: transparent; -fx-text-fill: white; " +
                    "-fx-border-color: white; -fx-border-width: 2; -fx-border-radius: 12; " +
                    "-fx-background-radius: 12; -fx-cursor: hand;");
            ScaleTransition st = new ScaleTransition(Duration.millis(150), btn);
            st.setToX(1.0);
            st.setToY(1.0);
            st.play();
        });

        return btn;
    }

    private void showPlayView() {
        mainContainer.getChildren().clear();

        VBox playBox = new VBox(35);
        playBox.setAlignment(Pos.CENTER);
        playBox.setPadding(new Insets(45));

        Label titleLabel = new Label("🎮 TIC-TAC-TOE");
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 46));
        titleLabel.setTextFill(Color.WHITE);

        DropShadow glow = new DropShadow();
        glow.setColor(Color.web("#ffffff", 0.7));
        glow.setRadius(25);
        titleLabel.setEffect(glow);

        Label playerLabel = new Label("Welcome, " + username + "!");
        playerLabel.setFont(Font.font("Arial", FontWeight.SEMI_BOLD, 20));
        playerLabel.setTextFill(Color.web("#ffffff", 0.95));

        HBox gridSelector = createGridSelector();

        Label selectLabel = new Label("Select Game Mode");
        selectLabel.setFont(Font.font("Arial", FontWeight.BOLD, 28));
        selectLabel.setTextFill(Color.WHITE);

        VBox modesBox = new VBox(22);
        modesBox.setAlignment(Pos.CENTER);

        Button computerBtn = createGameModeCard("🤖 VS Computer",
                "Challenge AI with adjustable difficulty levels", "#9b59b6");
        computerBtn.setOnAction(e -> startComputerGame());

        Button friendBtn = createGameModeCard("👥 VS Friend",
                "Play locally on the same device", "#3498db");
        friendBtn.setOnAction(e -> startFriendGame());

        Button onlineBtn = createGameModeCard("🌐 Online Multiplayer",
                "Connect and compete with players worldwide", "#2ecc71");
        onlineBtn.setOnAction(e -> showOnlineOptions());

        modesBox.getChildren().addAll(computerBtn, friendBtn, onlineBtn);
        playBox.getChildren().addAll(titleLabel, playerLabel, gridSelector, selectLabel, modesBox);
        mainContainer.getChildren().add(playBox);

        FadeTransition fade = new FadeTransition(Duration.millis(400), playBox);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.play();
    }

    private HBox createGridSelector() {
        HBox gridBox = new HBox(18);
        gridBox.setAlignment(Pos.CENTER);
        gridBox.setStyle("-fx-background-color: rgba(255, 255, 255, 0.2); " +
                "-fx-padding: 18; -fx-background-radius: 18;");

        Label gridLabel = new Label("Grid Size:");
        gridLabel.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        gridLabel.setTextFill(Color.WHITE);

        ToggleGroup gridGroup = new ToggleGroup();

        RadioButton grid3 = createGridRadioButton("3×3", 3, gridGroup);
        RadioButton grid5 = createGridRadioButton("5×5", 5, gridGroup);
        RadioButton grid7 = createGridRadioButton("7×7", 7, gridGroup);

        grid3.setSelected(true);

        gridBox.getChildren().addAll(gridLabel, grid3, grid5, grid7);

        // Add subtle animation
        ScaleTransition st = new ScaleTransition(Duration.millis(300), gridBox);
        st.setFromX(0.95);
        st.setFromY(0.95);
        st.setToX(1.0);
        st.setToY(1.0);
        st.play();

        return gridBox;
    }

    private RadioButton createGridRadioButton(String text, int size, ToggleGroup group) {
        RadioButton rb = new RadioButton(text);
        rb.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        rb.setTextFill(Color.WHITE);
        rb.setToggleGroup(group);
        rb.setUserData(size);
        rb.setStyle("-fx-text-fill: white;");

        rb.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                selectedGridSize = (int) rb.getUserData();
                ScaleTransition st = new ScaleTransition(Duration.millis(150), rb);
                st.setToX(1.1);
                st.setToY(1.1);
                st.setAutoReverse(true);
                st.setCycleCount(2);
                st.play();
            }
        });

        return rb;
    }

    private Button createGameModeCard(String title, String description, String color) {
        Button card = new Button();
        card.setMinWidth(550);
        card.setMinHeight(110);
        card.setStyle("-fx-background-color: " + color + "; " +
                "-fx-background-radius: 22; -fx-cursor: hand; " +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.5), 20, 0, 0, 6);");

        VBox content = new VBox(10);
        content.setAlignment(Pos.CENTER);

        Label titleLabel = new Label(title);
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 26));
        titleLabel.setTextFill(Color.WHITE);

        Label descLabel = new Label(description);
        descLabel.setFont(Font.font("Arial", FontWeight.NORMAL, 15));
        descLabel.setTextFill(Color.web("#ffffff", 0.92));

        content.getChildren().addAll(titleLabel, descLabel);
        card.setGraphic(content);

        card.setOnMouseEntered(e -> {
            card.setStyle("-fx-background-color: derive(" + color + ", 18%); " +
                    "-fx-background-radius: 22; -fx-cursor: hand; " +
                    "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.7), 25, 0, 0, 10);");
            ScaleTransition st = new ScaleTransition(Duration.millis(200), card);
            st.setToX(1.04);
            st.setToY(1.04);
            st.play();
        });

        card.setOnMouseExited(e -> {
            card.setStyle("-fx-background-color: " + color + "; " +
                    "-fx-background-radius: 22; -fx-cursor: hand; " +
                    "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.5), 20, 0, 0, 6);");
            ScaleTransition st = new ScaleTransition(Duration.millis(200), card);
            st.setToX(1.0);
            st.setToY(1.0);
            st.play();
        });

        return card;
    }

    private void showLeaderboardView() {
        mainContainer.getChildren().clear();

        VBox leaderboardBox = new VBox(28);
        leaderboardBox.setAlignment(Pos.TOP_CENTER);
        leaderboardBox.setPadding(new Insets(45));

        Label titleLabel = new Label("🏆 LEADERBOARD");
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 46));
        titleLabel.setTextFill(Color.WHITE);

        DropShadow glow = new DropShadow();
        glow.setColor(Color.web("#ffffff", 0.7));
        glow.setRadius(25);
        titleLabel.setEffect(glow);

        Label subLabel = new Label("Top Players by Performance (Oracle DB)");
        subLabel.setFont(Font.font("Arial", FontWeight.NORMAL, 17));
        subLabel.setTextFill(Color.web("#ffffff", 0.95));

        TableView<LeaderboardEntry> table = createLeaderboardTable();

        leaderboardBox.getChildren().addAll(titleLabel, subLabel, table);
        mainContainer.getChildren().add(leaderboardBox);

        FadeTransition fade = new FadeTransition(Duration.millis(400), leaderboardBox);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.play();
    }

    private TableView<LeaderboardEntry> createLeaderboardTable() {
        TableView<LeaderboardEntry> table = new TableView<>();
        table.setStyle("-fx-background-color: rgba(255, 255, 255, 0.15); " +
                "-fx-border-color: rgba(255, 255, 255, 0.4); -fx-border-radius: 18; " +
                "-fx-background-radius: 18;");
        table.setPrefHeight(420);
        table.setMaxWidth(850);

        TableColumn<LeaderboardEntry, Integer> rankCol = new TableColumn<>("Rank");
        rankCol.setCellValueFactory(data ->
                new SimpleIntegerProperty(data.getValue().rank).asObject());
        rankCol.setPrefWidth(70);
        rankCol.setStyle("-fx-alignment: CENTER;");

        TableColumn<LeaderboardEntry, String> nameCol = new TableColumn<>("Player");
        nameCol.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().playerName));
        nameCol.setPrefWidth(180);

        TableColumn<LeaderboardEntry, Integer> gamesCol = new TableColumn<>("Games");
        gamesCol.setCellValueFactory(data ->
                new SimpleIntegerProperty(data.getValue().totalGames).asObject());
        gamesCol.setPrefWidth(90);
        gamesCol.setStyle("-fx-alignment: CENTER;");

        TableColumn<LeaderboardEntry, Integer> winsCol = new TableColumn<>("Wins");
        winsCol.setCellValueFactory(data ->
                new SimpleIntegerProperty(data.getValue().wins).asObject());
        winsCol.setPrefWidth(90);
        winsCol.setStyle("-fx-alignment: CENTER;");

        TableColumn<LeaderboardEntry, String> timeCol = new TableColumn<>("Best Time");
        timeCol.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().bestTime));
        timeCol.setPrefWidth(110);
        timeCol.setStyle("-fx-alignment: CENTER;");

        TableColumn<LeaderboardEntry, String> modeCol = new TableColumn<>("Mode");
        modeCol.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().mode));
        modeCol.setPrefWidth(110);
        modeCol.setStyle("-fx-alignment: CENTER;");

        TableColumn<LeaderboardEntry, String> gridCol = new TableColumn<>("Grid");
        gridCol.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().gridSize + "×" + data.getValue().gridSize));
        gridCol.setPrefWidth(80);
        gridCol.setStyle("-fx-alignment: CENTER;");

        table.getColumns().addAll(rankCol, nameCol, gamesCol, winsCol, timeCol, modeCol, gridCol);

        // Load data from Oracle DB
        table.getItems().addAll(getTopPlayers());

        return table;
    }

    private void showSettingsView() {
        mainContainer.getChildren().clear();

        VBox settingsBox = new VBox(32);
        settingsBox.setAlignment(Pos.CENTER);
        settingsBox.setPadding(new Insets(45));

        Label titleLabel = new Label("⚙ SETTINGS");
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 46));
        titleLabel.setTextFill(Color.WHITE);

        DropShadow glow = new DropShadow();
        glow.setColor(Color.web("#ffffff", 0.7));
        glow.setRadius(25);
        titleLabel.setEffect(glow);

        VBox settingsCard = new VBox(25);
        settingsCard.setAlignment(Pos.CENTER);
        settingsCard.setStyle("-fx-background-color: rgba(255, 255, 255, 0.2); " +
                "-fx-padding: 35; -fx-background-radius: 22;");
        settingsCard.setMaxWidth(650);

        HBox usernameBox = new HBox(18);
        usernameBox.setAlignment(Pos.CENTER);
        Label usernameLabel = new Label("Username:");
        usernameLabel.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        usernameLabel.setTextFill(Color.WHITE);
        TextField usernameField = new TextField(username);
        usernameField.setPrefWidth(280);
        usernameField.setStyle("-fx-font-size: 16; -fx-padding: 10; -fx-background-radius: 10;");
        Button updateBtn = new Button("Update");
        updateBtn.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; " +
                "-fx-font-weight: bold; -fx-padding: 10 25; -fx-background-radius: 10; -fx-cursor: hand;");
        updateBtn.setOnAction(e -> {
            String newName = usernameField.getText().trim();
            if (!newName.isEmpty()) {
                username = newName;
                showAlert("Success", "Username updated successfully!", Alert.AlertType.INFORMATION);
                showPlayView();
            }
        });
        usernameBox.getChildren().addAll(usernameLabel, usernameField, updateBtn);

        Button clearStatsBtn = new Button("🗑 Clear Leaderboard (DB)");
        clearStatsBtn.setMinWidth(350);
        clearStatsBtn.setMinHeight(55);
        clearStatsBtn.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; " +
                "-fx-font-weight: bold; -fx-font-size: 17; -fx-background-radius: 12; -fx-cursor: hand;");
        clearStatsBtn.setOnAction(e -> {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Confirm");
            confirm.setHeaderText("Clear all leaderboard data from Database?");
            confirm.setContentText("This action cannot be undone.");
            confirm.showAndWait().ifPresent(response -> {
                if (response == ButtonType.OK) {
                    clearLeaderboard();
                    showAlert("Success", "Database Leaderboard cleared!", Alert.AlertType.INFORMATION);
                }
            });
        });

        Button exitBtn = new Button("❌ Exit Game");
        exitBtn.setMinWidth(350);
        exitBtn.setMinHeight(55);
        exitBtn.setStyle("-fx-background-color: #95a5a6; -fx-text-fill: white; " +
                "-fx-font-weight: bold; -fx-font-size: 17; -fx-background-radius: 12; -fx-cursor: hand;");
        exitBtn.setOnAction(e -> Platform.exit());

        settingsCard.getChildren().addAll(usernameBox, clearStatsBtn, exitBtn);
        settingsBox.getChildren().addAll(titleLabel, settingsCard);
        mainContainer.getChildren().add(settingsBox);

        FadeTransition fade = new FadeTransition(Duration.millis(400), settingsBox);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.play();
    }

    private void startComputerGame() {
        ChoiceDialog<String> dialog = new ChoiceDialog<>("Medium", "Easy", "Medium", "Hard");
        dialog.setTitle("Select Difficulty");
        dialog.setHeaderText("Choose AI Difficulty");
        dialog.setContentText("Difficulty:");

        dialog.showAndWait().ifPresent(difficulty -> {
            Stage gameStage = new Stage();
            GameWindow game = new GameWindow(username, "COMPUTER", difficulty, selectedGridSize);
            game.showGame(gameStage);
        });
    }

    private void startFriendGame() {
        Stage gameStage = new Stage();
        GameWindow game = new GameWindow(username, "FRIEND", null, selectedGridSize);
        game.showGame(gameStage);
    }

    private void showOnlineOptions() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Online Multiplayer");
        dialog.setHeaderText(null);

        VBox content = new VBox(25);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(35));
        content.setStyle("-fx-background: linear-gradient(to bottom right, #667eea 0%, #764ba2 100%);");

        Label titleLabel = new Label("🌐 Online Multiplayer");
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 24));
        titleLabel.setTextFill(Color.WHITE);

        Button hostBtn = new Button("🎮 Host Game");
        styleDialogButton(hostBtn, "#2ecc71");
        hostBtn.setOnAction(e -> {
            dialog.close();
            hostOnlineGame();
        });

        Button joinBtn = new Button("🔗 Join Game");
        styleDialogButton(joinBtn, "#3498db");
        joinBtn.setOnAction(e -> {
            dialog.close();
            joinOnlineGame();
        });

        content.getChildren().addAll(titleLabel, hostBtn, joinBtn);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);
        dialog.getDialogPane().setStyle("-fx-background-color: transparent;");

        dialog.showAndWait();
    }

    private void styleDialogButton(Button btn, String color) {
        btn.setMinWidth(320);
        btn.setMinHeight(65);
        btn.setFont(Font.font("Arial", FontWeight.BOLD, 19));
        btn.setStyle("-fx-background-color: " + color + "; -fx-text-fill: white; " +
                "-fx-background-radius: 16; -fx-cursor: hand;");

        btn.setOnMouseEntered(e -> {
            btn.setStyle("-fx-background-color: derive(" + color + ", 22%); -fx-text-fill: white; " +
                    "-fx-background-radius: 16; -fx-cursor: hand;");
            ScaleTransition st = new ScaleTransition(Duration.millis(100), btn);
            st.setToX(1.05);


            st.setToY(1.05);
            st.play();
        });

        btn.setOnMouseExited(e -> {
            btn.setStyle("-fx-background-color: " + color + "; -fx-text-fill: white; " +
                    "-fx-background-radius: 16; -fx-cursor: hand;");
            ScaleTransition st = new ScaleTransition(Duration.millis(100), btn);
            st.setToX(1.0);
            st.setToY(1.0);
            st.play();
        });
    }

    private void hostOnlineGame() {
        try {
            String localIP = InetAddress.getLocalHost().getHostAddress();

            // Create a custom Stage instead of Alert
            Stage infoStage = new Stage();
            infoStage.setTitle("Host Game - Server Info");

            VBox content = new VBox(20);
            content.setAlignment(Pos.CENTER);
            content.setPadding(new Insets(40));
            content.setStyle("-fx-background: linear-gradient(to bottom right, #2ecc71 0%, #27ae60 100%);");

            Label titleLabel = new Label("🎮 HOSTING GAME");
            titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 32));
            titleLabel.setTextFill(Color.WHITE);

            VBox infoBox = new VBox(15);
            infoBox.setAlignment(Pos.CENTER);
            infoBox.setStyle("-fx-background-color: rgba(255, 255, 255, 0.2); " +
                    "-fx-padding: 30; -fx-background-radius: 20;");

            Label serverLabel = new Label("✅ Server is ready!");
            serverLabel.setFont(Font.font("Arial", FontWeight.BOLD, 20));
            serverLabel.setTextFill(Color.WHITE);

            Label ipLabel = new Label("Your IP: " + localIP);
            ipLabel.setFont(Font.font("Arial", FontWeight.SEMI_BOLD, 18));
            ipLabel.setTextFill(Color.WHITE);

            Label portLabel = new Label("Port: 6000");
            portLabel.setFont(Font.font("Arial", FontWeight.SEMI_BOLD, 18));
            portLabel.setTextFill(Color.WHITE);

            Label gridLabel = new Label("Grid Size: " + selectedGridSize + "×" + selectedGridSize);
            gridLabel.setFont(Font.font("Arial", FontWeight.SEMI_BOLD, 18));
            gridLabel.setTextFill(Color.WHITE);

            Label instructionLabel = new Label("Share this info with your opponent");
            instructionLabel.setFont(Font.font("Arial", FontWeight.NORMAL, 16));
            instructionLabel.setTextFill(Color.web("#ffffff", 0.9));
            instructionLabel.setWrapText(true);
            instructionLabel.setTextAlignment(TextAlignment.CENTER);
            instructionLabel.setMaxWidth(350);

            infoBox.getChildren().addAll(serverLabel, ipLabel, portLabel, gridLabel, instructionLabel);

            Button startBtn = new Button("🚀 Start Game");
            startBtn.setMinWidth(250);
            startBtn.setMinHeight(50);
            startBtn.setFont(Font.font("Arial", FontWeight.BOLD, 18));
            startBtn.setStyle("-fx-background-color: white; -fx-text-fill: #27ae60; " +
                    "-fx-background-radius: 25; -fx-cursor: hand;");

            startBtn.setOnMouseEntered(e ->
                    startBtn.setStyle("-fx-background-color: #ecf0f1; -fx-text-fill: #27ae60; " +
                            "-fx-background-radius: 25; -fx-cursor: hand;"));

            startBtn.setOnMouseExited(e ->
                    startBtn.setStyle("-fx-background-color: white; -fx-text-fill: #27ae60; " +
                            "-fx-background-radius: 25; -fx-cursor: hand;"));

            startBtn.setOnAction(e -> {
                infoStage.close();
                Stage gameStage = new Stage();
                OnlineGameClient client = new OnlineGameClient(username, "localhost", 6000, selectedGridSize);
                client.start(gameStage);
            });

            content.getChildren().addAll(titleLabel, infoBox, startBtn);

            Scene scene = new Scene(content, 500, 550);
            infoStage.setScene(scene);
            infoStage.setResizable(false);
            infoStage.show();

            // Fade in animation
            FadeTransition fade = new FadeTransition(Duration.millis(300), content);
            fade.setFromValue(0);
            fade.setToValue(1);
            fade.play();

        } catch (Exception e) {
            System.err.println("Failed to host game: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void joinOnlineGame() {
        Stage joinStage = new Stage();
        joinStage.setTitle("Join Game");

        VBox content = new VBox(20);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(40));
        content.setStyle("-fx-background: linear-gradient(to bottom right, #3498db 0%, #2980b9 100%);");

        Label titleLabel = new Label("🔗 JOIN GAME");
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 32));
        titleLabel.setTextFill(Color.WHITE);

        VBox formBox = new VBox(15);
        formBox.setAlignment(Pos.CENTER);
        formBox.setStyle("-fx-background-color: rgba(255, 255, 255, 0.2); " +
                "-fx-padding: 30; -fx-background-radius: 20;");
        formBox.setMaxWidth(400);

        Label instructionLabel = new Label("Enter server details to connect");
        instructionLabel.setFont(Font.font("Arial", FontWeight.NORMAL, 16));
        instructionLabel.setTextFill(Color.web("#ffffff", 0.95));

        // IP Address field
        VBox ipBox = new VBox(8);
        Label ipLabel = new Label("Server IP Address:");
        ipLabel.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        ipLabel.setTextFill(Color.WHITE);

        TextField ipField = new TextField("localhost");
        ipField.setPromptText("e.g., 192.168.1.100");
        ipField.setStyle("-fx-font-size: 16; -fx-padding: 12; -fx-background-radius: 10;");
        ipField.setPrefWidth(320);

        ipBox.getChildren().addAll(ipLabel, ipField);

        // Port field
        VBox portBox = new VBox(8);
        Label portLabel = new Label("Port:");
        portLabel.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        portLabel.setTextFill(Color.WHITE);

        TextField portField = new TextField("6000");
        portField.setPromptText("Default: 6000");
        portField.setStyle("-fx-font-size: 16; -fx-padding: 12; -fx-background-radius: 10;");
        portField.setPrefWidth(320);

        portBox.getChildren().addAll(portLabel, portField);

        // Grid size info
        Label gridInfoLabel = new Label("Grid Size: " + selectedGridSize + "×" + selectedGridSize);
        gridInfoLabel.setFont(Font.font("Arial", FontWeight.SEMI_BOLD, 16));
        gridInfoLabel.setTextFill(Color.web("#ffffff", 0.9));

        Label noteLabel = new Label("⚠ Must match host's grid size");
        noteLabel.setFont(Font.font("Arial", FontWeight.NORMAL, 13));
        noteLabel.setTextFill(Color.web("#f39c12"));

        formBox.getChildren().addAll(instructionLabel, ipBox, portBox, gridInfoLabel, noteLabel);

        // Buttons
        HBox buttonBox = new HBox(15);
        buttonBox.setAlignment(Pos.CENTER);

        Button connectBtn = new Button("🚀 Connect");
        connectBtn.setMinWidth(180);
        connectBtn.setMinHeight(50);
        connectBtn.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        connectBtn.setStyle("-fx-background-color: white; -fx-text-fill: #2980b9; " +
                "-fx-background-radius: 25; -fx-cursor: hand;");

        connectBtn.setOnMouseEntered(e ->
                connectBtn.setStyle("-fx-background-color: #ecf0f1; -fx-text-fill: #2980b9; " +
                        "-fx-background-radius: 25; -fx-cursor: hand;"));

        connectBtn.setOnMouseExited(e ->
                connectBtn.setStyle("-fx-background-color: white; -fx-text-fill: #2980b9; " +
                        "-fx-background-radius: 25; -fx-cursor: hand;"));

        Button cancelBtn = new Button("❌ Cancel");
        cancelBtn.setMinWidth(180);
        cancelBtn.setMinHeight(50);
        cancelBtn.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        cancelBtn.setStyle("-fx-background-color: rgba(255, 255, 255, 0.3); -fx-text-fill: white; " +
                "-fx-background-radius: 25; -fx-cursor: hand;");

        cancelBtn.setOnMouseEntered(e ->
                cancelBtn.setStyle("-fx-background-color: rgba(255, 255, 255, 0.4); -fx-text-fill: white; " +
                        "-fx-background-radius: 25; -fx-cursor: hand;"));

        cancelBtn.setOnMouseExited(e ->
                cancelBtn.setStyle("-fx-background-color: rgba(255, 255, 255, 0.3); -fx-text-fill: white; " +
                        "-fx-background-radius: 25; -fx-cursor: hand;"));

        connectBtn.setOnAction(e -> {
            try {
                String ip = ipField.getText().trim();
                int port = Integer.parseInt(portField.getText().trim());

                if (ip.isEmpty()) {
                    Label errorLabel = new Label("❌ Please enter server IP!");
                    errorLabel.setTextFill(Color.web("#e74c3c"));
                    errorLabel.setFont(Font.font("Arial", FontWeight.BOLD, 14));
                    if (!formBox.getChildren().contains(errorLabel)) {
                        formBox.getChildren().add(errorLabel);
                    }
                    return;
                }

                joinStage.close();
                Stage gameStage = new Stage();
                OnlineGameClient client = new OnlineGameClient(username, ip, port, selectedGridSize);
                client.start(gameStage);

            } catch (NumberFormatException ex) {
                Label errorLabel = new Label("❌ Invalid port number!");
                errorLabel.setTextFill(Color.web("#e74c3c"));
                errorLabel.setFont(Font.font("Arial", FontWeight.BOLD, 14));
                if (!formBox.getChildren().contains(errorLabel)) {
                    formBox.getChildren().add(errorLabel);
                }
            }
        });

        cancelBtn.setOnAction(e -> joinStage.close());

        buttonBox.getChildren().addAll(connectBtn, cancelBtn);
        content.getChildren().addAll(titleLabel, formBox, buttonBox);

        Scene scene = new Scene(content, 520, 650);
        joinStage.setScene(scene);
        joinStage.setResizable(false);
        joinStage.show();

        // Fade in animation
        FadeTransition fade = new FadeTransition(Duration.millis(300), content);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.play();
    }

    private void showAlert(String title, String message, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void startServerInBackground() {
        new Thread(() -> {
            try {
                System.out.println("Starting game server in background...");
                GameServer.main(new String[]{});
            } catch (Exception e) {
                System.err.println("Failed to start server: " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
    }

    public static void main(String[] args) {
        launch(args);
    }
}