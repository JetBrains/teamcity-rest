/*
 * Copyright 2000-2018 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.model.files;

import java.util.Date;
import java.util.List;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.data.BuildArtifactsFinder;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.Href;
import jetbrains.buildServer.server.rest.model.Util;
import jetbrains.buildServer.server.rest.request.FilesSubResource;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.util.browser.Element;
import jetbrains.buildServer.web.artifacts.browser.ArtifactTreeElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Vladislav.Rassokhin
 */
@SuppressWarnings("PublicField")
@XmlRootElement(name = "file")
@XmlType(name = "file", propOrder = {"name", "fullName", "size", "modificationTime", "href",
"parent", "content", "children"})
public class File {

  @NotNull private final Fields myFields;
  @NotNull private final BeanContext myBeanContext;
  @XmlAttribute public String name;
  @XmlAttribute public String fullName;
  @XmlAttribute public String href;

  protected final FileApiUrlBuilder fileApiUrlBuilder;
  protected final Element parent;
  protected final ArtifactTreeElement element;

  @SuppressWarnings({"UnusedDeclaration", "ConstantConditions"})
  public File() {
    fileApiUrlBuilder = null;
    parent = null;
    element = null;
    myFields = null;
    myBeanContext = null;
  }

  public File(@NotNull final Element element, @Nullable final Element parent, @NotNull final FileApiUrlBuilder builder, @NotNull Fields fields, @NotNull final BeanContext beanContext) {
    myFields = fields;
    myBeanContext = beanContext;
    this.href = ValueWithDefault.decideDefault(fields.isIncluded("href", true), beanContext.getApiUrlBuilder().transformRelativePath(builder.getMetadataHref(element)));
    this.name = ValueWithDefault.decideDefault(fields.isIncluded("name", true), element.getName());
    this.fullName = ValueWithDefault.decideDefault(fields.isIncluded("fullName", false, false), element.getFullName());

    this.element = new BuildArtifactsFinder.ArtifactTreeElementWrapper(element);
    this.parent = parent;

    this.fileApiUrlBuilder = builder;
  }

  @Nullable
  @XmlAttribute(name = "size")
  public Long getSize() {
    if (element == null || !element.isContentAvailable()) {
      return null;
    } else {
      return ValueWithDefault.decideDefault(myFields.isIncluded("size", false), new ValueWithDefault.Value<Long>() {
        @Nullable
        public Long get() {
          final long size = element.getSize();
          return size > 0 ? size : null;
        }
      });
    }
  }

  @Nullable
  @XmlAttribute(name = "modificationTime")
  public String getModificationTime() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("modificationTime", false), new ValueWithDefault.Value<String>() {
      @Nullable
      public String get() {
        final Long lastModified = element != null ? element.getLastModified() : null;
        return lastModified == null ? null : Util.formatTime(new Date(lastModified));
      }
    });
  }

  @Nullable
  @XmlElement(name = "parent")
  public File getParent() {
    return parent == null ? null : ValueWithDefault.decideDefault(myFields.isIncluded("parent", false), new ValueWithDefault.Value<File>() {
      @Nullable
      public File get() {
        return new File(parent, null, fileApiUrlBuilder, myFields.getNestedField("parent",Fields.SHORT, Fields.SHORT), myBeanContext);
      }
    });
  }

  @Nullable
  @XmlElement(name = "content")
  public Href getContent() {
    if (element == null || !element.isContentAvailable()) {
      return null;
    }
    return ValueWithDefault.decideDefault(myFields.isIncluded("content", false), new Href(fileApiUrlBuilder.getContentHref(element), myBeanContext.getApiUrlBuilder()));
  }

  @Nullable
  @XmlElement(name = "children")
  public Files getChildren() {
    if (element == null || element.isLeaf()) {
      return null;
    }
    return ValueWithDefault.decideDefaultIgnoringAccessDenied(myFields.isIncluded("children", false), new ValueWithDefault.Value<Files>() {
      @Nullable
      public Files get() {
        final Fields nestedFields = myFields.getNestedField("children");
        final FileApiUrlBuilder builder = FilesSubResource.fileApiUrlBuilder(nestedFields.getLocator(), fileApiUrlBuilder.getUrlPathPrefix());
        return new Files(builder.getChildrenHref(element), new Files.DefaultFilesProvider(builder, myBeanContext) {
          @NotNull
          @Override
          protected List<? extends Element> getItems() {
            return BuildArtifactsFinder.getItems(element, nestedFields.getLocator(), builder, myBeanContext.getServiceLocator());
          }
        }, nestedFields, myBeanContext);
      }
    });
  }
}
