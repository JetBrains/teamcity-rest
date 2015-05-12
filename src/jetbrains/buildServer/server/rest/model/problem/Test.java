/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.model.problem;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.investigations.InvestigationFinder;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.model.buildType.Investigations;
import jetbrains.buildServer.server.rest.request.InvestigationRequest;
import jetbrains.buildServer.server.rest.request.TestOccurrenceRequest;
import jetbrains.buildServer.server.rest.request.TestRequest;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.STest;
import jetbrains.buildServer.serverSide.mute.CurrentMuteInfo;
import jetbrains.buildServer.serverSide.mute.MuteInfo;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 16.11.13
 */
@SuppressWarnings("PublicField")
@XmlRootElement(name = "test")
@XmlType(name = "test", propOrder = {"id", "name",
  "mutes", "investigations", "testOccurrences"})
public class Test {
  @XmlAttribute public Long id;
  @XmlAttribute public String name;
  @XmlAttribute public String href;

  @XmlElement public Mutes mutes;
  @XmlElement public Investigations investigations;
  @XmlElement public TestOccurrences testOccurrences;

  public Test() {
  }

  public Test(final @NotNull STest test, final @NotNull BeanContext beanContext, @NotNull final Fields fields) {
    id = ValueWithDefault.decideDefault(fields.isIncluded("id"), test.getTestNameId());
    name = ValueWithDefault.decideDefault(fields.isIncluded("name"), test.getName().getAsString());

    final ApiUrlBuilder apiUrlBuilder = beanContext.getApiUrlBuilder();
    href = ValueWithDefault.decideDefault(fields.isIncluded("href"), apiUrlBuilder.transformRelativePath(TestRequest.getHref(test)));

    mutes = ValueWithDefault.decideDefault(fields.isIncluded("mutes", false), new ValueWithDefault.Value<Mutes>() {
      public Mutes get() {
        final ArrayList<MuteInfo> muteInfos = new ArrayList<MuteInfo>();
        final CurrentMuteInfo currentMuteInfo = test.getCurrentMuteInfo(); //todo: TeamCity API: how to get unique mutes?
        if (currentMuteInfo != null) {
          muteInfos.addAll(new LinkedHashSet<MuteInfo>(currentMuteInfo.getProjectsMuteInfo().values())); //add with deduplication
          muteInfos.addAll(new LinkedHashSet<MuteInfo>(currentMuteInfo.getBuildTypeMuteInfo().values())); //add with deduplication
        }
        return new Mutes(muteInfos, null, fields.getNestedField("mutes", Fields.NONE, Fields.LONG), beanContext);
      }
    });

    investigations = ValueWithDefault.decideDefault(fields.isIncluded("investigations", false), new ValueWithDefault.Value<Investigations>() {
      public Investigations get() {
        return new Investigations(beanContext.getSingletonService(InvestigationFinder.class).getInvestigationWrappers(test),
                                  new PagerData(InvestigationRequest.getHref(test)), fields.getNestedField("investigations"),
                                  beanContext);
      }
    });

    testOccurrences = ValueWithDefault.decideDefault(fields.isIncluded("testOccurrences", false), new ValueWithDefault.Value<TestOccurrences>() {
      public TestOccurrences get() {
        //todo: add support for locator + filter here, like for builds in BuildType
        final Fields nestedFields = fields.getNestedField("testOccurrences");
        return new TestOccurrences(null, null, null, null, null, null, null, TestOccurrenceRequest.getHref(test), null, nestedFields, beanContext);
      }
    });
  }
}
