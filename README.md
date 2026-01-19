# 🎮 Modern JavaFX Tic-Tac-Toe

A feature-rich, graphical Tic-Tac-Toe game built with **Java** and **JavaFX**. This project goes beyond the basics, offering dynamic grid sizes, a smart AI opponent, local multiplayer, and a robust real-time online multiplayer mode with chat functionality.

## ✨ Key Features

* **Three Game Modes:**
* 🤖 **VS Computer:** Challenge an AI with three difficulty levels (Easy, Medium, Hard using **Minimax algorithm**).
* 👥 **VS Friend:** Local multiplayer on the same device.
* 🌐 **Online Multiplayer:** Play across different computers via TCP Sockets.


* **Dynamic Grid Sizes:** Support for standard **3x3**, complex **5x5**, and massive **7x7** grids.
* **Online Features:**
* Real-time gameplay synchronization.
* **In-game Chat** system.
* Server room management (supports multiple concurrent games).
* Rematch functionality.


* **Modern UI:**
* Smooth animations (Fade, Scale, Translate).
* Gradient backgrounds and glass-morphism effects.
* Responsive layout.


* **Leaderboard System:** Local persistence (serialization) to track wins, best times, and total games played.

## 🛠️ Tech Stack

* **Language:** Java (JDK 17+ recommended)
* **GUI Framework:** JavaFX
* **Networking:** Java Sockets (`java.net`), Multi-threading (`ExecutorService`)
* **Data Persistence:** Java Serialization (`Serializable`)

## 📂 Project Structure

| File | Description |
| --- | --- |
| `JavaFXLauncher.java` | **Entry Point.** Manages the Main Menu, Leaderboard, Settings, and navigates between views. |
| `GameWindow.java` | Handles the game logic and UI for **Local** and **Computer** modes. Implements the Minimax AI. |
| `OnlineGameClient.java` | The client-side logic for **Online** mode. Handles server communication and updates the UI based on network packets. |
| `GameServer.java` | A standalone multi-threaded server. Manages client connections, matchmaking, game rooms, and broadcasting moves/chat. |

## 🚀 Installation & Setup

### Prerequisites

1. **Java Development Kit (JDK):** Version 11 or higher.
2. **JavaFX SDK:** Ensure JavaFX is configured in your library path.

### Running in an IDE (IntelliJ IDEA / Eclipse)

1. Create a new Java project.
2. Add the provided `.java` files to your source folder (package `com.example.project`).
3. Add the **JavaFX Library** to your project dependencies.
4. Run `JavaFXLauncher.java`.

### Running via Command Line

If you have the JavaFX SDK downloaded manually:

**1. Compile:**

```bash
javac --module-path /path/to/javafx/lib --add-modules javafx.controls,javafx.fxml -d bin src/com/example/project/*.java

```

**2. Run:**

```bash
java --module-path /path/to/javafx/lib --add-modules javafx.controls,javafx.fxml -cp bin com.example.project.JavaFXLauncher

```

## 🎮 How to Play

### Online Multiplayer Guide

The application uses a Client-Server architecture.

1. **Hosting a Game:**
* The application automatically starts a background server instance on port `6000` when launched.
* Click **Online Multiplayer** -> **Host Game**.
* Share your **IP Address** (displayed on screen) with your friend.


2. **Joining a Game:**
* Click **Online Multiplayer** -> **Join Game**.
* Enter the Host's **IP Address** and ensure the Port is `6000`.
* **Important:** You must select the same **Grid Size** as the host.



### AI Difficulty Logic

* **Easy:** Makes random moves.
* **Medium:** Blocks immediate threats or takes winning moves; otherwise plays randomly.
* **Hard:** Uses the **Minimax** algorithm (with depth limiting for larger grids) to calculate the optimal move. Unbeatable in 3x3.

## 📡 Network Protocol

The client and server communicate using simple string-based text commands:

* `MOVE:row,col` - Player sent a move.
* `UPDATE:row,col,symbol` - Server broadcasts a move update.
* `CHAT:message` - Chat message payload.
* `GRIDSIZE:n` - Handshake to establish room size.
* `REMATCH` - Request to restart the game.

## 📸 Future Improvements

* [ ] Add sound effects for moves and wins.
* [ ] Implement a centralized database for a global leaderboard.
* [ ] Add spectator mode for online games.

---

**Created by Aslam, Ryan, Dawood, Ariz, Safwan, Adam, Fawzia, Zarifa**

*University Putra Malaysia - Computer Science*
---
