package jetbrains.buildServer.server.rest;

/**
 * User: Yegor Yarko
* Date: 29.03.2009
*/
class NotFoundException extends RuntimeException {
  public NotFoundException(String message) {
    super(message);
  }
}
