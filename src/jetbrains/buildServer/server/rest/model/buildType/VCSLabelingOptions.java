package jetbrains.buildServer.server.rest.model.buildType;

import java.util.Arrays;
import java.util.List;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.VcsRootFinder;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.BuildTypeOrTemplate;
import jetbrains.buildServer.serverSide.BuildTypeOptions;
import jetbrains.buildServer.serverSide.vcs.VcsLabelingSettings;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.Converter;
import jetbrains.buildServer.vcs.SVcsRoot;
import jetbrains.buildServer.vcs.VcsRoot;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 04.05.13
 */
@SuppressWarnings("PublicField")
@XmlRootElement(name="vcsLabeling")
public class VCSLabelingOptions {
  @XmlAttribute(name = "labelName")
  public String labelName;

  @XmlAttribute(name = "type")
  public String type;

  @XmlElement(name = "branchFilter")
  public String branchFilter;

  @XmlElement(name = "vcsRoots")
  public VcsRoots vcsRoots;

  public VCSLabelingOptions() {
  }

  public VCSLabelingOptions(@NotNull final BuildTypeOrTemplate buildType, @NotNull final ApiUrlBuilder apiUrlBuilder) {
    labelName = buildType.get().getLabelPattern();
    type = buildType.get().getLabelingType().toString();
    branchFilter = buildType.get().getOption(BuildTypeOptions.VCS_LABELING_BRANCH_FILTER);
    vcsRoots = new VcsRoots(getSVcsRoots(buildType.get().getLabelingRoots()), null, apiUrlBuilder);
  }

  //necessary because of TeamCity open API issue
  private List<SVcsRoot> getSVcsRoots(final List<VcsRoot> roots) {
    return CollectionsUtil.convertCollection(roots, new Converter<SVcsRoot, VcsRoot>() {
      public SVcsRoot createFrom(@NotNull final VcsRoot source) {
        return (SVcsRoot)source;
      }
    });
  }

  private List<VcsRoot> getVcsRoots(final List<SVcsRoot> roots) {
    return CollectionsUtil.convertCollection(roots, new Converter<VcsRoot, SVcsRoot>() {
      public VcsRoot createFrom(@NotNull final SVcsRoot source) {
        return source;
      }
    });
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

    buildType.get().setLabelingRoots(getVcsRoots(vcsRoots.getVcsRoots(context.getSingletonService(VcsRootFinder.class))));
    buildType.get().setLabelPattern(labelName);
    buildType.get().setLabelingType(labelingType);
    if (branchFilter != null){
      buildType.get().setOption(BuildTypeOptions.VCS_LABELING_BRANCH_FILTER, branchFilter);
    }else{
      buildType.get().setOption(BuildTypeOptions.VCS_LABELING_BRANCH_FILTER, BuildTypeOptions.DEFAULT_VCS_LABELING_BRANCH_FILTER);
    }
  }
}
