/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import java.util.Date;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.server.rest.data.DataProvider;
import jetbrains.buildServer.server.rest.model.Util;
import jetbrains.buildServer.server.rest.util.BeanFactory;
import jetbrains.buildServer.serverSide.SBuildServer;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author Yegor.Yarko
 *         Date: 17.11.2009
 */
@XmlRootElement(name = "server")
public class Server {
  @Autowired
  private SBuildServer myServer;
  @Autowired
  private DataProvider myDataProvider;

  public Server() {
  }

  public Server(final BeanFactory myFactory) {
    myFactory.autowire(this);
  }

  @XmlAttribute
  public String getVersion() {
    return myServer.getFullServerVersion();
  }

  @XmlAttribute
  public byte getVersionMajor() {
    return myServer.getServerMajorVersion();
  }

  @XmlAttribute
  public byte getVersionMinor() {
    return myServer.getServerMinorVersion();
  }

  @XmlAttribute
  public String getBuildNumber() {
    return myServer.getBuildNumber();
  }

  @XmlAttribute
  public String getStartTime() {
    return Util.formatTime(myDataProvider.getServerStartTime());
  }

  @XmlAttribute
  public String getCurrentTime() {
    return Util.formatTime(new Date());
  }
}
