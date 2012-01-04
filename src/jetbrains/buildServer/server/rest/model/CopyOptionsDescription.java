package jetbrains.buildServer.server.rest.model;

import javax.xml.bind.annotation.XmlAttribute;

@SuppressWarnings("PublicField")
public class CopyOptionsDescription {
  @XmlAttribute public Boolean copyAllAssociatedSettings;
  @XmlAttribute public Boolean shareVCSRoots;
}