package jetbrains.buildServer.server.rest.model.buildType;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.VcsRootFinder;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.BuildTypeOrTemplate;
import jetbrains.buildServer.serverSide.BuildTypeSettings;
import jetbrains.buildServer.serverSide.SBuildFeatureDescriptor;
import jetbrains.buildServer.serverSide.impl.VcsLabelingBuildFeature;
import jetbrains.buildServer.vcs.SVcsRoot;
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
    labelName = "";
    type = "NONE";
    branchFilter = "";
    vcsRoots = new VcsRoots(Collections.<SVcsRoot>emptyList(), null, apiUrlBuilder);
  }

  public void applyTo(final BuildTypeOrTemplate buildType, @NotNull final BeanContext context) {
    if (labelName == null) {
      throw new BadRequestException("Label name is not specified.");
    }
    if (type == null) {
      throw new BadRequestException("Labeling type is not specified.");
    }

    BuildTypeSettings buildTypeSettings = buildType.get();
    for (SBuildFeatureDescriptor feature: buildTypeSettings.getBuildFeatures()) {
      if (feature.getType().equals(VcsLabelingBuildFeature.VCS_LABELING_TYPE)) {
        buildTypeSettings.removeBuildFeature(feature.getId());
      }
    }

    for (SVcsRoot vcsRoot: vcsRoots.getVcsRoots(context.getSingletonService(VcsRootFinder.class))) {
      Map<String, String> params = new HashMap<String, String>();
      params.put(VcsLabelingBuildFeature.VCS_ROOT_ID_PARAM, vcsRoot.getExternalId());
      params.put(VcsLabelingBuildFeature.LABELING_PATTERN_PARAM, labelName);
      if ("SUCCESSFUL_ONLY".equals(type)) {
        params.put(VcsLabelingBuildFeature.SUCCESSFUL_ONLY_PARAM, "true");
      }

      if (branchFilter != null) {
        params.put(VcsLabelingBuildFeature.BRANCH_FILTER_PARAM, branchFilter);
      }
      buildTypeSettings.addBuildFeature(VcsLabelingBuildFeature.VCS_LABELING_TYPE, params);
    }
  }
}
