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

package jetbrains.buildServer.server.rest.model.deployment;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.data.finder.impl.DeploymentInstanceFinder;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelBaseType;
import jetbrains.buildServer.server.rest.swagger.constants.ObjectType;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.deploymentDashboards.entities.DeploymentInstance;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@XmlRootElement(name = "deploymentInstances")
@XmlType(name = "deploymentInstance-ref")
@ModelBaseType(ObjectType.PAGINATED)
@SuppressWarnings("PublicField")
public class Instances {
  public static final String COUNT = "count";

  @XmlAttribute
  public Integer count;

  public static final String DEPLOYMENT_INSTANCE = "deploymentInstance";

  @XmlElement(name = DEPLOYMENT_INSTANCE)
  public List<Instance> items;

  @XmlAttribute(required = false)
  @Nullable
  public String nextHref;

  @XmlAttribute(required = false)
  @Nullable
  public String prevHref;

  @XmlAttribute(required = false)
  @Nullable
  public String href;

  public Instances() {
  }

  public Instances(
    @NotNull final Collection<DeploymentInstance> deploymentInstances,
    @Nullable final PagerData pagerData,
    @NotNull final Fields fields,
    @NotNull final BeanContext beanContext
  ) {
    init(deploymentInstances, pagerData, fields, beanContext);
  }

  public Instances(
    @Nullable final String locator,
    @Nullable final PagerData pagerData,
    @NotNull final Fields fields,
    @NotNull final BeanContext beanContext
  ) {
    DeploymentInstanceFinder finder = beanContext.getSingletonService(DeploymentInstanceFinder.class);
    List<DeploymentInstance> result = null;
    if (
      fields.isIncluded(DEPLOYMENT_INSTANCE, false, true)
    ) {
      result = finder.getItems(locator).getEntries();
    }
    init(result, pagerData, fields, beanContext);
  }

  private void init(final @Nullable Collection<DeploymentInstance> deploymentInstances,
                    final @Nullable PagerData pagerData,
                    final @NotNull Fields fields,
                    final @NotNull BeanContext beanContext) {
    if (deploymentInstances != null) {
      items = ValueWithDefault.decideDefault(
        fields.isIncluded(DEPLOYMENT_INSTANCE, false, true),
        resolveItems(deploymentInstances, fields, beanContext)
      );

      count = ValueWithDefault.decideIncludeByDefault(
        fields.isIncluded(COUNT),
        deploymentInstances.size()
      );
    }

    if (pagerData != null) {
      href = ValueWithDefault.decideDefault(
        fields.isIncluded("href", true),
        resolveHref(pagerData, beanContext)
      );
      nextHref = ValueWithDefault
        .decideDefault(
          fields.isIncluded("nextHref"),
          resolveNextHref(pagerData, beanContext)
        );
      prevHref = ValueWithDefault
        .decideDefault(
          fields.isIncluded("prevHref"),
          resolvePrevHref(pagerData, beanContext)
        );
    }
  }

  @NotNull
  private static ArrayList<Instance> resolveItems(@NotNull Collection<DeploymentInstance> deploymentInstances, @NotNull Fields fields, @NotNull BeanContext beanContext) {
    final ArrayList<Instance> items = new ArrayList<Instance>(deploymentInstances.size());
    Fields instanceFields = fields.getNestedField(DEPLOYMENT_INSTANCE);
    for (DeploymentInstance item : deploymentInstances) {
      items.add(new Instance(item, instanceFields, beanContext));
    }
    return items;
  }

  @Nullable
  private static String resolveHref(@NotNull PagerData pagerData, @NotNull BeanContext beanContext) {
    return beanContext
      .getApiUrlBuilder()
      .transformRelativePath(
        pagerData.getHref()
      );
  }

  @Nullable
  private static String resolveNextHref(@NotNull PagerData pagerData, @NotNull BeanContext beanContext) {
    return pagerData.getNextHref() != null ?
           beanContext
             .getApiUrlBuilder()
             .transformRelativePath(
               pagerData.getNextHref()
             ) : null;
  }

  @Nullable
  private static String resolvePrevHref(@NotNull PagerData pagerData, @NotNull BeanContext beanContext) {
    return pagerData.getPrevHref() != null ?
           beanContext
             .getApiUrlBuilder()
             .transformRelativePath(
               pagerData.getPrevHref()
             ) : null;
  }
}
