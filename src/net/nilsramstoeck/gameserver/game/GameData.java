package net.nilsramstoeck.gameserver.game;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import net.nilsramstoeck.gameserver.GameServer;
import net.nilsramstoeck.gameserver.client.Client;

/**
 * Interface between the GameServer and in the SQL database stored Game and
 * Player data
 * 
 * @author Nils Ramstoeck
 *
 */
public class GameData {

	/**
	 * Connection to the database
	 */
	static private Connection conn = null;

	/**
	 * Data identity
	 */
	private String dataID;

	/**
	 * Data identity field
	 */
	private String IDField;

	/**
	 * Data table
	 */
	private String table;

	/**
	 * Connects to database
	 * 
	 * @param _gameId ID off the game to get the data from
	 */
	public GameData(String _gameId) {
		this.table = "game_data";
		this.dataID = _gameId;
		this.IDField = "game_id";
		GameData.connect();
	}

	/**
	 * Connects to database
	 * 
	 * @param client Client to get the player data from
	 */
	public GameData(Client client) {
		this.table = "player_data";
		this.dataID = Integer.toString(client.getUserID());
		this.IDField = "user_id";
		GameData.connect();
	}

	/**
	 * Connects to the GameData database if not already connected
	 */
	static private void connect() {
		try {
			if (conn != null && !conn.isClosed()) return;
		} catch (SQLException e1) {
			e1.printStackTrace();
		}
		// read properties from the config file
		String dbUser = GameServer.properties.getProperty("sql.user");
		String dbPass = GameServer.properties.getProperty("sql.pass", "");
		String dbUrl = GameServer.properties.getProperty("sql.url");
		String dbPort = GameServer.properties.getProperty("sql.port");
		String gamedb = GameServer.properties.getProperty("sql.database");

		// connect to database
		String fullUrl = "jdbc:MySQL://" + dbUrl + ":" + dbPort;
		try {
			conn = DriverManager.getConnection(fullUrl, dbUser, dbPass);
			conn.setCatalog(gamedb);
		} catch (SQLException e) {
			e.printStackTrace();
		}

	}

	/**
	 * Saves a String value into the database
	 * 
	 * @param key Name of the value
	 * @param value Value to save
	 * @return if the value could be saved
	 */
	public boolean saveString(String key, String value) {
		return this.saveValue(key, value);
	}

	/**
	 * Saves an Integer value to the database
	 * @param key Name of the value
	 * @param value Value to save
	 * @return if the value could be saved
	 */
	public boolean saveInt(String key, int value) {
		return this.saveValue(key, Integer.toString(value));
	}

	/**
	 * Overall method to enter a value into the database
	 * @param key Name of the Value
	 * @param value value to save
	 * @return if the value could be saved
	 */
	protected boolean saveValue(String key, String value) {
		if (!isConnected()) return false;
		String query = "INSERT INTO " + this.table + " SET " + this.IDField + "=?, name=?, value=? ON DUPLICATE KEY UPDATE value=?";
		try {

			PreparedStatement stmt = null;
			// build prepared statement
			stmt = conn.prepareStatement(query);

			// set parameters
			stmt.setString(1, this.dataID);
			stmt.setString(2, key);
			stmt.setString(3, value);
			stmt.setString(4, value);

//			System.out.println(stmt.toString());

			// execute query
			stmt.execute();
			return true;
		} catch (SQLException e) {
			e.printStackTrace();
			return false;
		}
	}


	/**
	 * Overall method to retrieve a value from the database
	 * @param key Name of the value
	 * @return Value of the key
	 */
	protected String getValue(String key) {
		if (!isConnected()) return null;
		String query = "SELECT value FROM " + this.table + " WHERE " + this.IDField + "=? AND name=?";
		try {

			PreparedStatement stmt = null;
			// build prepared statement
			stmt = conn.prepareStatement(query);

			// set parameters
			stmt.setString(1, this.dataID);
			stmt.setString(2, key);

//			System.out.println(stmt.toString());

			// execute query and get result
			try {
				stmt.execute();
				ResultSet result = stmt.getResultSet();
				result.next();

				// return value
				return result.getString("value");

			} catch (Exception e) {
				return null;
			}

		} catch (SQLException e) {
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * Returns the String value of a saved value
	 * @param key Name of the value
	 * @return Value of the key
	 */
	public String getString(String key) {
		return this.getValue(key);
	}

	/**
	 * Returns an Integer value from the database
	 * @param key Name of the value
	 * @return Integer value of the key
	 */
	public int getInt(String key) {
		return Integer.parseInt(this.getValue(key));
	}

	/**
	 * Check if connection is open
	 * @return if connection is open
	 */
	public boolean isConnected() {
		try {
			return !conn.isClosed();
		} catch (SQLException e) {
			e.printStackTrace();
			return false;
		}
	}

}
