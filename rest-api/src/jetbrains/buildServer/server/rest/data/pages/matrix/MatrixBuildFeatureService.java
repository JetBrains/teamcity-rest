package jetbrains.buildServer.server.rest.data.pages.matrix;

import com.intellij.openapi.util.Pair;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import jetbrains.buildServer.ExtensionHolder;
import jetbrains.buildServer.controllers.admin.projects.BuildFeatureBean;
import jetbrains.buildServer.controllers.admin.projects.BuildFeaturesBean;
import jetbrains.buildServer.controllers.project.GenerateSettingsController;
import jetbrains.buildServer.log.ThrottleLogger;
import jetbrains.buildServer.server.matrixBuild.MatrixParamsBuildFeature;
import jetbrains.buildServer.server.matrixBuild.MatrixParamsUtils;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.model.pages.ErrorDescriptor;
import jetbrains.buildServer.server.rest.model.pages.matrix.MatrixParameterDescriptor;
import jetbrains.buildServer.server.rest.model.pages.matrix.MatrixBuildFeatureDescriptor;
import jetbrains.buildServer.server.rest.model.project.LabeledValue;
import jetbrains.buildServer.server.rest.request.pages.matrix.MatrixBuildFeatureSubResource;
import jetbrains.buildServer.server.rest.util.BuildTypeOrTemplate;
import jetbrains.buildServer.server.rest.util.VirtualBuildsUtil;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.dependency.BuildDependency;
import jetbrains.buildServer.serverSide.impl.BuildFeatureDescriptorImpl;
import jetbrains.buildServer.serverSide.impl.versionedSettings.ConfigurationEntityGenerationResult;
import jetbrains.buildServer.serverSide.impl.versionedSettings.ProjectSettingsGenerator;
import jetbrains.buildServer.serverSide.impl.versionedSettings.ProjectSettingsGeneratorRegistry;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
public class MatrixBuildFeatureService {
  private static final ThrottleLogger LOGGER = ThrottleLogger.get1MinLogger(MatrixBuildFeatureService.class);
  private static final Pattern VALID_PARAMETER_NAME = Pattern.compile("[a-zA-Z][a-zA-Z0-9._\\-*]*");
  private static final String ERROR_MESSAGE__AT_LEAST_ONE_PARAM_REQUIRED = "At least one parameter must be configured";
  private static final String ERROR_MESSAGE__INVALID_PARAM_NAME_SYNTAX = "The names of configuration parameters must contain only the [a-zA-Z0-9._-*] characters and start with an ASCII letter";
  private static final String ERROR_MESSAGE__DUPLICATE_PARAM_NAME = "Duplicate parameter name";
  private static final String ERROR_MESSAGE__AT_LEAST_ONE_VALUE_PER_PARAM = "At least one value must be configured";
  private static final String ERROR_MESSAGE__EMPTY_VALUE_MUST_HAVE_A_LABEL = "Empty value must have a label";
  private static final String ERROR_MESSAGE__VALUE_MUST_BE_UNIQUE = "Value must be unique";
  private static final String ERROR_MESSAGE__LABEL_MUST_BE_UNIQUE = "Label must be unique";
  private static final String ERROR_MESSAGE__MATRIX_FEATURE_NOT_FOUND = "Matrix Build feature is not present in this build type";
  private static final String AUDIT_MESSAGE__CREATE_FEATURE_REST = "Matrix Build feature was created via REST API";
  private static final String AUDIT_MESSAGE__CREATE_FEATURE_UI = "Matrix Build feature was created via user interface";
  private static final String AUDIT_MESSAGE__UPDATE_FEATURE_REST = "Matrix Build feature was updated via REST API";
  private static final String AUDIT_MESSAGE__UPDATE_FEATURE_UI = "Matrix Build feature was updated via user interface";
  private static final String AUDIT_MESSAGE__REMOVE_FEATURE_REST = "Matrix Build feature was removed via REST API";
  private static final String AUDIT_MESSAGE__REMOVE_FEATURE_UI = "Matrix Build feature was removed via user interface";

  private final BuildFeatureDescriptorFactory myFeatureDescriptorFactory;
  private final ExtensionHolder myExtentionHolder;
  private final SBuildServer myServer;
  private final ProjectSettingsGeneratorRegistry myGeneratorRegistry;

  public MatrixBuildFeatureService(@NotNull BuildFeatureDescriptorFactory featureDescriptorFactory,
                                   @NotNull ExtensionHolder extensionHolder,
                                   @NotNull ProjectSettingsGeneratorRegistry generatorRegistry,
                                   @NotNull SBuildServer server) {
    myFeatureDescriptorFactory = featureDescriptorFactory;
    myExtentionHolder = extensionHolder;
    myServer = server;
    myGeneratorRegistry = generatorRegistry;
  }

  /**
   * @return created feature id.
   */
  @NotNull
  public String createFeature(@NotNull BuildTypeOrTemplate btt,
                                            @NotNull List<MatrixParameterDescriptor> submittedParams,
                                            boolean isUiAction) {
    return createFeatureInternal(unwrap(btt), submittedParams, isUiAction);
  }

  @NotNull
  private <BTT extends BuildTypeSettings & SPersistentEntity> String createFeatureInternal(@NotNull BTT buildTypeOrTemplate,
                                                                                           @NotNull List<MatrixParameterDescriptor> submittedParams,
                                                                                           boolean isUiAction) {
    Map<String, String> params = convertParameters(submittedParams);

    SBuildFeatureDescriptor createdFeature = myFeatureDescriptorFactory.createNewBuildFeature(MatrixParamsBuildFeature.TYPE, params);
    buildTypeOrTemplate.addBuildFeature(createdFeature);
    String reason = isUiAction ? AUDIT_MESSAGE__CREATE_FEATURE_UI : AUDIT_MESSAGE__CREATE_FEATURE_REST;
    buildTypeOrTemplate.schedulePersisting(reason);

    return createdFeature.getId();
  }

  public void updateExistingFeature(@NotNull BuildTypeOrTemplate btt,
                                    @NotNull String featureId,
                                    @NotNull List<MatrixParameterDescriptor> submittedParams,
                                    boolean isUiAction) {
    updateExistingFeatureInternal(unwrap(btt), featureId, submittedParams, isUiAction);
  }

  private <BTT extends BuildTypeSettings & SPersistentEntity> void updateExistingFeatureInternal(@NotNull BTT buildTypeOrTemplate,
                                                                                                 @NotNull String featureId,
                                                                                                 @NotNull List<MatrixParameterDescriptor> submittedParams,
                                                                                                 boolean isUiAction) {

    BuildFeaturesBean buildFeaturesBean = new BuildFeaturesBean(buildTypeOrTemplate, myExtentionHolder);
    BuildFeatureBean oldFeature = buildFeaturesBean.getBuildFeatureDescriptors().stream()
                                                   .filter(bfb -> MatrixParamsBuildFeature.TYPE.equals(bfb.getType()) && featureId.equals(bfb.getId()))
                                                   .findFirst()
                                                   .orElseThrow(() -> new NotFoundException(ERROR_MESSAGE__MATRIX_FEATURE_NOT_FOUND));

    Map<String, String> params = convertParameters(submittedParams);

    if(oldFeature.isInherited() && !oldFeature.isOverridden()) {
      SBuildFeatureDescriptor createdFeature = myFeatureDescriptorFactory.createBuildFeature(featureId, MatrixParamsBuildFeature.TYPE, params);
      buildTypeOrTemplate.addBuildFeature(createdFeature);
    } else {
      buildTypeOrTemplate.updateBuildFeature(oldFeature.getId(), oldFeature.getType(), params);
    }

    String reason = isUiAction ? AUDIT_MESSAGE__UPDATE_FEATURE_UI : AUDIT_MESSAGE__UPDATE_FEATURE_REST;
    buildTypeOrTemplate.schedulePersisting(reason);

    buildTypeOrTemplate.getBuildFeaturesOfType(MatrixParamsBuildFeature.TYPE).stream()
                       .filter(bfd -> featureId.equals(bfd.getId()))
                       .findFirst()
                       .orElseThrow(() -> new NotFoundException(ERROR_MESSAGE__MATRIX_FEATURE_NOT_FOUND));
  }

  public void removeFeature(@NotNull BuildTypeOrTemplate btt, @NotNull String featureId, boolean isUiAction) {
    removeFeatureInternal(unwrap(btt), featureId, isUiAction);
  }

  private <BTT extends BuildTypeSettings & SPersistentEntity> void removeFeatureInternal(@NotNull BTT buildTypeOrTemplate, @NotNull String featureId, boolean isUiAction) {
    buildTypeOrTemplate.getBuildFeaturesOfType(MatrixParamsBuildFeature.TYPE).stream()
                       .filter(bfd -> featureId.equals(bfd.getId()))
                       .findFirst()
                       .orElseThrow(() -> new NotFoundException(ERROR_MESSAGE__MATRIX_FEATURE_NOT_FOUND));

    SBuildFeatureDescriptor feature = buildTypeOrTemplate.removeBuildFeature(featureId);
    if(feature == null) {
      throw new NotFoundException(ERROR_MESSAGE__MATRIX_FEATURE_NOT_FOUND);
    }

    String reason = isUiAction ? AUDIT_MESSAGE__REMOVE_FEATURE_UI : AUDIT_MESSAGE__REMOVE_FEATURE_REST;
    buildTypeOrTemplate.schedulePersisting(reason);
  }

  @NotNull
  public GenerateDslResult generateDSL(@NotNull BuildTypeOrTemplate btt,
                                       @NotNull MatrixBuildFeatureSubResource.ViewAsCodePayload payload) {

    ProjectSettingsGenerator generator = myGeneratorRegistry.findGenerator(ProjectSettingsGeneratorRegistry.KOTLIN_FORMAT);
    if(generator == null) {
      return new GenerateDslResult(new ErrorDescriptor("Internal error: can't generate DSL, generator not found.", ""));
    }

    final String showDSL = payload.getShowDSL();

    final Map<String, String> dslProperties = new HashMap<>();
    dslProperties.put("version", payload.getShowDSLVersion());
    dslProperties.put("portable", payload.getShowDSLPortable());

    if ("item".equals(showDSL)) {
      Pair<ParametersDescriptor, ErrorDescriptor> descriptor = getParametersFromPayload(btt.get(), payload);
      if (descriptor.second != null) {
        return new GenerateDslResult(descriptor.second);
      }

      return new GenerateDslResult(GenerateSettingsController.generateDSL(generator, btt.getIdentity(), descriptor.first, dslProperties));
    }

    if (!"buildType".equals(showDSL)) {
      return new GenerateDslResult(new ErrorDescriptor("Unknown dsl option.", ""));
    }

    BuildTypeIdentity identity;
    if(btt.isBuildType()) {
      SBuildType buildType = btt.getBuildType();
      assert buildType != null;

      identity = ((BuildTypeEx) buildType).createEditableCopy(true);
    } else if(btt.isTemplate()) {
      BuildTypeTemplate template = btt.getTemplate();
      assert template != null;

      identity = ((BuildTypeTemplateEx)template).createEditableCopy(true);
    } else {
      return new GenerateDslResult(new ErrorDescriptor("Internal error: unknown identity.", ""));
    }

    return new GenerateDslResult(GenerateSettingsController.generateDSL(generator, identity, null, dslProperties));
  }

  @NotNull
  private Pair<ParametersDescriptor, ErrorDescriptor> getParametersFromPayload(@NotNull BuildTypeSettings settings, @NotNull MatrixBuildFeatureSubResource.ViewAsCodePayload payload) {
    Map<String, String> parameters = convertParameters(payload.getDescriptor().getParameters());

    if(payload.getFeatureId() != null) {
      return settings.getBuildFeatures().stream()
                     .filter(bfd -> payload.getFeatureId().equals(bfd.getId()))
                     .findFirst()
                     .map(bfd -> new Pair<ParametersDescriptor, ErrorDescriptor>(new BuildFeatureDescriptorImpl(bfd.getId(), bfd.getType(), parameters, myServer), null))
                     .orElse(new Pair<>(null, new ErrorDescriptor("Can't find build feature by given id.", "")));

    }
    if(MatrixParamsBuildFeature.TYPE.equals(payload.getFeatureType())) {
      return new Pair<>(new BuildFeatureDescriptorImpl("", MatrixParamsBuildFeature.TYPE, parameters, myServer), null);
    }
    return new Pair<ParametersDescriptor, ErrorDescriptor>(null, new ErrorDescriptor("Unable to find build feature by given type or id.", ""));
  }

  /**
   * @return List of defined matrix parameters if given build type has a matrix feature enabled.
   */
  @NotNull
  public MatrixBuildFeatureDescriptor resolveParameters(@NotNull BuildTypeOrTemplate buildTypeOrTemplate, @NotNull String featureId) {
    SBuildFeatureDescriptor descriptor = buildTypeOrTemplate.get().getBuildFeaturesOfType(MatrixParamsBuildFeature.TYPE).stream()
                                                            .filter(fd -> featureId.equals(fd.getId()))
                                                            .findFirst()
                                                            .orElseThrow(() -> new NotFoundException(ERROR_MESSAGE__MATRIX_FEATURE_NOT_FOUND));

    return new MatrixBuildFeatureDescriptor(featureId, resolveParameters(descriptor));
  }

  /**
   * @return List of defined matrix parameters if given promotion is a matrix promotion, null otherwise.
   */
  @Nullable
  public static MatrixBuildFeatureDescriptor resolveParameters(@NotNull BuildPromotion matrixPromotion) {
    if (!isMatrixBuild(matrixPromotion)) {
      return null;
    }

    SBuildFeatureDescriptor featureDescriptor = null;
    try {
      featureDescriptor = matrixPromotion.getBuildSettings()
                                         .getBuildFeaturesOfType(MatrixParamsBuildFeature.TYPE).stream()
                                         .findFirst().orElse(null);
    } catch (BuildTypeNotFoundException e) {
      LOGGER.debug("Unable to get matrix parameters for promotion {} due to a missing build type.", matrixPromotion.getId());
    }

    if (featureDescriptor == null) {
      return null;
    }

    Map<String, Map<String, String>> promotionParameters = MatrixParamsUtils.getMatrixParameters(matrixPromotion);

    List<MatrixParameterDescriptor> params = promotionParameters.entrySet().stream()
                                                            .map(parameter -> resolveParameter(parameter.getKey(), parameter.getValue()))
                                                            .collect(Collectors.toList());

    return new MatrixBuildFeatureDescriptor(featureDescriptor.getId(), params);
  }

  public static boolean isMatrixBuild(@NotNull BuildPromotion buildPromotion) {
    if (!buildPromotion.isCompositeBuild()) {
      return false;
    }

    try {
      return !buildPromotion.getBuildSettings()
                            .getBuildFeaturesOfType(MatrixParamsBuildFeature.TYPE)
                            .isEmpty();
    } catch (BuildTypeNotFoundException e) {
      LOGGER.debug("Unable to check if promotion {} is a matrix build head due to a missing build type.", buildPromotion.getId());
      return false;
    }
  }

  /**
   * Get list of dependecies of which this matrix build is composed.
   */
  @Nullable
  public static List<BuildPromotion> getDependencies(@NotNull BuildPromotion matrixHead) {
    if (!isMatrixBuild(matrixHead)) {
      return null;
    }

    return matrixHead.getDependencies().stream()
                     .map(dep -> dep.getDependOn())
                     .filter(dep -> isMatrixDependency(matrixHead, dep))
                     .collect(Collectors.toList());
  }

  public static boolean isMatrixDependency(@NotNull BuildPromotion head, @NotNull BuildPromotion dep) {
    SBuildType headBuildType = head.getBuildType();
    if (headBuildType == null) {
      return false;
    }

    String linkParam = dep.getParameterValue(VirtualBuildsUtil.LINK_TO_ORIGINAL_PROMOTION_PARAM_NAME);
    if (linkParam == null || !linkParam.startsWith(VirtualBuildsUtil.LINK_TO_PARENT_BT_PREFIX)) {
      return false;
    }

    return headBuildType.getExternalId().equals(linkParam.substring(VirtualBuildsUtil.LINK_TO_PARENT_BT_PREFIX.length()));
  }

  /**
   * Get matrix parameters resolved in this part of the matrix build in a form of mapping (value->label?).
   */
  @Nullable
  public static Map<String, String> getResolvedValues(@NotNull BuildPromotion matrixDependency) {
    BuildPromotion matrixHead = matrixDependency.getDependedOnMe().stream()
                                                .findFirst()
                                                .map(BuildDependency::getDependent)
                                                .orElse(null);

    if (matrixHead == null || !isMatrixBuild(matrixHead)) {
      return null;
    }

    MatrixBuildFeatureDescriptor parameters = resolveParameters(matrixHead);
    if (parameters == null) {
      return null;
    }

    Map<String, String> result = new LinkedHashMap<>();
    for (MatrixParameterDescriptor parameter : parameters.getParameters()) {
      result.put(parameter.getName(), matrixDependency.getParameterValue(parameter.getName()));
    }

    return result;
  }

  /**
   * Validates the following things:
   * <ul>
   * <li> parameter[] is not null and not empty
   * <li> parameter[idx].name is valid and unique
   * <li> parameter[idx].value[] is not null and not empty and at least 2 (?)
   * <li> parameter[idx].value[idx2].value is unique
   * <li> parameter[idx].value[idx2].value is labeled whrn empty
   * <li> parameter[idx].value[idx2].label is unique if not null
   * </ul>
   */
  @NotNull
  public List<ErrorDescriptor> validateParameters(@Nullable List<MatrixParameterDescriptor> parameters) {
    if (parameters == null || parameters.isEmpty()) {
      return Collections.singletonList(new ErrorDescriptor(ERROR_MESSAGE__AT_LEAST_ONE_PARAM_REQUIRED, ""));
    }

    Set<String> seenParameters = new HashSet<>();
    List<ErrorDescriptor> errors = new ArrayList<>();
    for (int i = 0; i < parameters.size(); i++) {
      MatrixParameterDescriptor parameter = parameters.get(i);
      String location = String.format("parameter[%d]", i);

      ErrorDescriptor validationError = validateParameter(parameter, location);
      if (validationError != null) {
        errors.add(validationError);
      }

      if (!seenParameters.add(parameter.getName())) {
        errors.add(new ErrorDescriptor(ERROR_MESSAGE__DUPLICATE_PARAM_NAME, location));
      }
    }

    return errors;
  }

  @Nullable
  private static ErrorDescriptor validateParameter(@NotNull MatrixParameterDescriptor parameter, @NotNull String parameterLocation) {
    if (!VALID_PARAMETER_NAME.matcher(parameter.getName()).matches()) {
      return new ErrorDescriptor(
        ERROR_MESSAGE__INVALID_PARAM_NAME_SYNTAX,
        parameterLocation + ".name"
      );
    }

    if (parameter.getValues() == null || parameter.getValues().size() < 1) {
      return new ErrorDescriptor(
        ERROR_MESSAGE__AT_LEAST_ONE_VALUE_PER_PARAM,
        parameterLocation
      );
    }

    Set<String> seenValues = new HashSet<>();
    Set<String> seenLabels = new HashSet<>();
    for (int i = 0; i < parameter.getValues().size(); i++) {
      LabeledValue labeledValue = parameter.getValues().get(i);

      if (labeledValue.getLabel() != null && !seenLabels.add(labeledValue.getLabel())) {
        return new ErrorDescriptor(
          ERROR_MESSAGE__LABEL_MUST_BE_UNIQUE,
          parameterLocation + String.format(".value[%d]", i)
        );
      }

      if (StringUtil.isEmpty(labeledValue.getValue()) && StringUtil.isEmpty(labeledValue.getLabel())) {
        return new ErrorDescriptor(
          ERROR_MESSAGE__EMPTY_VALUE_MUST_HAVE_A_LABEL,
          parameterLocation + String.format(".value[%d]", i)
        );
      }

      if (!seenValues.add(labeledValue.getValue())) {
        return new ErrorDescriptor(
          ERROR_MESSAGE__VALUE_MUST_BE_UNIQUE,
          parameterLocation + String.format(".value[%d]", i)
        );
      }
    }

    return null;
  }

  @NotNull
  private static Map<String, String> convertParameters(@Nullable List<MatrixParameterDescriptor> parameters) {
    if (parameters == null || parameters.isEmpty()) {
      return Collections.emptyMap();
    }

    // Parameter name => (value => label?)
    Map<String, Map<String, String>> result = new LinkedHashMap<>();
    for (MatrixParameterDescriptor submittedParam : parameters) {
      result.put(submittedParam.getName(), convertParameter(submittedParam));
    }

    return MatrixParamsUtils.fromObject(result, Collections.emptyMap());
  }

  @NotNull
  private static Map<String, String> convertParameter(@NotNull MatrixParameterDescriptor parameters) {
    Map<String, String> result = new LinkedHashMap<>();
    for (LabeledValue value : parameters.getValues()) {
      result.put(value.getValue(), value.getLabel());
    }
    return result;
  }

  @NotNull
  private static List<MatrixParameterDescriptor> resolveParameters(@NotNull SBuildFeatureDescriptor matrixFeatureDescriptor) {
    // Parameter name => (value => label?)
    Map<String, Map<String, String>> featureParams = MatrixParamsUtils.getMatrixParams(MatrixParamsUtils.toObject(matrixFeatureDescriptor.getParameters()));

    return featureParams.entrySet().stream()
                        .map(parameter -> resolveParameter(parameter.getKey(), parameter.getValue()))
                        .collect(Collectors.toList());
  }

  @NotNull
  private static MatrixParameterDescriptor resolveParameter(@NotNull String paramName, @NotNull Map<String, String> paramValues) {
    List<LabeledValue> values = paramValues.entrySet().stream()
                                           .map(value -> new LabeledValue(value.getKey(), value.getValue()))
                                           .collect(Collectors.toList());

    return new MatrixParameterDescriptor(paramName, values);
  }

  private static <BTT extends BuildTypeSettings & SPersistentEntity> BTT unwrap(@NotNull BuildTypeOrTemplate btt) {
    return (BTT)btt.get();
  }

  public static class GenerateDslResult {
    private ErrorDescriptor myError;
    private ConfigurationEntityGenerationResult myDsl;

    public GenerateDslResult(ConfigurationEntityGenerationResult dsl) {
      myError = null;
      myDsl = dsl;
    }

    public GenerateDslResult(ErrorDescriptor error) {
      myError = error;
      myDsl = null;
    }

    public ErrorDescriptor getError() {
      return myError;
    }

    public ConfigurationEntityGenerationResult getDsl() {
      return myDsl;
    }
  }
}
