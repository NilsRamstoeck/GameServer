package net.nilsramstoeck.gameserver;

/**
 * Exception with a a warning that can be send to the clients
 * @author Nils Ramstoeck
 *
 */
@SuppressWarnings("serial")
public class GameServerException extends Exception implements GameServerErrorCode {

	/**
	 * Error code of the Exception
	 */
	private int errorCode;

	public GameServerException(String _message, int _errorCode) {
		super(_message);
		this.errorCode = _errorCode;
	}

	/**
	 * Gets the error code of the Exception
	 * @return Error code
	 */
	public int getErrorCode() {
		return this.errorCode;
	}
}
