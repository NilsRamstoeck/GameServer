package net.nilsramstoeck.gameserver;

/**
 * All the GameServer Errors
 * @author Nils Ramstoeck
 *
 */
public interface GameServerErrorCode{
	/**
	 * Duplicate username on register operation
	 */
	public static final int DUP_USERNAME = 0x11;
	
	/**
	 * Action is unknown to server
	 */
	public static final int INVALID_ACTION = 0x12;
	
	/**
	 * Type is unknown to server
	 */
	public static final int INVALID_TYPE = 0x1B;
		
	/**
	 * Action not permitted
	 */
	public static final int ACTION_NOT_PERMITTED = 0x13;
	
	/**
	 * Server can not resolve User Type
	 */
	public static final int UNKNOWN_USER_TYPE = 0x14;
	
	/**
	 * Server was expecting a request
	 */
	public static final int NOT_A_REQUEST = 0x15;

	/**
	 * Server received data of an Invalid format
	 */
	public static final int INVALID_FORMAT = 0x16;
	
	/**
	 * A value that was expected in a message has not been found
	 */
	public static final int MSG_MISSING_VALUE = 0x17;
	
	/**
	 * Server failed to close the connection
	 */
	public static final int CLOSE_FAILED = 0x18;
	
	/**
	 * Something went wrong trying to access the DB
	 */
	public static final int SQL_ERROR = 0x19;

	/**
	 * Something went wrong trying to login
	 */
	public static final int LOGIN_ERROR = 0x1A;

	/**
	 * Client tried to authenticated twice
	 */
	public static final int ALREADY_AUTHENTICATED = 0x1C;
		
	/**
	 * Game could not be found
	 */
	public static final int GAME_NOT_FOUND = 0x1B;

	
}
