package jetbrains.buildServer.server.rest;

/**
 * User: Yegor Yarko
* Date: 29.03.2009
*/
class ErrorInRequestException extends Exception {
  public ErrorInRequestException(String message) {
    super(message);
  }
}
