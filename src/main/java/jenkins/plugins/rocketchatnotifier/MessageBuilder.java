package jenkins.plugins.rocketchatnotifier;

import edu.umd.cs.findbugs.annotations.SuppressWarnings;
import hudson.EnvVars;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.Result;
import hudson.model.Run;
import hudson.tasks.test.AbstractTestResultAction;
import hudson.util.LogTaskListener;

import java.io.IOException;
import java.util.logging.Logger;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.SEVERE;


/**
 * @author Martin Reinhardt (hypery2k)
 */
public class MessageBuilder {

  private static final Logger LOGGER = Logger.getLogger(MessageBuilder.class.getName());

  public static final String STARTING_STATUS_MESSAGE = "Starting...",
    END_STATUS_MESSAGE = "Finished",
    BACK_TO_NORMAL_STATUS_MESSAGE = "Back to normal",
    STILL_FAILING_STATUS_MESSAGE = "Still Failing",
    SUCCESS_STATUS_MESSAGE = "Success",
    FAILURE_STATUS_MESSAGE = "Failure",
    ABORTED_STATUS_MESSAGE = "Aborted",
    NOT_BUILT_STATUS_MESSAGE = "Not built",
    UNSTABLE_STATUS_MESSAGE = "Unstable",
    UNKNOWN_STATUS_MESSAGE = "Unknown";
	
  public static final String STARTING_STATUS_COLOR = null,
    END_STATUS_COLOR = null,
    BACK_TO_NORMAL_STATUS_COLOR = "#00ff00",
    STILL_FAILING_STATUS_COLOR = "#ff0000",
    SUCCESS_STATUS_COLOR = "#00ff00",
    FAILURE_STATUS_COLOR = "#ff0000",
    ABORTED_STATUS_COLOR = "#ff0000",
    NOT_BUILT_STATUS_COLOR = null,
    UNSTABLE_STATUS_COLOR = null,
    UNKNOWN_STATUS_COLOR = null;

  private StringBuffer message;
  private RocketChatNotifier notifier;
  private AbstractBuild build;
  private boolean finished = false;
  
  private String buildColor = null;

  MessageBuilder(RocketChatNotifier notifier, AbstractBuild build, boolean finished) {
    this.notifier = notifier;
    this.message = new StringBuffer();
    this.build = build;
    this.finished = finished;
    startMessage();
  }

  MessageBuilder appendStatusMessage() {
    message.append(this.escape(this.getStatusMessage()));
    return this;
  }

  @SuppressWarnings("NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE")
  String getStatusMessage() {
	if (!this.finished) {
		buildColor = STARTING_STATUS_COLOR;
        return STARTING_STATUS_MESSAGE;
    }
    Result result = this.build.getResult();
	while(result == null) {
		result = this.build.getResult();
		try {
            Thread.sleep(10);
		} catch(InterruptedException e){}
	}
    Result previousResult;
    Run previousBuild = this.build.getProject().getLastBuild().getPreviousBuild();
    Run previousSuccessfulBuild = this.build.getPreviousSuccessfulBuild();
    boolean buildHasSucceededBefore = previousSuccessfulBuild != null;

            /*
             * If the last build was aborted, go back to find the last non-aborted build.
             * This is so that aborted builds do not affect build transitions.
             * I.e. if build 1 was failure, build 2 was aborted and build 3 was a success the transition
             * should be failure -> success (and therefore back to normal) not aborted -> success.
             */
    Run lastNonAbortedBuild = previousBuild;
    while (lastNonAbortedBuild != null && lastNonAbortedBuild.getResult() == Result.ABORTED) {
      lastNonAbortedBuild = lastNonAbortedBuild.getPreviousBuild();
    }


            /* If all previous builds have been aborted, then use
             * SUCCESS as a default status so an aborted message is sent
             */
    if (lastNonAbortedBuild == null) {
      previousResult = Result.SUCCESS;
    } else {
      previousResult = lastNonAbortedBuild.getResult();
    }

            /* Back to normal should only be shown if the build has actually succeeded at some point.
             * Also, if a build was previously unstable and has now succeeded the status should be
             * "Back to normal"
             */
    if (result == Result.SUCCESS
      && (previousResult == Result.FAILURE || previousResult == Result.UNSTABLE)
      && buildHasSucceededBefore) {
	  buildColor = BACK_TO_NORMAL_STATUS_COLOR;
      return BACK_TO_NORMAL_STATUS_MESSAGE;
    }
    if (result == Result.FAILURE && previousResult == Result.FAILURE) {
	  buildColor = STILL_FAILING_STATUS_COLOR;
      return STILL_FAILING_STATUS_MESSAGE;
    }
    if (result == Result.SUCCESS) {
	  buildColor = SUCCESS_STATUS_COLOR;
      return SUCCESS_STATUS_MESSAGE;
    }
    if (result == Result.FAILURE) {
	  buildColor = FAILURE_STATUS_COLOR;
      return FAILURE_STATUS_MESSAGE;
    }
    if (result == Result.ABORTED) {
	  buildColor = ABORTED_STATUS_COLOR;
      return ABORTED_STATUS_MESSAGE;
    }
    if (result == Result.NOT_BUILT) {
	  buildColor = NOT_BUILT_STATUS_COLOR;
      return NOT_BUILT_STATUS_MESSAGE;
    }
    if (result == Result.UNSTABLE) {
	  buildColor = UNSTABLE_STATUS_COLOR;
      return UNSTABLE_STATUS_MESSAGE;
    }
	buildColor = UNKNOWN_STATUS_COLOR;
    return UNKNOWN_STATUS_MESSAGE;
  }

  MessageBuilder append(String string) {
    message.append(this.escape(string));
    return this;
  }

  MessageBuilder append(Object string) {
    message.append(this.escape(string.toString()));
    return this;
  }

  private MessageBuilder startMessage() {
    message.append(this.escape(build.getProject().getFullDisplayName()));
    message.append(" - ");
    message.append(this.escape(build.getDisplayName()));
    message.append(" ");
    return this;
  }

  MessageBuilder appendOpenLink() {
    String url = notifier.getBuildServerUrl() + build.getUrl();
    message.append(" (<").append(url).append("|Open>)");
    return this;
  }

  MessageBuilder appendDuration() {
    message.append(" after ");
    String durationString;
    if (message.toString().contains(BACK_TO_NORMAL_STATUS_MESSAGE)) {
      durationString = createBackToNormalDurationString();
    } else {
      durationString = Util.getTimeSpanString(build.getDuration());
    }
    message.append(durationString);
    return this;
  }

  MessageBuilder appendTestSummary() {
    AbstractTestResultAction<?> action = this.build
      .getAction(AbstractTestResultAction.class);
    if (action != null) {
      int total = action.getTotalCount();
      int failed = action.getFailCount();
      int skipped = action.getSkipCount();
      message.append("\nTest Status:\n");
      message.append("\tPassed: " + (total - failed - skipped));
      message.append(", Failed: " + failed);
      message.append(", Skipped: " + skipped);
    } else {
      message.append("\nNo Tests found.");
    }
    return this;
  }

  MessageBuilder appendCustomMessage() {
    String customMessage = notifier.getCustomMessage();
    EnvVars envVars = new EnvVars();
    try {
      envVars = build.getEnvironment(new LogTaskListener(LOGGER, INFO));
    } catch (IOException e) {
      LOGGER.log(SEVERE, e.getMessage(), e);
    } catch (InterruptedException e) {
      LOGGER.log(SEVERE, e.getMessage(), e);
    }
    message.append("\n");
    message.append(envVars.expand(customMessage));
    return this;
  }

  @SuppressWarnings("NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE")
  private String createBackToNormalDurationString() {
    Run previousSuccessfulBuild = build.getPreviousSuccessfulBuild();
    long previousSuccessStartTime = previousSuccessfulBuild.getStartTimeInMillis();
    long previousSuccessDuration = previousSuccessfulBuild.getDuration();
    long previousSuccessEndTime = previousSuccessStartTime + previousSuccessDuration;
    long buildStartTime = build.getStartTimeInMillis();
    long buildDuration = build.getDuration();
    long buildEndTime = buildStartTime + buildDuration;
    long backToNormalDuration = buildEndTime - previousSuccessEndTime;
    return Util.getTimeSpanString(backToNormalDuration);
  }

  String escape(String string) {
    string = string.replace("&", "&amp;");
    string = string.replace("<", "&lt;");
    string = string.replace(">", "&gt;");

    return string;
  }

  public String toString() {
    return message.toString();
  }
  
  public String getBuildColor() {
    return buildColor;
  }
}
