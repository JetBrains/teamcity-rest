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

package jetbrains.buildServer.server.rest.swagger;

import com.intellij.openapi.diagnostic.Logger;
import io.swagger.config.SwaggerConfig;
import io.swagger.models.Info;
import io.swagger.models.Swagger;
import java.net.MalformedURLException;
import java.net.URL;
import jetbrains.buildServer.server.rest.data.DataProvider;
import jetbrains.buildServer.serverSide.SBuildServer;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;

public class SwaggerConfigImpl implements SwaggerConfig {
  private final DataProvider myDataProvider;
  private static final Logger LOG = Logger.getInstance(SwaggerConfigImpl.class.getName());

  public SwaggerConfigImpl(@NotNull final DataProvider dataProvider) {
    myDataProvider = dataProvider;
  }

  public Swagger configure(final Swagger swagger) {
    if (swagger == null) return null;

    Info info = swagger.getInfo();
    if (info == null) {
      info = new Info();
    }

    final SBuildServer server = myDataProvider.getServer();
    try {
      final URL url = new URL(server.getRootUrl());
      swagger.setHost(getHostAndPort(url));
      swagger.setBasePath(url.getPath());
    } catch (MalformedURLException e) {
      LOG.warnAndDebugDetails("Failed to configure swagger with server url", e);
    }

    info.setTitle("TeamCity REST API");

    final String version = myDataProvider.getPluginInfo().getParameterValue("api.version");
    if (!StringUtil.isEmptyOrSpaces(version)) {
      info.setVersion(version);
    } else {
      info.setVersion("" + server.getVersion().getDisplayVersionMajor() + "." + server.getVersion().getDisplayVersionMinor());
    }

    swagger.setInfo(info);
    return swagger;
  }

  public String getFilterClass() {
    return null;
  }

  private static String getHostAndPort(final URL url) {
    final StringBuilder builder = new StringBuilder();
    builder.append(url.getHost());
    final int port = url.getPort();
    if (port != -1) {
      builder.append(':').append(port);
    }
    return builder.toString();
  }
}
