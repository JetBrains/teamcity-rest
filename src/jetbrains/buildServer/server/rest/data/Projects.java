package jetbrains.buildServer.server.rest.data;

import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.SProject;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlElement;
import java.util.List;
import java.util.ArrayList;

/**
 * User: Yegor Yarko
 * Date: 29.03.2009
 */
@XmlRootElement(name="projects")
public class Projects {
  @XmlElement(name="project")
  public List<ProjectRef> projects;

  public Projects() {
  }

  public Projects(List<SProject> projectObjects) {
    projects = new ArrayList<ProjectRef>(projectObjects.size());
    for (SProject project : projectObjects) {
      projects.add(new ProjectRef(project));
    }
  }
}