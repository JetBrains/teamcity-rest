/*
 * Copyright 2000-2019 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.model.server;

import java.util.List;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Entity for list of server statistics.
 *
 * @author Mikhail Khorkov
 * @since 2019.1
 */
@SuppressWarnings("PublicField")
@XmlRootElement(name = "statistics-list")
public class StatisticsList {
  @XmlElement(name = "items")
  public List<Statistics> items;
  @XmlElement(name = "count")
  public Integer count;

  public StatisticsList() {
  }

  public StatisticsList(final List<Statistics> items) {
    this.items = items;
    this.count = items.size();
  }
}
