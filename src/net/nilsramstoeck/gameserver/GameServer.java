package net.nilsramstoeck.gameserver;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Properties;
import org.java_websocket.WebSocket;
import org.java_websocket.drafts.Draft;
import org.java_websocket.exceptions.InvalidDataException;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.handshake.ServerHandshakeBuilder;
import org.java_websocket.server.WebSocketServer;

import com.mysql.cj.exceptions.MysqlErrorNumbers;
import net.nilsramstoeck.util.debug.Debugger;
import net.nilsramstoeck.gameserver.client.Client;
import net.nilsramstoeck.gameserver.message.Message;
import net.nilsramstoeck.security.PasswordManager;

/**
 * Base class for a remotely manageable GameServer
 * 
 * @author Nils Ramstoeck
 *
 */
abstract public class GameServer extends WebSocketServer implements GameServerErrorCode {

	/**
	 * Contains all connected clients
	 */
	private static HashMap<WebSocket, Client> conns;

	/**
	 * Contains all games and their connected clients
	 */
	private static HashMap<String, HashSet<Client>> games;

	/**
	 * Name of the Server
	 */
	public final String NAME;

	/**
	 * Server properties
	 */
	public static Properties properties = null;

	/**
	 * Standard location of the configuration file
	 */
	private static final String CONFIG_LOCATION = "config/server.config";

	/**
	 * SQL Database Garbage Collection Thread
	 */
	private final Thread SQL_GBC_THREAD;

	/**
	 * Sets up the WebSocketServer
	 * 
	 */
	public GameServer() {
		super(new InetSocketAddress(GameServer.getPortProperty()));

		// Load configuration file
		if (GameServer.properties == null) {
			GameServer.properties = GameServer.loadProperties();
		}

		// Initialize properties
		GameServer.conns = new HashMap<WebSocket, Client>();
		GameServer.games = new HashMap<String, HashSet<Client>>();
		this.NAME = GameServer.properties.getProperty("server.name");
		this.SQL_GBC_THREAD = this.createGarbageCollectionThread();
	}

	/**
	 * Connects to the SQL database and starts the garbage collection if necessary
	 */
	@Override
	public void onStart() {
		// read properties from the config file
		String dbUser = GameServer.properties.getProperty("sql.user");
		String dbPass = GameServer.properties.getProperty("sql.pass", "");
		String dbUrl = GameServer.properties.getProperty("sql.url");
		String dbPort = GameServer.properties.getProperty("sql.port");
		String gamedb = GameServer.properties.getProperty("sql.database");
		String logindb = GameServer.properties.getProperty("sql.login_database");

		// connect to database
		if (!GameServerSQL.connect(dbUser, dbPass, dbUrl + ":" + dbPort, gamedb, logindb)) {
			Debugger.debugln("Could not connect to database: " + dbUrl + ":" + gamedb + " login: " + logindb);
			System.exit(1);
		}

		// start garbage collection thread if its not already running
		if (!this.SQL_GBC_THREAD.isAlive()) {
			this.SQL_GBC_THREAD.start();
		}

		Debugger.debugln("Connected to database: " + dbUrl + ":" + gamedb + " login: " + logindb);
		Debugger.debugln("Server started: " + this.getAddress().getHostName() + ":" + this.getPort());

	}

	/**
	 * Handles incoming connections
	 */
	@Override
	public void onOpen(WebSocket conn, ClientHandshake handshake) {
		Debugger.debugln("New Connection from: " + GameServer.getClientIP(conn));
		conns.put(conn, new Client(conn));
	}

	/**
	 * Prints the exception and removes the connection from the list
	 * 
	 * @param conn connection that error'd
	 * @param ex   Exception
	 */
	@Override
	public void onError(WebSocket conn, Exception ex) {
		Debugger.debugln("ERROR from " + GameServer.getClientIP(conn));
		ex.printStackTrace();

		if (conn != null) {
			conn.close(1011, "Internal Server Error");
			conns.remove(conn);
		}
	}

	/**
	 * Removes connection from map and client from its game if it is in one
	 */
	@Override
	public void onClose(WebSocket conn, int code, String reason, boolean remote) {
		Debugger.debugln("Closed connection to " + GameServer.getClientIP(conn) + ":" + code + (reason.isEmpty() ? "" : " : " + reason));

		// remove connection from game if it is in one
		Client client = conns.get(conn);
		String gameID = GameServerSQL.getGameOfClient(client);
		if (gameID != null) {
			GameServer.games.get(gameID).remove(client);
		}
		conns.remove(conn);
	}

	/**
	 * 
	 * Parses incoming messages and redirects them to their handlers
	 * 
	 * @param conn    Connection that send the message
	 * @param message Received message
	 */
	@Override
	public void onMessage(WebSocket conn, String message) {
		// get client and check authentication
		Client client = conns.get(conn);

		Message jsonMessage;
		String messageID;
		Debugger.debugln("Message from " + GameServer.getClientIP(conn) + ": " + message);
		try {
			// Try to parse message as JSON
			jsonMessage = new Message(message);
			messageID = jsonMessage.getString(Message.MESSAGE_ID);
		} catch (Exception e) {
			// tell client that the message could not be parsed
			GameServer.sendErrorMessage(client, "Message could not be parsed (Invalid Format)", null, GameServer.INVALID_FORMAT);
			return;
		}

		try {
			// update last access. If user is not authenticated, this will do nothing
			GameServerSQL.updateUserLastAccess(client);

			// handle public messages
			if (this.handlePublicActions(client, jsonMessage)) {
				// if command was handled, return
				return;
			}

			// handle commands for authenticated clients
			if (client.checkAuth(Client.AUTHENTICATED)) {
				// handle basic commands like getValue
				if (this.handleBaseActions(client, jsonMessage)) {
					// if command was handled, return
					return;
				}

				// handle authLevel specific commands
				if (client.checkAuth(Client.ROOT)) {
					// handle root messages
				}

				if (client.checkAuth(Client.MANAGER)) {
					// handle manager messages
				}

				if (client.checkAuth(Client.REGISTERED)) {
					// handle messages of registered users
				}

				// handle game messages
				String gameId = GameServerSQL.getGameOfClient(client);
				if (client.checkAuth(Client.PLAYER) && gameId != null) {
					GameServerSQL.updateGameLastAccess(gameId);
					jsonMessage.put(Message.GAME_ID, gameId);
					this.onMessage(client, jsonMessage);
				}
			} else {
				// if an unauthorized client send a message that is not a public function, error
				throw new GameServerException("New clients must request authenticaton or register", GameServer.ACTION_NOT_PERMITTED);
			}

		} catch (GameServerException e) {
			// send error messages
			GameServer.sendErrorMessage(client, e.getMessage(), messageID, e.getErrorCode());
			return;
		}
	}

	/**
	 * Handles all basic actions
	 * 
	 * @param client  Client that send the message
	 * @param message Message sent
	 * @return If an action has been processed
	 * @throws GameServerException Any Error during the handling of an action
	 */
	private boolean handlePublicActions(Client client, Message message) throws GameServerException {

		String type;
		String action;

		// get type and action
		try {
			type = message.getString(Message.TYPE);
			action = message.getString(Message.ACTION);
		} catch (Exception e) {
			// throw missing value exception if they can't be found
			throw new GameServerException("Could not parse message", GameServer.MSG_MISSING_VALUE);
		}

		if (type.equals(Message.AUTHENTICATE)) {
			// handle auth actions
			boolean result;
			switch (action) {
			case Message.REGISTER:
				result = this.handleRegisterAction(client, message);
				break;
			case Message.LOGIN:
				result = this.handleLoginAction(client, message);
				break;
			case Message.LOGIN_GUEST:
				result = this.handleLoginGuestAction(client, message);
				break;
			case Message.SESS_AUTH:
				result = this.handleSessAuthAction(client, message);
				break;
			default:
				return false;
			}

			// Build response message
			Message response = Message.buildResponseMessage(message);
			response.put(Message.SUCCESS, result);

			// if client was authenticated, add to active and create a session ID
			if (result) {
				String sessionID = GameServerSQL.addUserToActive(client);
				response.put(Message.GS_SESS_ID, sessionID);
				client.setSessionID(sessionID);
				int userID = GameServerSQL.getUserId(client);
				client.setUserID(userID);
			}
			// Send response
			client.send(response);
			return true;
		} else if (type.equals(Message.RESPONSE)) {
			// handle responses
			switch (action) {
			default:
				return false;
			}
		} else if (type.equals(Message.REQUEST)) {
			// handle requests that can be performed by anyone
			boolean result = false;
			;
			switch (action) {
			case Message.VALUE:
				result = this.handlePublicValueRequest(client, message);
				break;
			}
			return result;
		} else {
			throw new GameServerException("Invalid message type", GameServer.INVALID_TYPE);
		}
	}

	/**
	 * Handles all basic actions
	 * 
	 * @param client      Client that send the message
	 * @param message Message sent
	 * @return If an action has been processed
	 * @throws GameServerException Any Error during the handling of an action
	 */
	private boolean handleBaseActions(Client client, Message message) throws GameServerException {
		String type;
		String action;

		// get type and action
		try {
			type = message.getString(Message.TYPE);
			action = message.getString(Message.ACTION);
		} catch (Exception e) {
			// throw missing value exception if they can't be found
			throw new GameServerException("Could not parse message", GameServer.MSG_MISSING_VALUE);
		}

		if (type.equals(Message.REQUEST)) {
			// handle requests
			boolean result = false;
			switch (action) {
			case Message.SIGN_OUT:
				result = this.handleSignOutAction(client, message);
				break;
			case Message.VALUE:
				result = this.handleValueRequest(client, message);
				break;

			// TODO: make sure clients that already are in a room get rejected
			case Message.ENTER_GAME:
				result = this.handleEnterGameAction(client, message);
				break;
			case Message.CREATE_GAME:
				result = this.handleCreateGameAction(client, message);
				break;
			default:
				return false;
			}

			return result;
		} else if (type.equals(Message.RESPONSE)) {
			// handle responses
			switch (action) {
			default:
				return false;
			}
		} else {
			return false;
		}
	}

	/**
	 * Returns any publicly available value to the client
	 * 
	 * @param client  Client that requested a value
	 * @param message Message sent
	 * @return if request was successful
	 * @throws GameServerException Any Error during the handling of an action
	 */
	private boolean handlePublicValueRequest(Client client, Message message) throws GameServerException {
		String requestedValue;

		// get wanted value identifier
		try {
			requestedValue = message.getString(Message.VALUE);
		} catch (Exception e) {
			// throw missing value exception if it can't be found
			throw new GameServerException("Could not parse message", GameServer.MSG_MISSING_VALUE);
		}

		// pipes all publicly allowed value requests to the value request handler
		boolean result = false;
		switch (requestedValue) {
		case Message.AUTHENTICATED:
			result = this.handleValueRequest(client, message);
			break;
		default:
			throw new GameServerException("Invalid Value", GameServer.ACTION_NOT_PERMITTED);
		}
		return result;
	}

	/**
	 * Returns any available value to the client
	 * 
	 * @param client  Client that requested a value
	 * @param message Message sent
	 * @return if request was successful
	 * @throws GameServerException Any Error during the handling of an action
	 */
	private boolean handleValueRequest(Client client, Message message) throws GameServerException {
		// prebuild response
		Message response = Message.buildResponseMessage(message);
		String requestedValue;

		// get wanted value identifier
		try {
			requestedValue = message.getString(Message.VALUE);
		} catch (Exception e) {
			// throw missing value exception if it can't be found
			throw new GameServerException("Could not parse message", GameServer.MSG_MISSING_VALUE);
		}

		switch (requestedValue) {
		case Message.AUTHENTICATED:
			response.put(Message.VALUE, client.checkAuth(0));
			break;
		default:
			throw new GameServerException("Invalid Value", GameServer.INVALID_ACTION);
		}
		client.send(response);
		return true;
	}

	/**
	 * Signs out a logged in client
	 * 
	 * @param client  Client that sent the message
	 * @param message Message sent by client
	 * @return if request was successful
	 * @throws GameServerException Any Error during the handling of an action
	 */
	private boolean handleSignOutAction(Client client, Message message) throws GameServerException {
		WebSocket conn = client.getSocket();
		try {
			Message response = Message.buildResponseMessage(message);
			client.send(response);
			conn.close(1000, "User signed out");
			conns.remove(conn);
			GameServer.games.get(GameServerSQL.getGameOfClient(client)).remove(client);
			GameServerSQL.removePlayerFromGame(client);
			GameServerSQL.removeUserFromActive(client);
			return true;
		} catch (Exception e) {
			if (!conn.isClosed()) throw new GameServerException("Server can't sign out client", GameServer.CLOSE_FAILED);
			e.printStackTrace();
			Debugger.debugln("SIGN OUT FAILED");
		}
		return false;
	}

	/**
	 * Authenticates a client
	 * 
	 * @param client  Client that sent the message
	 * @param message Message sent by client
	 * @return if the client was authenticated
	 * @throws GameServerException Errors during the authentication
	 */
	private boolean handleLoginAction(Client client, Message message) throws GameServerException {
		if (client.checkAuth(Client.AUTHENTICATED)) {
			throw new GameServerException("Client is already authenticated", GameServer.ALREADY_AUTHENTICATED);
		}

		// get username and password
		String password;
		String username;

		try {
			username = message.getString(Message.USERNAME);
			password = message.getString(Message.PASSWORD);
		} catch (Exception e) {
			// throw missing value exception if they can't be found
			throw new GameServerException("Could not parse message", GameServer.MSG_MISSING_VALUE);
		}

		if (username.isBlank() || password.isBlank()) {
			throw new GameServerException("Username and password cannot be empty", GameServer.MSG_MISSING_VALUE);
		}

		// check user authentication
		boolean result = GameServerSQL.authenticateUser(username, password);
		if (result) {
			int authLevel = GameServerSQL.getAuthLevel(username);
			// auth level is saved bitshifted to the right
			client.setAuthLevel(authLevel << 1);
			// set authenticated bit;
			client.authenticate();
		} else {
			throw new GameServerException("Wrong Username or Password", GameServer.LOGIN_ERROR);
		}

		client.setUsername(username);
		return result;
	}

	/**
	 * Registered a client into the database
	 * 
	 * @param client  Client that sent the message
	 * @param message Message sent by client
	 * @return if the client was authenticated
	 * @throws GameServerException Errors during the authentication
	 */
	private boolean handleRegisterAction(Client client, Message message) throws GameServerException {
		if (client.checkAuth(Client.AUTHENTICATED)) {
			throw new GameServerException("Client is already authenticated", GameServer.ALREADY_AUTHENTICATED);
		}

		// get username and password
		String password;
		String username;
		try {
			username = message.getString(Message.USERNAME);
			password = message.getString(Message.PASSWORD);
		} catch (Exception e) {
			// throw missing value exception if they can't be found
			throw new GameServerException("Could not parse message", GameServer.MSG_MISSING_VALUE);
		}

		// check if username and pass are valid
		if (username.isBlank() || password.isBlank()) {
			throw new GameServerException("Username and password cannot be empty", GameServer.MSG_MISSING_VALUE);
		}

		// enter username, password an type(player) into database
		boolean result = GameServerSQL.registerUser(username, password);

		return result;
	}

	/**
	 * Authenticates a Session by its ID
	 * 
	 * @param client  Client that sent the message
	 * @param message Message sent by client
	 * @return if the client was authenticated
	 * @throws GameServerException Errors during the authentication
	 */
	private boolean handleSessAuthAction(Client client, Message message) throws GameServerException {
		if (client.checkAuth(Client.AUTHENTICATED)) {
			throw new GameServerException("Client is already authenticated", GameServer.ALREADY_AUTHENTICATED);
		}

		String sessionID = message.getString(Message.GS_SESS_ID);
		boolean result = GameServerSQL.authSession(sessionID);

		// get username from Session
		GameServerSQL.changeDatabase(GameServerSQL.gameDatabase);
		String query = "SELECT username FROM users WHERE session_id=?";
		ResultSet sqlResult;
		if (result) {
			try {
				// TODO: check if user is registered
				sqlResult = GameServerSQL.preparedQuery(query, sessionID);
				sqlResult.next();
				// set Username
				client.setUsername(sqlResult.getString("username"));
				client.setSessionID(sessionID);

				int userID = GameServerSQL.getUserId(client);
				client.setUserID(userID);

				int host = GameServerSQL.isUserHost(client) ? Client.HOST : 0;
				client.setAuthLevel(Client.PLAYER | host);
				client.authenticate();

				// add client to game/create game of client that doesn't exists (example: server
				// reboot)
				String gameID = GameServerSQL.getGameOfClient(client);
				if (gameID != null) {
					HashSet<Client> game = GameServer.games.get(gameID);
					if (game == null) {
						GameServer.games.put(gameID, new HashSet<Client>());
					}
					GameServer.games.get(gameID).add(client);
				}

			} catch (SQLException e) {
				e.printStackTrace();
				throw new GameServerException("SQL Query failed", GameServer.SQL_ERROR);
			}
		}
		return result;
	}

	/**
	 * Authenticates a client as guest
	 * 
	 * @param client  Client that sent the message
	 * @param message Message sent by client
	 * @return if the client was authenticated
	 * @throws GameServerException Errors during the authentication
	 */
	private boolean handleLoginGuestAction(Client client, Message message) throws GameServerException {
		if (client.checkAuth(Client.AUTHENTICATED)) {
			throw new GameServerException("Client is already authenticated", GameServer.ALREADY_AUTHENTICATED);
		}
		// get username
		String username;
		try {
			username = message.getString(Message.USERNAME);
		} catch (Exception e) {
			// throw missing value exception if they can't be found
			throw new GameServerException("Could not parse message", GameServer.MSG_MISSING_VALUE);
		}

		// check username validity
		if (username.isBlank()) {
			throw new GameServerException("Username cannot be empty", GameServer.MSG_MISSING_VALUE);
		}

		// check user authentication
		boolean result = GameServerSQL.authenticateGuestUser(username);
		if (result) {
			// set authenticated;
			client.setAuthLevel(Client.PLAYER | Client.AUTHENTICATED);
		} else {
			throw new GameServerException("Wrong Username or Password", GameServer.LOGIN_ERROR);
		}
		// set username
		client.setUsername(username);
		return result;
	}

	/**
	 * Creates a game and sets the creating client as Host
	 * 
	 * @param client  Client that sent the message
	 * @param message Message sent by client
	 * @return if game was created
	 * @throws GameServerException Any Error during the creating of the game
	 */
	private boolean handleCreateGameAction(Client client, Message message) throws GameServerException {
		// create a new game
		String gameId = GameServerSQL.createGame(client);
		GameServer.games.put(gameId, new HashSet<Client>());
		// add client as player
		GameServer.games.get(gameId).add(client);
		GameServerSQL.addPlayerToGame(client, gameId);
		// set client as host
		client.setAuthLevel(client.getAuthLevel() | Client.HOST);

		// send response to client
		Message response = Message.buildResponseMessage(message);
		response.put("game_id", gameId);
		client.send(response);

		// trigger onGameOpen event
		this.onGameOpen(client, gameId);
		return true;
	}

	/**
	 * Lets a client enter a game
	 * 
	 * @param client  Client that sent the message
	 * @param message Message sent by client
	 * @return if the client entered the game
	 * @throws GameServerException Any Error during the entering of the game
	 */
	private boolean handleEnterGameAction(Client client, Message message) throws GameServerException {

		String gameId;
		try {
			gameId = message.getString(Message.GAME_ID);
		} catch (Exception e) {
			// throw missing value exception if they can't be found
			throw new GameServerException("Could not parse message", GameServer.MSG_MISSING_VALUE);
		}

		GameServer.games.get(gameId).add(client);
		GameServerSQL.addPlayerToGame(client, gameId);

		// send response to client
		Message response = Message.buildResponseMessage(message);
		response.put("game_id", gameId);
		client.send(response);
		return true;
	}

	/**
	 * Sends an Error to a client
	 * 
	 * @param client       Client to send the message to
	 * @param errorMessage Message of the error
	 * @param messageID    Message that caused the error/Own Message ID
	 * @param code         error code
	 */
	public static void sendErrorMessage(Client client, String errorMessage, String messageID, int code) {
		Message error = Message.buildErrorMessage(errorMessage, messageID, code);
		client.send(error);
	}

	/**
	 * Gets all clients in a game
	 * 
	 * @param gameID ID of the game
	 * @return Array of all clients in the game
	 */
	public static Client[] getClientsInGame(String gameID) {
		return (Client[]) GameServer.games.get(gameID).toArray(new Client[0]);
	}

	/**
	 * Gets the host of a game
	 * 
	 * @param gameID ID of the game
	 * @return Host client of the game
	 */
	public static Client getHostOfGame(String gameID) {
		for (Client c : GameServer.getClientsInGame(gameID)) {
			if (c.isHost()) return c;
		}
		return null;
	}

	/**
	 * Terminates the process if server failed to start
	 */
	@Override
	public void run() {
		try {
			super.run();
		} catch (Exception e) {
			e.printStackTrace();
			Debugger.debugln("Failed to start server, shutting down...");
			System.exit(1);
		}
	}

	/**
	 * Adds server name to handshake header
	 */
	@Override
	public ServerHandshakeBuilder onWebsocketHandshakeReceivedAsServer(WebSocket conn, Draft draft, ClientHandshake request) throws InvalidDataException {
		ServerHandshakeBuilder builder = super.onWebsocketHandshakeReceivedAsServer(conn, draft, request);
		builder.put("name", this.NAME); // add server name to handshake
		return builder;
	}

	/**
	 * Disconnects all connected clients
	 * 
	 * @param message Message to be send to the clients
	 * @param code    Closing code
	 */
	public void disconnectAll(int code, String message) {
		for (Entry<WebSocket, Client> e : conns.entrySet()) {
			e.getKey().close(code, message);
			conns.remove(e.getKey());
		}
	}

	/**
	 * Creates the Garbage Collection Thread
	 * 
	 * @return Garbage Collection Thread
	 */
	private Thread createGarbageCollectionThread() {
//		final GameServer self = this;
		return new Thread() {
			@Override
			public void run() {
				long interval = Integer.parseInt(GameServer.properties.getProperty("server.garbage_collection.interval")) * 60 * 1000;
//				interval = 5 * 1000;
				while (true) {
					// stop on interrupt
					if (this.isInterrupted()) break;

					// collect garbage
					try {
						Debugger.debugln("RUN GARBAGE COLLECTION");
						GameServerSQL.collectGarbage();
					} catch (Exception e) {
						e.printStackTrace();
					}

					// sleep
					try {
						Thread.sleep(interval);
					} catch (InterruptedException e) {
						break;
					}
				}
			}
		};
	}

	/**
	 * Gets the IP address of a given connection
	 * 
	 * @param conn Connection whose IP is wanted
	 * @return IP address
	 */
	public static String getClientIP(WebSocket conn) {
		return conn.getRemoteSocketAddress().getAddress().getHostAddress();
	}

	/**
	 * Reads the port property of the configuration file
	 * 
	 * @return port property
	 */
	private static int getPortProperty() {
		try {
			int port;

			if (GameServer.properties != null) {
				port = Integer.parseInt(GameServer.properties.getProperty("server.port", "-1"));
			} else {
				port = Integer.parseInt(GameServer.loadProperties().getProperty("server.port", "-1"));
			}

			return port;
		} catch (Exception e) {
			return -1;
		}
	}

	/**
	 * Loads server properties from the configuration file
	 * @return Server Properties
	 */
	private static Properties loadProperties() {
		InputStream is = null;
		Properties properties = new Properties();
		try {
			is = new FileInputStream(GameServer.CONFIG_LOCATION);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		try {
			properties.load(is);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return properties;
	}

	/**
	 * Gets called when a {@code Client} sends a message to the GameServer
	 * 
	 * @param client  Client that send the message
	 * @param message Message
	 */
	protected abstract void onMessage(Client client, Message message);

	/**
	 * Gets called when a {@code Client} creates a new game
	 * 
	 * @param client  Client that opened the game
	 * @param gameID ID of the opened game
	 */
	protected abstract void onGameOpen(Client client, String gameID);

	/**
	 * Server SQL Connection subclass
	 * 
	 * @author Nils Ramstoeck
	 *
	 */
	protected static class GameServerSQL {
		/**
		 * Connection to the SQL database
		 */
		private static Connection conn = null;

		/**
		 * Database that stores login information
		 */
		private static String loginDatabase;

		/**
		 * Database that stores game data
		 */
		private static String gameDatabase;

		/**
		 * SQL Update Queries
		 */
		private static final String SQL_UPDATE_CMD = "insert update delete";

		/**
		 * Connects to a database
		 * 
		 * @param username       db username
		 * @param password       db password
		 * @param url            db url
		 * @param _gameDatabase  database that contains game data
		 * @param _loginDatabase database that contains login info
		 * @return if connection was successfull
		 */
		private static boolean connect(String username, String password, String url, String _gameDatabase, String _loginDatabase) {
			loginDatabase = _loginDatabase;
			gameDatabase = _gameDatabase;
			String fullUrl = "jdbc:MySQL://" + url;
			try {
				conn = DriverManager.getConnection(fullUrl, username, password);
				conn.setCatalog(gameDatabase);
			} catch (SQLException e) {
				e.printStackTrace();
				return false;
			}
			return true;
		}

		/**
		 * Changes the database. Returns true if change was successful or if the new
		 * database equals the old one
		 * 
		 * @param database new database
		 * @return if change was successful
		 */
		private static boolean changeDatabase(String database) {
			try {
				if (!conn.getCatalog().equals(database)) {
					conn.setCatalog(database);
				}
			} catch (Exception e) {
				return false;
			}
			return true;
		}

		/**
		 * Executes an SQL query and returns the result
		 * 
		 * @deprecated use {@link #preparedQuery(String, Object...)}
		 * @param query query to be executed
		 * @return result of the query
		 * @throws SQLException No SQL Connection
		 */
		@Deprecated
		public static ResultSet query(String query) throws SQLException {
			if (!isConnected()) throw new SQLException("No SQL Connection", "08003");
			Statement stmt = conn.createStatement();
			if (!stmt.execute(query)) {
				Debugger.debugln(stmt.getWarnings().getMessage());
			}
			return stmt.getResultSet();
		}

		/**
		 * Uses a prepared statement to execute a query
		 * 
		 * @param query  Query to be executed with '?' place holders
		 * @param params Values to replace the place holders
		 * @return used statement
		 * @throws SQLException SQLException
		 */
		private static PreparedStatement prepareAndExecuteStatement(String query, Object... params) throws SQLException {
			if (!isConnected()) return null;
			PreparedStatement stmt = null;
			// build prepared statement
			stmt = conn.prepareStatement(query);
			int i = 0;
			// fill in placeholders
			for (Object p : params) {
				stmt.setString(++i, p.toString());
			}

			Debugger.debugln("GameServerSQL: " + stmt.toString().split(": ", 2)[1]);

			// execute query
			stmt.execute();

			return stmt;
		}

		/**
		 * Executes a query and returns a {@code ResultSet} using
		 * {@link #prepareAndExecuteStatement(String, Object...)}
		 * 
		 * @param query  Query to be executed with '?' place holders
		 * @param params Values to replace the place holders
		 * @return SQL ResultSet
		 * @throws SQLException SQLException
		 */
		public static ResultSet preparedQuery(String query, Object... params) throws SQLException {
			return GameServerSQL.prepareAndExecuteStatement(query, params).getResultSet();
		}

		/**
		 * Executes an update query and returns the number of affected rows using
		 * {@link #prepareAndExecuteStatement(String, Object...)}
		 * 
		 * @param query  Query to be executed with '?' place holders
		 * @param params Values to replace the place holders
		 * @return SQL ResultSet
		 * @throws SQLException SQLException
		 */
		public static int preparedUpdateQuery(String query, Object... params) throws SQLException {
			String sqlCmd = query.toLowerCase().split(" ")[0];
			if (GameServerSQL.SQL_UPDATE_CMD.contains(sqlCmd)) {
				return GameServerSQL.prepareAndExecuteStatement(query, params).getUpdateCount();
			} else {
				throw new SQLException("Not an Update Command: " + sqlCmd);
			}
		}

		/**
		 * Check if database connection is open
		 * 
		 * @return is connected
		 */
		private static boolean isConnected() {
			try {
				return !conn.isClosed();
			} catch (SQLException e) {
				e.printStackTrace();
				return false;
			}
		}

		/**
		 * Registers a new user into the login database. Password is hashed before entry
		 * 
		 * @param username Username of the client
		 * @param password Password in clear text
		 * @return if entry was successful
		 * @throws GameServerException User can not be registered
		 */
		private static boolean registerUser(String username, String password) throws GameServerException {
			if (!isConnected()) return false;
			// hash password
			String hashedPassword = PasswordManager.hashPassword(password);
			String query = "INSERT INTO registered_users (username, password, auth_level) values (?, ?, ?)";
			GameServerSQL.changeDatabase(loginDatabase);
			try {
				GameServerSQL.preparedUpdateQuery(query, username, hashedPassword, Client.REGISTERED | Client.PLAYER >> 1);
			} catch (SQLException e) {
				// if username already exists
				if (e.getErrorCode() == MysqlErrorNumbers.ER_DUP_ENTRY) {
					throw new GameServerException("username is already in use", GameServerErrorCode.DUP_USERNAME);
				}
				e.printStackTrace();
				return false;
			}
			return true; // this will probably break at some point
		}

		/**
		 * Authenticates a user with the database
		 * 
		 * @param username User to be authenticated
		 * @param password Users password
		 * @return if user was authenticated
		 */
		private static boolean authenticateUser(String username, String password) {
			if (!GameServerSQL.isConnected()) return false;
			GameServerSQL.changeDatabase(loginDatabase);
			String query = "SELECT password FROM registered_users WHERE username=?";
			try {
				ResultSet result = GameServerSQL.preparedQuery(query, username);
				result.next();
				String goodHash = result.getString("password");
				return PasswordManager.validatePassword(password, goodHash);
			} catch (SQLException e) {
				e.printStackTrace();
				return false;
			}
		}

		/**
		 * Authenticates a guest user. Guest users need a username that is not used by a
		 * registered user.
		 * 
		 * @param username User to be authenticated
		 * @return if user was authenticated
		 */

		private static boolean authenticateGuestUser(String username) {
			if (!GameServerSQL.isConnected()) return false;
			GameServerSQL.changeDatabase(loginDatabase);

			// check if username is already registered. guests can have duplicate usernames
			String query = "SELECT COUNT(*) AS COUNT FROM registered_users WHERE username=?";
			try {
				ResultSet result = GameServerSQL.preparedQuery(query, username);
				result.next();
				int count = result.getInt("COUNT");
				return count == 0;
			} catch (SQLException e) {
				e.printStackTrace();
				return false;
			}
		}

		/**
		 * Authenticates a Session by its SessionID
		 * 
		 * @param sessionID SessionID to authenticate
		 * @return if user was authenticated
		 */
		private static boolean authSession(String sessionID) {
			if (!GameServerSQL.isConnected()) return false;
			GameServerSQL.changeDatabase(gameDatabase);
			String query = "SELECT COUNT(*) AS COUNT FROM users WHERE session_id=?";
			try {
				ResultSet result = GameServerSQL.preparedQuery(query, sessionID);
				result.next();
				return result.getInt("COUNT") == 1;
			} catch (SQLException e) {
				e.printStackTrace();
				return false;
			}
		}

		/**
		 * Returns a new, unique Game ID
		 * 
		 * @return Unique Game ID
		 */
		private static String getUniqueGameId() {
			if (!GameServerSQL.isConnected()) return null;
			String gameID = "";
			int maxTrys = 100;
			int curTry = 0;
			boolean uniqueGameIDFound = false;
			do {
				// Create a new GameID
				// Creates a String [a-zA-Z0-9]
				String alphabet = "abcdefghijklmnopqrstuvwxyz";
				alphabet += alphabet.toUpperCase();
				alphabet += "1234567890";
				int length = Integer.parseInt(GameServer.properties.getProperty("server.game.id_length"));
				for (int i = 0; i < length; i++) {
					int randomIndex = (int) (Math.floor(Math.random() * 10000) % alphabet.length());
					gameID += alphabet.charAt(randomIndex);
				}
				// check if ID is unique
				GameServerSQL.changeDatabase(GameServerSQL.gameDatabase);
				String query = "SELECT COUNT(*) AS COUNT FROM games WHERE game_id=?";
				try {
					ResultSet result = GameServerSQL.preparedQuery(query, gameID);
					result.next();
					int count = result.getInt("COUNT");
					uniqueGameIDFound = count == 0;
				} catch (SQLException e) {
					e.printStackTrace();
					return null;
				}
				curTry++;
			} while (!uniqueGameIDFound && curTry <= maxTrys);
			// If failed to generate a unique ID, return null
			if (curTry >= 100) {
				return null;
			}
			return gameID;
		}

		/**
		 * Creates a new Game
		 * 
		 * @param host Host of the game
		 * @return GameID of the created game
		 * @throws GameServerException Game could not be created
		 */
		public static String createGame(Client host) throws GameServerException {
			if (!GameServerSQL.isConnected()) throw new GameServerException("No database connection", GameServer.SQL_ERROR);
			GameServerSQL.changeDatabase(loginDatabase);
			String gameId = GameServerSQL.getUniqueGameId();
			String query = "INSERT INTO games SET game_id=?, host_id=?";
			try {
				GameServerSQL.preparedQuery(query, gameId, host.getUserID());
			} catch (SQLException e) {
				e.printStackTrace();
				throw new GameServerException(e.getMessage(), GameServer.SQL_ERROR);
			}
			return gameId;
		}

		/**
		 * Adds a player to an existing game
		 * 
		 * @param client Client to be added to the game
		 * @param gameId GameID of the game
		 * @throws GameServerException Player could not be added
		 */
		public static void addPlayerToGame(Client client, String gameId) throws GameServerException {
			if (!GameServerSQL.isConnected()) throw new GameServerException("No database connection", GameServer.SQL_ERROR);
			GameServerSQL.changeDatabase(loginDatabase);
			int userId = GameServerSQL.getUserId(client);
			String query = "INSERT INTO players SET game_id=?, user_id=?";
			try {
				GameServerSQL.preparedQuery(query, gameId, userId);
			} catch (SQLException e) {
				e.printStackTrace();
				if (e.getErrorCode() == 1452) {
					throw new GameServerException("Game does not Exist", GameServer.GAME_NOT_FOUND);
				} else {
					throw new GameServerException(e.getMessage(), GameServer.SQL_ERROR);
				}
			}
		}

		/**
		 * Removes a player from a game
		 * 
		 * @param client Client to be removed from the game
		 * @throws GameServerException Player could not be removed
		 */
		public static void removePlayerFromGame(Client client) throws GameServerException {
			if (!GameServerSQL.isConnected()) throw new GameServerException("No database connection", GameServer.SQL_ERROR);
			GameServerSQL.changeDatabase(gameDatabase);
			int userId = GameServerSQL.getUserId(client);
			String query = "DELETE FROM players WHERE user_id=?";
			try {
				GameServerSQL.preparedQuery(query, userId);
			} catch (SQLException e) {
				e.printStackTrace();
				throw new GameServerException(e.getMessage(), GameServer.SQL_ERROR);
			}
		}

		/**
		 * Returns a new, unique Session ID
		 * 
		 * @return Unique Session ID
		 */
		private static String getUniqueSessionId() {
			if (!GameServerSQL.isConnected()) return null;
			String sessionID = "";
			int maxTrys = 100;
			int curTry = 0;
			boolean uniqueGameIDFound = false;
			do {
				// Create a new SessionID
				// Creates a String [a-zA-Z0-9]
				String alphabet = "abcdefghijklmnopqrstuvwxyz";
				alphabet += alphabet.toUpperCase();
				alphabet += "1234567890";
				int length = Integer.parseInt(GameServer.properties.getProperty("server.session.id_length"));
				for (int i = 0; i < length; i++) {
					int randomIndex = (int) (Math.floor(Math.random() * 10000) % alphabet.length());
					sessionID += alphabet.charAt(randomIndex);
				}
				// check if ID is unique
				GameServerSQL.changeDatabase(GameServerSQL.gameDatabase);
				String query = "SELECT COUNT(*) AS COUNT FROM users WHERE session_id=?";
				try {
					ResultSet result = GameServerSQL.preparedQuery(query, sessionID);
					result.next();
					int count = result.getInt("COUNT");
					uniqueGameIDFound = count == 0;
				} catch (SQLException e) {
					e.printStackTrace();
					return null;
				}
				curTry++;
			} while (!uniqueGameIDFound && curTry <= maxTrys);
			// If failed to generate a unique ID, return null
			if (curTry >= 100) {
				return null;
			}
			return sessionID;

		}

		/**
		 * Gets Authentication level of a client
		 * 
		 * @param username Username of the client
		 * @return Authentication level of the client
		 * @throws GameServerException AuthLevel could not be loaded
		 */
		public static int getAuthLevel(String username) throws GameServerException {
			if (!GameServerSQL.isConnected()) throw new GameServerException("No database connection", GameServer.SQL_ERROR);
			GameServerSQL.changeDatabase(loginDatabase);
			String query = "SELECT auth_level FROM registered_users WHERE username=?";
			try {
				ResultSet result = GameServerSQL.preparedQuery(query, username);
				result.next();
				int authLevel = result.getInt("auth_level");
				return authLevel;
			} catch (SQLException e) {
				e.printStackTrace();
				throw new GameServerException(e.getMessage(), GameServer.SQL_ERROR);
			}
		}

		/**
		 * Gets the game a player is in
		 * 
		 * @param client Client to get the game of
		 * @return GameID of the clients game
		 */
		private static String getGameOfClient(Client client) {
			if (!GameServerSQL.isConnected() || client == null) return null;
			GameServerSQL.changeDatabase(gameDatabase);
			String query = "SELECT game_id FROM players INNER JOIN users ON users.user_id=players.user_id WHERE session_id=?";
			try {
				ResultSet result = GameServerSQL.preparedQuery(query, client.getSessionID());
				if (result.next()) {
					String gameId = result.getString("game_id");
					return gameId;
				} else {
					return null;
				}
			} catch (SQLException e) {
				e.printStackTrace();
				return null;
			}
		}

		/**
		 * Gets the UserID of a client
		 * 
		 * @param client Client to get the UserID from
		 * @return UserID of the client
		 * @throws GameServerException UserID could not be loaded
		 */
		private static int getUserId(Client client) throws GameServerException {
			if (!GameServerSQL.isConnected()) throw new GameServerException("No database connection", GameServer.SQL_ERROR);
			GameServerSQL.changeDatabase(gameDatabase);
			String query = "SELECT user_id FROM users WHERE session_id=?";
			try {
				ResultSet result = GameServerSQL.preparedQuery(query, client.getSessionID());
				result.next();
				int userId = result.getInt("user_id");
				return userId;
			} catch (SQLException e) {
				e.printStackTrace();
				throw new GameServerException(e.getMessage(), GameServer.SQL_ERROR);
			}
		}

		/**
		 * Adds a user to a list of active sessions
		 * 
		 * @param client Client to be added
		 * @return SessionID
		 * @throws GameServerException User could not be added to Active
		 */
		private static String addUserToActive(Client client) throws GameServerException {
			if (!isConnected()) throw new GameServerException("No Database Connection", GameServer.SQL_ERROR);

			String sessionID;

			// if client already has a SessionID, use it. Otherwise create new, unique
			// SessionID
			if (client.getSessionID() == null) {
				sessionID = GameServerSQL.getUniqueSessionId();
			} else {
				sessionID = client.getSessionID();
			}

			// change database to GameData database
			GameServerSQL.changeDatabase(gameDatabase);

			// Insert user into database, if SessionID already exists, update it
			String query = "INSERT INTO users SET username=?, session_id=? ON DUPLICATE KEY UPDATE username=?, session_id=session_id, last_access=NOW()";
			try {
				GameServerSQL.preparedUpdateQuery(query, client.getUsername(), sessionID, client.getUsername());
			} catch (SQLException e) {
				throw new GameServerException(e.getMessage(), GameServer.SQL_ERROR);
			}
			return sessionID;
		}

		/**
		 * Updates the last_access field of a user
		 * 
		 * @param client Client that sent a message
		 * @throws GameServerException User could not be updated
		 */
		private static void updateUserLastAccess(Client client) throws GameServerException {
			if (!isConnected()) throw new GameServerException("No Database Connection", GameServer.SQL_ERROR);
			if (!client.checkAuth(Client.AUTHENTICATED)) return;

			String query = "UPDATE users SET last_access=NOW() WHERE session_id=?";
			GameServerSQL.changeDatabase(gameDatabase);
			try {
				GameServerSQL.preparedUpdateQuery(query, client.getSessionID());
			} catch (SQLException e) {
				e.printStackTrace();
				throw new GameServerException(e.getMessage(), GameServer.SQL_ERROR);
			}
		}

		/**
		 * Checks if a client is the host of a game
		 * 
		 * @param client Client to be checked
		 * @return If client is a Host
		 * @throws GameServerException Could not check if user is Host
		 */
		private static boolean isUserHost(Client client) throws GameServerException {
			if (!isConnected()) throw new GameServerException("No Database Connection", GameServer.SQL_ERROR);

			String query = "SELECT COUNT(*) AS COUNT FROM games WHERE host_id=?";
			GameServerSQL.changeDatabase(gameDatabase);
			try {
				ResultSet result = GameServerSQL.preparedQuery(query, client.getUserID());
				result.next();
				return result.getInt("COUNT") == 1;
			} catch (SQLException e) {
				e.printStackTrace();
				throw new GameServerException(e.getMessage(), GameServer.SQL_ERROR);
			}
		}

		/**
		 * Updates the last_access field of a game
		 * 
		 * @param gameId Game to update
		 * @throws GameServerException Game could not be updated
		 */
		private static void updateGameLastAccess(String gameId) throws GameServerException {
			if (!isConnected()) throw new GameServerException("No Database Connection", GameServer.SQL_ERROR);

			String query = "UPDATE games SET last_access=NOW() WHERE game_id=?";
			GameServerSQL.changeDatabase(gameDatabase);
			try {
				GameServerSQL.preparedUpdateQuery(query, gameId);
			} catch (SQLException e) {
				e.printStackTrace();
				throw new GameServerException(e.getMessage(), GameServer.SQL_ERROR);
			}
		}

		/**
		 * Removes a user from the list of active sessions
		 * 
		 * @param client Client to be removed
		 * @throws GameServerException User could not be removed from Active
		 */
		private static void removeUserFromActive(Client client) throws GameServerException {
			if (!isConnected()) throw new GameServerException("No Database Connection", GameServer.SQL_ERROR);
			String query = "DELETE FROM users WHERE session_id=?";
			GameServerSQL.changeDatabase(gameDatabase);
			try {
				GameServerSQL.preparedUpdateQuery(query, client.getSessionID());
			} catch (SQLException e) {
				e.printStackTrace();
				throw new GameServerException(e.getMessage(), GameServer.SQL_ERROR);
			}
		}

		/**
		 * Collects all Garbage in the SQL Database
		 * 
		 * @throws GameServerException Garbage could not be collected
		 */
		private static void collectGarbage() throws GameServerException {
			if (!isConnected()) throw new GameServerException("No Database Connection", GameServer.SQL_ERROR);
			GameServerSQL.changeDatabase(gameDatabase);

			// get all players who's session is timed out and close their connection
			try {
				// SQL query to get all timed out clients
				String query = "SELECT session_id FROM users WHERE last_access<=DATE_SUB(NOW(), INTERVAL ? MINUTE)";
				String interval = GameServer.properties.getProperty("server.session.timeout");
				ResultSet result = GameServerSQL.preparedQuery(query, interval);

				// get session ID set
				HashSet<String> sidSet = new HashSet<String>();
				while (result.next()) {
					sidSet.add(result.getString("session_id"));
				}

				// get all clients
				HashSet<Client> clientSet = new HashSet<Client>();
				// for all connections
				for (Entry<WebSocket, Client> entry : GameServer.conns.entrySet()) {
					Client client = entry.getValue();
					String sessionId = client.getSessionID();
					// if session ID set contains client sessionId
					// add client to set and remove id from list
					if (sidSet.contains(sessionId)) {
						clientSet.add(client);
						sidSet.remove(sessionId);
					}
					// if list is empty, stop
					if (sidSet.isEmpty()) {
						break;
					}
				}

				for (Client client : clientSet) {
					client.disconnect("Session Timeout", 1000);
				}

			} catch (Exception e) {
				e.printStackTrace();
			}

			// clean players table
			try {
				String query = "DELETE FROM players WHERE players.user_id IN (SELECT users.user_id FROM users WHERE users.last_access<=DATE_SUB(NOW(), INTERVAL ? MINUTE))";
				String interval = GameServer.properties.getProperty("server.session.timeout");
				GameServerSQL.preparedUpdateQuery(query, interval);
			} catch (SQLException e) {
				e.printStackTrace();
				throw new GameServerException(e.getMessage(), GameServer.SQL_ERROR);
			}

			// clean users table
			try {
				String query = "DELETE FROM users WHERE last_access<=DATE_SUB(NOW(), INTERVAL ? MINUTE)";
				String interval = GameServer.properties.getProperty("server.session.timeout");
				GameServerSQL.preparedUpdateQuery(query, interval);
			} catch (SQLException e) {
				e.printStackTrace();
				throw new GameServerException(e.getMessage(), GameServer.SQL_ERROR);
			}

			// clean up games table
			try {
				// get all players who's session is timed out and close their connection
				// SQL query to get all timed out clients
				String query = "SELECT game_id FROM games WHERE last_access<=DATE_SUB(NOW(), INTERVAL ? MINUTE)";
				String interval = GameServer.properties.getProperty("server.game.timeout");
				ResultSet result = GameServerSQL.preparedQuery(query, interval);

				// get all timed out games and delete them
				while (result.next()) {
					String gameID = result.getString("game_id");
					GameServer.games.remove(gameID);
				}

			} catch (SQLException e) {
				e.printStackTrace();
				throw new GameServerException(e.getMessage(), GameServer.SQL_ERROR);
			}

			try {
				// delete timed out games
				String query = "DELETE FROM games WHERE last_access<=DATE_SUB(NOW(), INTERVAL ? MINUTE)";
				String interval = GameServer.properties.getProperty("server.game.timeout");
				GameServerSQL.preparedUpdateQuery(query, interval);
			} catch (SQLException e) {
				e.printStackTrace();
				throw new GameServerException(e.getMessage(), GameServer.SQL_ERROR);
			}
		}
	}
}
