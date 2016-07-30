/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.parameters.impl.MapParametersProviderImpl;
import jetbrains.buildServer.server.rest.data.ParameterCondition;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.Converter;
import jetbrains.buildServer.util.filters.Filter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 25/02/2016
 */
@SuppressWarnings("PublicField")
@XmlRootElement(name = "links")
public class Links {
  @XmlAttribute
  public Integer count;

  @XmlElement(name = "link")
  public List<Link> links;

  public Links() {
  }

  public Links(@NotNull final Collection<LinkData> linksP, @NotNull final Fields fields) {
    this.links = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("link"), new ValueWithDefault.Value<List<Link>>() {
      @Nullable
      @Override
      public List<Link> get() {
        final Fields nestedField = fields.getNestedField("link", Fields.LONG, Fields.LONG);
        final ParameterCondition condition = ParameterCondition.create(fields.getLocator());
        return CollectionsUtil.filterAndConvertCollection(linksP, new Converter<Link, LinkData>() {
          @Override
          public Link createFrom(@NotNull final LinkData source) {
            return new Link(source.type, source.url, source.relativeUrl, nestedField);
          }
        }, new Filter<LinkData>() {
          @Override
          public boolean accept(@NotNull final LinkData data) {
            return data.matches(condition);
          }
        });
      }
    });
    this.count = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("count"), linksP.size());
  }

  public static LinksBuilder builder() {
    return new LinksBuilder();
  }

  public static class LinksBuilder {
    private final ArrayList<LinkData> datas = new ArrayList<>();

    public LinksBuilder add(@NotNull final String type, @NotNull final String url, @NotNull final String relativeUrl) {
      datas.add(new LinkData(type, url, relativeUrl));
      return this;
    }

    public Links build(@NotNull final Fields fields) {
      return new Links(datas, fields);
    }
  }

  public static class LinkData {
    @NotNull public String type;
    @NotNull public String url;
    @NotNull public String relativeUrl;

    public LinkData(@NotNull final String type, @NotNull final String url, @NotNull final String relativeUrl) {
      this.type = type;
      this.url = url;
      this.relativeUrl = relativeUrl;
    }

    public boolean matches(final ParameterCondition condition) {
      return condition == null || condition.matches(new MapParametersProviderImpl(CollectionsUtil.asMap("type", type, "url", url, "relativeUrl", relativeUrl)));
    }
  }
}