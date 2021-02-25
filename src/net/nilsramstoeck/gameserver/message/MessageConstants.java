package net.nilsramstoeck.gameserver.message;

/**
 * A List of all Message Constants
 * @author Nils Ramstoeck
 *
 */
public interface MessageConstants {
	/**
	 * Register action constant
	 */
	public static final String REGISTER = "register";

	/**
	 * Login action constant
	 */
	public static final String LOGIN = "login";

	/**
	 * Authenticate Session ID action constant
	 */
	public static final String SESS_AUTH = "session_auth";	
	
	/**
	 * Register action constant
	 */
	public static final String LOGIN_GUEST = "login_guest";

	/**
	 * Enter Game action constant
	 */
	public static final String ENTER_GAME = "enter_game";

	/**
	 * Create game action constant
	 */
	public static final String CREATE_GAME = "create_game";

	/**
	 * Sign out action constant
	 */
	public static final String SIGN_OUT = "sign_out";

	/**
	 * Value request/response/value constant
	 */
	public static final String VALUE = "value";

	/**
	 * Authenticate type constant
	 */
	public static final String AUTHENTICATE = "authenticate";

	/**
	 * Request type constant
	 */
	public static final String REQUEST = "request";

	/**
	 * Response type constant
	 */
	public static final String RESPONSE = "response";

	/**
	 * Error type constant
	 */
	public static final String ERROR = "error";

	/**
	 * Error code value constant
	 */
	public static final String ERROR_CODE = "error_code";

	/**
	 * Type value constant
	 */
	public static final String TYPE = "type";

	/**
	 * Action value constant
	 */
	public static final String ACTION = "action";

	/**
	 * Authenticated value constant
	 */
	public static final String AUTHENTICATED = "authenticated";

	/**
	 * Success value constant
	 */
	public static final String SUCCESS = "success";

	/**
	 * Message value constant
	 */
	public static final String MESSAGE = "message";

	/**
	 * Root user type constant
	 */
	public static final String ROOT = "root";

	/**
	 * Manager user type constant
	 */
	public static final String MANAGER = "manager";

	/**
	 * Player user type constant
	 */
	public static final String PLAYER = "player";

	/**
	 * Message ID value constant
	 */
	public static final String MESSAGE_ID = "message_id";

	/**
	 * Password value constant
	 */
	public static final String PASSWORD = "password";

	/**
	 * Username value constant
	 */
	public static final String USERNAME = "username";
	
	/**
	 * Session ID value constant
	 */
	public static final String GS_SESS_ID = "session_id";

	/**
	 * Game ID value constant
	 */
	public static final String GAME_ID = "game_id";
}
