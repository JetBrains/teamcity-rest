package jetbrains.buildServer.server.rest.model.pages.problems;

import java.util.List;
import java.util.stream.Collectors;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import org.jetbrains.annotations.NotNull;

@XmlType(name = "buildProblemEntryGroup")
public class BuildProblemEntryGroup {
  private String myBuildTypeId;
  private Integer myCount;
  private List<BuildProblemEntry> myEntries;

  public BuildProblemEntryGroup() { }

  public BuildProblemEntryGroup(@NotNull String buildTypeId,
                                @NotNull List<jetbrains.buildServer.server.rest.data.pages.problems.BuildProblemEntry> entries,
                                @NotNull Fields fields,
                                @NotNull BeanContext beanContext) {
    myEntries = ValueWithDefault.decideDefault(
      fields.isIncluded("entry"),
      resolveEntries(entries, fields.getNestedField("entry"), beanContext)
    );
    myCount = ValueWithDefault.decideDefault(
      fields.isIncluded("count"),
      entries.size()
    );
    myBuildTypeId = ValueWithDefault.decideDefault(
      fields.isIncluded("buildTypeId"),
      buildTypeId
    );
  }

  @XmlAttribute(name = "buildTypeId")
  public String getBuildTypeId() {
    return myBuildTypeId;
  }

  @XmlAttribute(name = "count")
  public Integer getCount() {
    return myCount;
  }

  @XmlElement(name = "entry")
  public List<BuildProblemEntry> getEntries() {
    return myEntries;
  }

  @NotNull
  private List<BuildProblemEntry> resolveEntries(@NotNull List<jetbrains.buildServer.server.rest.data.pages.problems.BuildProblemEntry> entries,
                                                 @NotNull Fields fields,
                                                 @NotNull BeanContext beanContext) {
    return entries.stream()
                  .map(bpe -> new BuildProblemEntry(bpe, fields, beanContext))
                  .collect(Collectors.toList());
  }
}

