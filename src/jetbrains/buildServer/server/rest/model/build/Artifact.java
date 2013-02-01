package jetbrains.buildServer.server.rest.model.build;

import java.util.Date;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.model.Util;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.artifacts.BuildArtifact;
import jetbrains.buildServer.util.FileUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vladislav.Rassokhin
 */
@XmlRootElement(name = "artifact")
@XmlType(name = "artifact", propOrder = {"name", "relativePath", "size", "timestamp", "href", "type"})
public class Artifact {

  private final BuildArtifact myArtifact;
  private final ApiUrlBuilder myApiUrlBuilder;
  private final SBuild myBuild;

  public Artifact() {
    myArtifact = null;
    myApiUrlBuilder = null;
    myBuild = null;
  }

  public Artifact(@NotNull final BuildArtifact artifact, @NotNull final ApiUrlBuilder apiUrlBuilder, @NotNull final SBuild build) {
    myArtifact = artifact;
    myApiUrlBuilder = apiUrlBuilder;
    myBuild = build;
  }

  @XmlAttribute(name = "name")
  public String getName() {
    return myArtifact.getName();
  }

  @XmlAttribute(name = "relativePath")
  public String getRelativePath() {
    return myArtifact.getRelativePath();
  }

  @XmlAttribute(name = "size")
  public long getSize() {
    return myArtifact.getSize();
  }

  @XmlAttribute(name = "timestamp")
  public String getTimestamp() {
    return Util.formatTime(new Date(myArtifact.getTimestamp()));
  }

  @XmlAttribute(name = "href")
  public String getHref() {
    return myApiUrlBuilder.getHref(myBuild) + "/artifacts/files/" + getRelativePath();
  }

  @XmlAttribute(name = "type")
  public String getType() {
    return FileUtil.getExtension(getRelativePath());
  }
}