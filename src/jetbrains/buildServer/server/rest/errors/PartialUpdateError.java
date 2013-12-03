package jetbrains.buildServer.server.rest.errors;

import java.util.List;

/**
 * @author Yegor.Yarko
 *         Date: 02.12.13
 */
public class PartialUpdateError extends RuntimeException {
  public PartialUpdateError(String message, List<Throwable> causes) {
    super(getCombinedMessage(message, causes).toString(), getFirst(causes));
  }

  private static Throwable getFirst(final List<Throwable> causes) {
    if (causes == null || causes.size() == 0) {
      return null;
    }
    return causes.get(0);
  }

  private static StringBuilder getCombinedMessage(final String message, final List<Throwable> causes) {
    final StringBuilder resultMessage = new StringBuilder(message);
    if (causes.size() > 0) {
      resultMessage.append(", nested errors: ");
      for (Throwable cause : causes) {
        resultMessage.append(cause.toString()).append(", ");
      }
      resultMessage.delete(resultMessage.length() - 2, resultMessage.length());
    }
    return resultMessage;
  }
}
