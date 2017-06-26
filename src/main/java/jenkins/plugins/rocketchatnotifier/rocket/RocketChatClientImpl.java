package jenkins.plugins.rocketchatnotifier.rocket;

import jenkins.plugins.rocketchatnotifier.RocketClientImpl;
import jenkins.plugins.rocketchatnotifier.model.Info;
import jenkins.plugins.rocketchatnotifier.model.Response;
import jenkins.plugins.rocketchatnotifier.model.Room;
import jenkins.plugins.rocketchatnotifier.model.User;
import sun.security.validator.ValidatorException;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Client for Rocket.Chat which relies on the REST API v1.
 * <p>
 * Please note, this client does <strong>not</strong> cache any of the results.
 *
 * @version 0.1.0
 * @since 0.0.1
 */
public class RocketChatClientImpl implements RocketChatClient {
  private RocketChatClientCallBuilder callBuilder;

  private static final Logger LOG = Logger.getLogger(RocketClientImpl.class.getName());

  /**
   * Initialize a new instance of the client providing the server's url along with username and
   * password to use.
   *
   * @param serverUrl of the Rocket.Chat server, with or without it ending in "/api/"
   * @param user      which to authenticate with
   * @param password  of the user to authenticate with
   */
  public RocketChatClientImpl(String serverUrl, String user, String password) {
    this.callBuilder = new RocketChatClientCallBuilder(serverUrl, user, password);
  }

  @Override
  public User[] getUsers() throws IOException {
    Response res = this.callBuilder.buildCall(RocketChatRestApiV1.UsersList);

    if (!res.isSuccessful()) {
      LOG.severe("Could not read users information: " + res.getMessage().getMsg());
      throw new IOException("The call to get the User's Information was unsuccessful.");
    }
    if (!res.isUsers()) {
      LOG.severe("Failed to read users information: " + res.getMessage().getMsg());
      throw new IOException("Get User Information failed to retrieve a user.");
    }
    return res.getUsers();
  }

  @Override
  public User getUser(String userId) throws IOException {
    Response res = this.callBuilder.buildCall(RocketChatRestApiV1.UsersInfo,
                                              new RocketChatQueryParams("userId", userId));

    if (!res.isSuccessful()) {
      LOG.severe("Could not read user information: " + res.getMessage().getMsg());
      throw new IOException("The call to get the User's Information was unsuccessful.");
    }
    if (!res.isUser()) {
      LOG.severe("Failed to read users information: " + res.getMessage().getMsg());
      throw new IOException("Get User Information failed to retrieve a user.");
    }
    return res.getUser();
  }

  @Override
  public Room[] getChannels() throws IOException {
    Response res = this.callBuilder.buildCall(RocketChatRestApiV1.ChannelsList);

    if (!res.isSuccessful()) {
      LOG.severe("Could not read channels information: " + res.getMessage().getMsg());
      throw new IOException("The call to get the all Channel Information was unsuccessful.");
    }
    return res.getChannels();
  }

  @Override
  public Info getInfo() throws IOException {
    Response res = this.callBuilder.buildCall(RocketChatRestApiV1.Info);

    if (!res.isSuccessful()) {
      LOG.severe("Could not read information: " + res.getMessage().getMsg());
      throw new IOException("The call to get informations was unsuccessful.");
    }
    return res.getInfo();
  }

  @Override
  public void send(Room room, String message) throws ValidatorException, IOException {
    this.send(room.getName(), message);
  }

  @Override
  public void send(String channelName, String message) throws ValidatorException, IOException {
    this.send(channelName, message, null, null);
  }

  @Override
  public void send(final String channelName, final String message, final String emoji, final String avatar)
    throws ValidatorException, IOException {
    this.send(channelName, message, emoji, avatar, null);
  }

  @Override
  public void send(final String channelName, final String message, final String emoji, final String avatar, final List<Map<String, Object>> attachments)
    throws ValidatorException, IOException {
    Map body = new HashMap<String, String>();
    body.put("channel", "#" + channelName);
	if (message != null)
		body.put("text", message);
    if (this.getInfo().getVersion().compareTo("0.50.1") >= 0) {
      if (emoji != null)
        body.put("emoji", emoji);
      else if (avatar != null)
        body.put("avatar", avatar);
      if (attachments != null && attachments.size() > 0)
        body.put("attachments", attachments);
    }
    Response res = this.callBuilder.buildCall(RocketChatRestApiV1.PostMessage, null, body);

    if (!res.isSuccessful()) {
      LOG.severe("Could not send message: " + res.getMessage() != null ? res.getMessage().getMsg() : "");
      throw new IOException("The send of the message was unsuccessful.");
    }
  }

}
