package jetbrains.buildServer.server.rest;

/**
 * User: Yegor Yarko
* Date: 29.03.2009
*/
class BadRequestException extends RuntimeException {
  public BadRequestException(String message) {
    super(message);
  }
}
