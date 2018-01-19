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

import java.util.List;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.Converter;
import jetbrains.buildServer.util.browser.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Vladislav.Rassokhin
 * @since 8.0
 */
@SuppressWarnings({"UnusedDeclaration", "PublicField"})
@XmlRootElement(name = "files")
@XmlType
public class Files {

  @XmlAttribute public Integer count;
  @XmlAttribute(name = "href") public String href;

  public static final String FILE = "file";
  @Nullable
  @XmlElement(name = FILE)
  public List<File> files;

  public Files() {
  }

  public Files(@Nullable final String shortHref, @Nullable final FilesProvider filesP, @NotNull final Fields fields, @NotNull final BeanContext beanContext) {
    href = shortHref == null ? null : ValueWithDefault.decideDefault(fields.isIncluded("href", true), beanContext.getApiUrlBuilder().transformRelativePath(shortHref));
    if (filesP != null) {
      files = ValueWithDefault.decideDefault(fields.isIncluded(FILE, false, true), new ValueWithDefault.Value<List<File>>() {
        public List<File> get() {
          return filesP.getFiles(fields.getNestedField(FILE, Fields.SHORT, Fields.LONG));
        }
      });

      boolean countIsCheap = filesP.isCountCheap();
      count = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("count", countIsCheap, countIsCheap, true), () -> filesP.getCount());
    }
  }

  public interface FilesProvider {
    @NotNull
    List<File> getFiles(@NotNull final Fields fields);

    int getCount();

    /**
     * @return true if getCount() method is cheap in terms of performance
     */
    boolean isCountCheap();
  }

  public static abstract class DefaultFilesProvider implements FilesProvider {
    @NotNull private final FileApiUrlBuilder myBuilder;
    @NotNull private final BeanContext myBeanContext;

    @Nullable protected List<? extends Element> myItems;

    public DefaultFilesProvider(@NotNull final FileApiUrlBuilder builder, @NotNull final BeanContext beanContext) {
      myBuilder = builder;
      myBeanContext = beanContext;
    }

    @NotNull
    abstract protected List<? extends Element> getItems();

    @Override
    @NotNull
    public List<File> getFiles(@NotNull final Fields fields) {
      if (myItems == null) {
        myItems = getItems();
      }
      return Files.toFiles(myItems, myBuilder, fields, myBeanContext);
    }

    @Override
    public int getCount() {
      if (myItems == null) {
        myItems = getItems();
      }
      return myItems.size();
    }

    @Override
    public boolean isCountCheap() {
      return myItems != null;
    }
  }

  @NotNull
  static List<File> toFiles(final List<? extends Element> source, final @NotNull FileApiUrlBuilder builder, final @NotNull Fields fields, final @NotNull BeanContext beanContext) {
    return CollectionsUtil.convertCollection(source, new Converter<File, Element>() {
      public File createFrom(@NotNull final Element source) {
        return new File(source, null, builder, fields, beanContext);
      }
    });
  }
}
