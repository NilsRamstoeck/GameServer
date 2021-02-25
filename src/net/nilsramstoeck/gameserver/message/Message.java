package net.nilsramstoeck.gameserver.message;

import org.json.JSONObject;

/**
 * A message received by a GameServer
 * 
 * @author Nils Ramstoeck
 *
 */
public class Message extends JSONObject implements MessageConstants {

	/**
	 * Build a message from a string in JSON formatting
	 * 
	 * @param jsonString JSON formatted String
	 */
	public Message(String jsonString) {
		super(jsonString);
	}

	/**
	 * Creates a new, empty message
	 */
	public Message() {
		super();
	}

	/**
	 * Builds an error message that can be send to a client
	 * 
	 * @param errorMessage Human-Readable error message
	 * @param messageID    Reference to the message that caused the error.
	 * @param code         ErrorCode
	 * @return Error message
	 */
	public static Message buildErrorMessage(String errorMessage, String messageID, int code) {
		Message error = new Message();
		error.put(Message.TYPE, Message.ERROR);
		error.put(Message.ERROR_CODE, code);
		error.put(Message.MESSAGE, errorMessage);
		error.put(Message.MESSAGE_ID, messageID);
		return error;
	}

	/**
	 * Builds the template for a response message
	 * 
	 * @param message Message to respond to
	 * @return Response template
	 */
	public static Message buildResponseMessage(Message message) {
		Message response = new Message();
		response.put(Message.TYPE, Message.RESPONSE);
		response.put(Message.ACTION, message.getString(Message.ACTION));
		response.put(Message.MESSAGE_ID, message.getString(Message.MESSAGE_ID));
		return response;
	}
}
