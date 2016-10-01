package i5.las2peer.services.shortMessageService;

import java.text.SimpleDateFormat;
import java.util.GregorianCalendar;
import java.util.logging.Level;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

import i5.las2peer.api.exceptions.ArtifactNotFoundException;
import i5.las2peer.logging.L2pLogger;
import i5.las2peer.logging.NodeObserver.Event;
import i5.las2peer.p2p.AgentNotKnownException;
import i5.las2peer.persistency.Envelope;
import i5.las2peer.restMapper.RESTService;
import i5.las2peer.security.Agent;
import i5.las2peer.security.GroupAgent;
import i5.las2peer.security.L2pSecurityException;
import i5.las2peer.security.ServiceAgent;
import i5.las2peer.security.UserAgent;

/**
 * This is a middleware service for las2peer that provides methods to send short messages via a las2peer network. It is
 * stateless, so there exist no session dependent values and it uses the las2peer shared storage for persistence. This
 * makes it possible to run and use the service either at each node that joins a las2peer network or to just call the
 * service from a las2peer instance that joined a network that contains at least one node hosting this service.<br>
 * 
 * <h3>Usage Hints</h3>
 * 
 * If you are new to las2peer and only want to start an instance (or ring) hosting this service, you can make use of the
 * start-script from the bin/ directory that comes with this project.
 * <p>
 * Since there currently exists no user manager application, you will have to add each user as an XML-file to the
 * "config/startup" directory. This directory will be uploaded when you execute the start scripts. To produce agent
 * XML-files, you will have to make use of the las2peer ServiceAgentGenerator. At GitHub, there exists a script to use
 * this tool in the las2peer-Template-Project of the RWTH-ACIS group.
 * 
 */
@Path("/sms-service")
public class ShortMessageService extends RESTService {

	private static final L2pLogger logger = L2pLogger.getInstance(ShortMessageService.class.getName());

	/**
	 * service properties with default values, can be overwritten with properties file
	 * config/ShortMessageService.properties
	 */
	private long DEFAULT_MAXIMUM_MESSAGE_LENGTH = 140;
	protected long maxMessageLength = DEFAULT_MAXIMUM_MESSAGE_LENGTH;

	private final String STORAGE_BASENAME = "shortmessagestorage";

	/**
	 * Constructor: Loads the properties file and sets the values.
	 */
	public ShortMessageService() {
		setFieldValues();
	}

	/**
	 * Sends a {@link ShortMessage} to a recipient specified by his agent id.
	 * 
	 * @param receivingAgentId the id of the agent representing the recipient
	 * @param message the actual message text as {@link String}
	 * @return success or error message
	 */
	public String sendShortMessage(long receivingAgentId, String message) {
		// validate message
		if (message == null || message.isEmpty()) {
			String logMsg = "Message can not be empty!";
			L2pLogger.logEvent(Event.SERVICE_CUSTOM_ERROR_1, getContext().getMainAgent(), logMsg);
			return logMsg;
		}
		if (message.length() > maxMessageLength) {
			String logMsg = "Message too long! (Maximum: " + maxMessageLength + ")";
			L2pLogger.logEvent(Event.SERVICE_CUSTOM_ERROR_2, getContext().getMainAgent(), logMsg);
			return logMsg;
		}
		UserAgent sendingAgent = (UserAgent) getContext().getMainAgent();
		// create short message
		ShortMessage msg = new ShortMessage(sendingAgent.getId(), receivingAgentId, message);
		msg.setSendTimestamp(new GregorianCalendar());
		// persist message to recipient storage
		try {
			Envelope env = null;
			ShortMessageBox stored = null;
			try {
				env = getContext().fetchEnvelope(STORAGE_BASENAME + receivingAgentId);
				// get messages from storage
				stored = (ShortMessageBox) env.getContent(getAgent());
				// add new message
				stored.addMessage(msg);
				env = getContext().createEnvelope(env, stored, getAgent());
			} catch (ArtifactNotFoundException e) {
				String logMsg = "Network storage not found. Creating new one. " + e.toString();
				logger.info(logMsg);
				L2pLogger.logEvent(Event.SERVICE_CUSTOM_ERROR_3, getContext().getMainAgent(), logMsg);
				stored = new ShortMessageBox(1);
				// add new message
				stored.addMessage(msg);
				env = getContext().createEnvelope(STORAGE_BASENAME + receivingAgentId, stored, getAgent());
			}
			getContext().storeEnvelope(env);
			String logMsg = "stored " + stored.size() + " messages in network storage";
			logger.info(logMsg);
			L2pLogger.logEvent(Event.SERVICE_CUSTOM_MESSAGE_1, getContext().getMainAgent(), logMsg);
			return "Message send successfully";
		} catch (Exception e) {
			String logMsg = "Can't persist short messages to network storage!";
			logger.log(Level.SEVERE, logMsg, e);
			L2pLogger.logEvent(Event.SERVICE_CUSTOM_ERROR_4, getContext().getMainAgent(), logMsg);
		}
		return "Failure sending message";
	}

	/**
	 * Sends a {@link ShortMessage} to a recipient specified by login or email.
	 * 
	 * @param message the actual message text as {@link String}
	 * @param recipient the login name or email address representing the recipient
	 * @return success or error message
	 */
	@GET
	@Path("/sendShortMessage/{recipient}/{message}")
	public String sendShortMessage(@PathParam("recipient") String recipient, @PathParam("message") String message) {
		if (recipient == null || recipient.isEmpty()) {
			String logMsg = "No recipient specified!";
			L2pLogger.logEvent(Event.SERVICE_CUSTOM_MESSAGE_2, getContext().getMainAgent(), logMsg);
			return logMsg;
		}
		long receiverId;
		try {
			receiverId = getContext().getLocalNode().getAgentIdForEmail(recipient);
		} catch (AgentNotKnownException | L2pSecurityException e) {
			try {
				receiverId = getContext().getLocalNode().getAgentIdForLogin(recipient);
			} catch (AgentNotKnownException | L2pSecurityException e2) {
				String logMsg = "There exists no agent for '" + recipient + "'! Email: " + e.getMessage() + " Login: "
						+ e2.getMessage();
				L2pLogger.logEvent(Event.SERVICE_CUSTOM_ERROR_5, getContext().getMainAgent(), logMsg);
				return logMsg;
			}
		}
		L2pLogger.logEvent(Event.SERVICE_CUSTOM_MESSAGE_9, getContext().getMainAgent(), "message send requested");
		return sendShortMessage(receiverId, message);
	}

	/**
	 * Gets all {@link ShortMessage}'s for the active agent.
	 * 
	 * @return array with all messages
	 */
	public ShortMessage[] getShortMessages() {
		try {
			// load messages from network
			Agent owner = getContext().getMainAgent();
			Envelope env = getContext().fetchEnvelope(STORAGE_BASENAME + owner.getId());
			ShortMessageBox stored = (ShortMessageBox) env.getContent(getAgent());
			String logMsg = "Loaded " + stored.size() + " messages from network storage";
			logger.info(logMsg);
			L2pLogger.logEvent(Event.SERVICE_CUSTOM_MESSAGE_3, getContext().getMainAgent(), logMsg);
			ShortMessage[] result = stored.getMessages();
			return result;
		} catch (ArtifactNotFoundException e) {
			String logMsg = "No messages found in network!";
			logger.log(Level.INFO, logMsg);
			L2pLogger.logEvent(Event.SERVICE_CUSTOM_MESSAGE_4, getContext().getMainAgent(), logMsg);
		} catch (Exception e) {
			String logMsg = "Can't read messages from network storage!";
			logger.log(Level.SEVERE, logMsg, e);
			L2pLogger.logEvent(Event.SERVICE_CUSTOM_ERROR_6, getContext().getMainAgent(), logMsg);
		}
		return new ShortMessage[0];
	}

	/**
	 * Gets messages separated by newline (\n) for the requesting agent. Sender and recipient agent ids are replaced
	 * with their names.
	 * 
	 * @return A String with messages or "No messages"
	 */
	@GET
	@Path("/getShortMessagesAsString")
	public String getShortMessagesAsString() {
		ShortMessage[] messages = getShortMessages();
		if (messages == null || messages.length == 0) {
			String logMsg = "No messages";
			L2pLogger.logEvent(Event.SERVICE_CUSTOM_MESSAGE_5, getContext().getMainAgent(), logMsg);
			return logMsg;
		} else {
			SimpleDateFormat sdf = new SimpleDateFormat();
			StringBuilder sb = new StringBuilder();
			for (ShortMessage sms : messages) {
				sb.append(sdf.format(sms.getSendTimestamp().getTime()) + " from " + getAgentName(sms.getSenderId())
						+ " to " + getAgentName(sms.getRecipientId()) + ": " + new String(sms.getContent()) + "\n");
			}
			L2pLogger.logEvent(Event.SERVICE_CUSTOM_MESSAGE_6, getContext().getMainAgent(), "messages fetched");
			return sb.toString();
		}
	}

	/**
	 * Clears all messages for the active agent inside the storage. This can't be undone!
	 */
	@GET
	@Path("/deleteShortMessages")
	public void deleteShortMessages() {
		try {
			Agent owner = getContext().getMainAgent();
			Envelope env = getContext().fetchEnvelope(STORAGE_BASENAME + owner.getId());
			ShortMessageBox stored = (ShortMessageBox) env.getContent();
			stored.clear();
			Envelope updated = getContext().createEnvelope(env, stored);
			getContext().storeEnvelope(updated);
			L2pLogger.logEvent(Event.SERVICE_CUSTOM_MESSAGE_7, getContext().getMainAgent(), "message deleted");
		} catch (Exception e) {
			String logMsg = "Can't clear messages from network storage!";
			logger.log(Level.SEVERE, logMsg, e);
			L2pLogger.logEvent(Event.SERVICE_CUSTOM_ERROR_6, getContext().getMainAgent(), logMsg);
		}
	}

	/**
	 * Gets the name for a specified agent id. For UserAgent's the login name, for ServiceAgent's the class name and for
	 * GroupAgent's the group name is retrieved.
	 * 
	 * @param agentId The agent id that name should be retrieved
	 * @return Returns the agent name for the given agentId or the agentId as String if an error occurred.
	 */
	protected String getAgentName(long agentId) {
		String result = Long.toString(agentId);
		try {
			Agent agent = getContext().getLocalNode().getAgent(agentId);
			if (agent != null) {
				if (agent instanceof UserAgent) {
					result = ((UserAgent) agent).getLoginName();
				} else if (agent instanceof ServiceAgent) {
					result = ((ServiceAgent) agent).getServiceNameVersion().getName();
				} else if (agent instanceof GroupAgent) {
					result = ((GroupAgent) agent).getName();
				}
			}
			L2pLogger.logEvent(Event.SERVICE_CUSTOM_MESSAGE_9, getContext().getMainAgent(), "agent name resolved");
		} catch (Exception e) {
			String logMsg = "Could not resolve agent id " + agentId;
			logger.log(Level.SEVERE, logMsg, e);
			L2pLogger.logEvent(Event.SERVICE_CUSTOM_ERROR_7, getContext().getMainAgent(), logMsg);
		}
		return result;
	}

}
