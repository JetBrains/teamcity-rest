/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.model.Href;
import jetbrains.buildServer.server.rest.model.Util;
import jetbrains.buildServer.util.browser.Element;
import jetbrains.buildServer.web.artifacts.browser.ArtifactTreeElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Vladislav.Rassokhin
 */
@XmlRootElement(name = "file")
@XmlType(name = "file", propOrder = {"size", "modificationTime",
"parent", "content", "children"})
public class File extends FileRef {

  protected final FileApiUrlBuilder fileApiUrlBuilder;
  protected final Element parent;
  protected final Element element;
  protected final Long lastModified;

  @SuppressWarnings("UnusedDeclaration")
  public File() {
    fileApiUrlBuilder = null;
    parent = null;
    element = null;
    lastModified = null;
  }

  public File(@NotNull final ArtifactTreeElement element, @Nullable final Element parent, @NotNull final FileApiUrlBuilder builder) {
    super(element, builder);
    this.element = element;
    final Long actualLastModified = element.getLastModified();
    this.lastModified = (actualLastModified != null && actualLastModified > 0) ? actualLastModified : null;

    this.fileApiUrlBuilder = builder;
    this.parent = parent;
  }


  public File(@NotNull final Element element, @Nullable final Long lastModified, @Nullable final Element parent, @NotNull final FileApiUrlBuilder builder) {
    super(element, builder);
    this.element = element;
    this.lastModified = lastModified;
    this.fileApiUrlBuilder = builder;
    this.parent = parent;
  }

  @Nullable
  @XmlAttribute(name = "size")
  public Long getSize() {
    if (!element.isContentAvailable()) {
      return null;
    } else {
      final long size = element.getSize();
      return size > 0 ? size : null;
    }
  }

  @Nullable
  @XmlAttribute(name = "modificationTime")
  public String getModificationTime() {
    return lastModified == null ? null : Util.formatTime(new Date(lastModified));
  }

  @Nullable
  @XmlElement(name = "parent")
  public FileRef getParent() {
    return parent != null ? new FileRef(parent, fileApiUrlBuilder) : null;
  }

  @Nullable
  @XmlElement(name = "content")
  public Href getContent() {
    if (!element.isContentAvailable()) {
      return null;
    }
    return new Href(fileApiUrlBuilder.getContentHref(element));
  }

  @Nullable
  @XmlElement(name = "children")
  public Href getChildren() {
    if (element.isLeaf()) {
      return null;
    }
    return new Href(fileApiUrlBuilder.getChildrenHref(element));
  }
}
