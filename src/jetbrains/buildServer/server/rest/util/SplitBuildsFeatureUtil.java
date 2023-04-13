/*
 * Copyright 2000-2022 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.server.rest.util;

import com.intellij.openapi.util.Pair;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import jetbrains.buildServer.serverSide.BuildPromotion;
import jetbrains.buildServer.serverSide.BuildPromotionEx;
import jetbrains.buildServer.serverSide.SBuildFeatureDescriptor;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.dependency.BuildDependency;
import org.jetbrains.annotations.NotNull;

public class SplitBuildsFeatureUtil {
  private static final Pattern MATRIX_PARAM_SEPARATOR = Pattern.compile(",");
  public static final String PARALLEL_TESTS_FEATURE_TYPE = "parallelTests";
  public static final String LINK_TO_PARENT_PARAM_NAME = "teamcity.internal.original.link.id";
  public static final String MATRIX_FEATURE_TYPE = "matrix";
  public static final String MATRIX_PARAM_NAME_PREFIX = "matrix.param.";
  public static final String LINK_TO_PARENT_BT_PREFIX = "bt:";

  public static boolean isVirtualBuild(@NotNull BuildPromotion build) {
    SBuildType bt = build.getBuildType();
    return bt != null && bt.getProject().isVirtual();
  }

  public static boolean isVirtualConfiguration(@NotNull SBuildType bt) {
    return bt.getProject().isVirtual();
  }

  public static boolean isParallelizedBuild(@NotNull BuildPromotion buildPromotion) {
    if(!buildPromotion.isCompositeBuild()) {
      return false;
    }

    return !((BuildPromotionEx)buildPromotion).getBuildSettings().getBuildFeaturesOfType(PARALLEL_TESTS_FEATURE_TYPE).isEmpty();
  }

  public static boolean isMatrixBuild(@NotNull BuildPromotion buildPromotion) {
    if(!buildPromotion.isCompositeBuild()) {
      return false;
    }

    return !((BuildPromotionEx)buildPromotion).getBuildSettings().getBuildFeaturesOfType(MATRIX_FEATURE_TYPE).isEmpty();
  }

  @NotNull
  public static List<BuildPromotion> getMatrixDependencies(@NotNull BuildPromotion matrixHead) {
    return matrixHead.getDependencies().stream()
                     .map(dep -> dep.getDependOn())
                     .filter(dep -> isMatrixDependency(matrixHead, dep))
                     .collect(Collectors.toList());
  }

  private static boolean isMatrixDependency(@NotNull BuildPromotion head, @NotNull BuildPromotion dep) {
    SBuildType headBuildType = head.getBuildType();
    if(headBuildType == null) {
      return false;
    }

    String linkParam = dep.getParameterValue(LINK_TO_PARENT_PARAM_NAME);
    if(linkParam == null || !linkParam.startsWith(LINK_TO_PARENT_BT_PREFIX)) {
      return false;
    }

    return headBuildType.getExternalId().equals(linkParam.substring(LINK_TO_PARENT_BT_PREFIX.length()));
  }

  @NotNull
  public static List<MatrixParameter> getMatrixParameters(@NotNull BuildPromotion matrixPromotion) {
    if(!isMatrixBuild(matrixPromotion)) {
      throw getMissingMatrixBuildFeatureException();
    }

    SBuildFeatureDescriptor featureDescriptor = matrixPromotion.getBuildSettings()
                                                               .getBuildFeaturesOfType(MATRIX_FEATURE_TYPE).stream()
                                                               .findFirst()
                                                               .orElseThrow(SplitBuildsFeatureUtil::getMissingMatrixBuildFeatureException);

    return featureDescriptor.getParameters().entrySet().stream()
                            .filter(entry -> entry.getKey().startsWith(MATRIX_PARAM_NAME_PREFIX))
                            .map(entry -> {
                              String paramName = entry.getKey().substring(MATRIX_PARAM_NAME_PREFIX.length());
                              List<String> paramValues = Arrays.asList(MATRIX_PARAM_SEPARATOR.split(entry.getValue()));
                              return new MatrixParameter(paramName, paramValues);
                            })
                            .sorted(Comparator.comparing(MatrixParameter::getName))
                            .collect(Collectors.toList());
  }

  @NotNull
  public static Map<String, String> getMatrixParameterResolvedValues(@NotNull BuildPromotion matrixDependency) {
    BuildPromotion matrixHead = matrixDependency.getDependedOnMe().stream()
                                                .findFirst()
                                                .map(BuildDependency::getDependent)
                                                .orElse(null);

    if(matrixHead == null || !isMatrixBuild(matrixHead)) {
      throw getMissingMatrixBuildFeatureException();
    }

    return SplitBuildsFeatureUtil.getMatrixParameters(matrixHead).stream()
                                 .map(MatrixParameter::getName)
                                 .map(name -> new Pair<>(name, matrixDependency.getParameterValue(name)))
                                 .collect(Collectors.toMap(p -> p.getFirst(), p -> p.getSecond()));
  }

  @NotNull
  private static IllegalArgumentException getMissingMatrixBuildFeatureException() {
    return new IllegalArgumentException("Matrix build feature is not enabled for this build.");
  }

  public static class MatrixParameter {
    private final String myName;
    private final List<String> myValues;

    public MatrixParameter(@NotNull String name, @NotNull List<String> values) {
      myName = name;
      myValues = values;
    }

    @NotNull
    public String getName() {
      return myName;
    }

    @NotNull
    public List<String> getValues() {
      return myValues;
    }
  }
}
