package jetbrains.buildServer.server.rest.model.pages.problems;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.data.pages.problems.BuildProblemEntry;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import org.jetbrains.annotations.NotNull;

@XmlType(name = "buildProblemEntryGroups")
@XmlRootElement(name = "buildProblemEntryGroups")
public class BuildProblemEntryGroups {
  private Integer myCount;
  private List<BuildProblemEntryGroup> myGroups;

  public BuildProblemEntryGroups() { }

  public BuildProblemEntryGroups(@NotNull Map<String, List<BuildProblemEntry>> groups,
                                 @NotNull Fields fields,
                                 @NotNull BeanContext beanContext) {
    myGroups = ValueWithDefault.decideDefault(
      fields.isIncluded("group"),
      resolveGroups(groups, fields.getNestedField("group"), beanContext)
    );
    myCount = ValueWithDefault.decideDefault(
      fields.isIncluded("count"),
      groups.size()
    );
  }

  @NotNull
  private List<BuildProblemEntryGroup> resolveGroups(@NotNull Map<String, List<BuildProblemEntry>> groups,
                                                     @NotNull Fields fields,
                                                     @NotNull BeanContext beanContext) {
    return groups.entrySet().stream()
                 .map(e -> new BuildProblemEntryGroup(e.getKey(), e.getValue(), fields, beanContext))
                 .collect(Collectors.toList());
  }

  @XmlAttribute(name = "count")
  public Integer getCount() {
    return myCount;
  }

  @XmlElement(name = "group")
  public List<BuildProblemEntryGroup> getGroups() {
    return myGroups;
  }
}
