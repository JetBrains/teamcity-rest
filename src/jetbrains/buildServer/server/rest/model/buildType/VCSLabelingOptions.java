package jetbrains.buildServer.server.rest.model.buildType;

import java.util.Arrays;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.BuildTypeOrTemplate;
import jetbrains.buildServer.serverSide.vcs.VcsLabelingSettings;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 04.05.13
 */
@SuppressWarnings("PublicField")
@XmlRootElement(name="vcs-labeling")
public class VCSLabelingOptions {
  @XmlAttribute(name = "labelName")
  public String labelName;

  @XmlAttribute(name = "type")
  public String type;

  @XmlElement(name = "branchFilter")
  public String branchFilter;

  public VCSLabelingOptions() {
  }

  public VCSLabelingOptions(@NotNull final BuildTypeOrTemplate buildType, @NotNull final ApiUrlBuilder apiUrlBuilder) {
    labelName = buildType.get().getLabelPattern();
    type = buildType.get().getLabelingType().toString();
    branchFilter = buildType.get().getBranchFilter();
  }

  public void applyTo(final BuildTypeOrTemplate buildType, @NotNull final BeanContext context) {
    if (labelName == null) {
      throw new BadRequestException("Label name is not specified.");
    }
    if (type == null) {
      throw new BadRequestException("Labeling type is not specified.");
    }

    VcsLabelingSettings.LabelingType labelingType;
    try {
      labelingType = VcsLabelingSettings.LabelingType.valueOf(type);
    } catch (IllegalArgumentException e) {
      throw new BadRequestException("Invalid labeling type value. Should be one of " + Arrays.toString(VcsLabelingSettings.LabelingType.values()));
    }

    buildType.get().setLabelPattern(labelName);
    buildType.get().setLabelingType(labelingType);
    if (branchFilter != null){
      buildType.get().setBranchFilter(branchFilter);
    }else{
      buildType.get().setBranchFilter(""); //todo: TeamCity API: not clear if this is correct way to reset the value
    }
  }
}
