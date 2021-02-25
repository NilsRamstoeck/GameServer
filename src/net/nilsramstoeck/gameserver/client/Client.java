
package net.nilsramstoeck.gameserver.client;

import org.java_websocket.WebSocket;
import net.nilsramstoeck.gameserver.message.Message;

/**
 * A Client that is connected to a GameServer
 * @author Nils Ramstoeck
 *
 */
public class Client {
	
	/**
	 * Bitmask for root authentication
	 */
	public static final int ROOT = 0xFE;
	
	/**
	 * Bitmask for manager authentication
	 */
	public static final int MANAGER = 0x1E;
	
	/**
	 * Bitmask for player authentication
	 */
	public static final int PLAYER = 0x02;
	
	/**
	 * Bitmask for registered authentication
	 */
	public static final int REGISTERED = 0x08;

	/**
	 * Bitmask for hosting players
	 */
	public static final int HOST = 0x04;
	
	/**
	 * Bitmask for the authenticated bit
	 */
	public static final int AUTHENTICATED = 0x01;
	
	/**
	 * WebSocket connection of the client
	 */
	private WebSocket socket = null;

	/**
	 * Username of the client
	 */
	private String username = null;

	/**
	 * Session ID
	 */
	private String sessionID = null;
	
	/**
	 * authentication level
	 */
	private int authLevel = 0x00;

	/**
	 * User ID
	 */
	private int userID;

	public Client(WebSocket _socket) {
		this.socket = _socket;
	}

	/**
	 * Convenience method to WebSocket.send()
	 * 
	 * @param message Message to be send
	 */
	public void send(Message message) {
		System.out.println("Message to " + this.username + ": " + message);
		this.socket.send(message.toString());
	}

	/**
	 * Username getter
	 * 
	 * @return {@link #username}
	 */
	public String getUsername() {
		return username;
	}

	/**
	 * Username setter
	 * 
	 * @param _username new Username
	 */
	public void setUsername(String _username) {
		this.username = _username;
	}

	
	/**
	 * Check if client is a host
	 * @return is client a host
	 */
	public boolean isHost() {
		return this.checkAuth(HOST);
	}
	
	/**
	 * Socket Getter
	 * @return {@link #socket}
	 */
	public WebSocket getSocket() {
		return socket;
	}

	/**
	 * Sets the authLevel of a client
	 * @param authMasks Authentication masks to be set
	 */
	public void setAuthLevel(int... authMasks) {
		this.authLevel = 0x00;
		for(int m : authMasks) {
			this.authLevel |= m;
		}
	}
	
	/**
	 * Sets the authentication bit of {@link #authLevel}
	 */
	public void authenticate() {
		this.authLevel |= Client.AUTHENTICATED;
	}
	
	/**
	 * Checks if mask matches value b
	 * @param b Value to match
	 * @param mask Mask to apply
	 * @return if mask matches value
	 */
	static public boolean matchMask(int b, int mask) {
		return (b & mask) == mask;
	}
	
	/**
	 * Checks if client is authenticated with a certain level
	 * @param mask Mask of the AuthLevel to check
	 * @return if client is authenticated
	 */
	public boolean checkAuth(int mask) {
		return Client.matchMask(this.authLevel,  Client.AUTHENTICATED | mask);
	}

	/**
	 * authLevel getter
	 * @return {@link #authLevel}
	 */
	public int getAuthLevel() {
		return this.authLevel;
	}

	/**
	 * SessionID getter
	 * @return SessionID
	 */
	public String getSessionID() {
		return sessionID;
	}

	/**
	 * SessionID setter
	 * @param sessionID New SessionID
	 */
	public void setSessionID(String sessionID) {
		this.sessionID = sessionID;
	}

	/**
	 * Disconnect the client
	 * @param reason Reason for the disconnect
	 * @param code Close Code
	 */
	public void disconnect(String reason, int code) {
		socket.close(code, reason);		
	}

	/**
	 * UserID getter
	 * @return UserID
	 */
	public int getUserID() {
		return userID;
	}

	/**
	 * UserID setter
	 * @param userID New UserID
	 */
	public void setUserID(int userID) {
		this.userID = userID;
	}
}
