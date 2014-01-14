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

package jetbrains.buildServer.server.rest.request;

import com.sun.jersey.api.core.InjectParam;
import java.io.File;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.DataProvider;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.InvalidStateException;
import jetbrains.buildServer.server.rest.model.plugin.PluginInfos;
import jetbrains.buildServer.server.rest.model.server.Server;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.BeanFactory;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.serverSide.maintenance.BackupConfig;
import jetbrains.buildServer.serverSide.maintenance.BackupProcess;
import jetbrains.buildServer.serverSide.maintenance.BackupProcessManager;
import jetbrains.buildServer.serverSide.maintenance.MaintenanceProcessAlreadyRunningException;
import jetbrains.buildServer.util.StringUtil;

/*
 * User: Yegor Yarko
 * Date: 11.04.2009
 */
@Path(ServerRequest.API_SERVER_URL)
public class ServerRequest {
  public static final String SERVER_VERSION_RQUEST_PATH = "version";
  public static final String API_SERVER_URL = Constants.API_URL + "/server";
  @Context
  private DataProvider myDataProvider;
  @Context
  private ServiceLocator myServiceLocator;
  @Context
  private ApiUrlBuilder myApiUrlBuilder;
  @Context
  private BeanFactory myFactory;

  @GET
  @Produces({"application/xml", "application/json"})
  public Server serveServerInfo() {
    return new Server(new BeanContext(myFactory, myServiceLocator, myApiUrlBuilder));
  }

  @GET
  @Path("/{field}")
  @Produces({"text/plain"})
  public String serveServerVersion(@PathParam("field") String fieldName) {
    return Server.getFieldValue(fieldName, myServiceLocator);
  }

  @GET
  @Path("/plugins")
  @Produces({"application/xml", "application/json"})
  public PluginInfos servePlugins() {
    myDataProvider.checkGlobalPermission(Permission.CHANGE_SERVER_SETTINGS);
    return new PluginInfos(myDataProvider.getPlugins());
  }

  /**
   *
   * @param fileName relative file name to save backup to (will be saved into
   *                 the default backup directory (<tt>.BuildServer/backup</tt>
   *                 if not overriden in main-config.xml)
   * @param addTimestamp whether to add timestamp to the file or not
   * @param includeConfigs whether to include configs into the backup or not
   * @param includeDatabase whether to include database into the backup or not
   * @param includeBuildLogs whether to include build logs into the backup or not
   * @param includePersonalChanges whether to include personal changes into the backup or not
   * @return the resulting file name that the backup will be saved to
   */
  @POST
  @Path("/backup")
  @Produces({"text/plain"})
  public String startBackup(@QueryParam("fileName") String fileName,
                            @QueryParam("addTimestamp") Boolean addTimestamp,
                            @QueryParam("includeConfigs") Boolean includeConfigs,
                            @QueryParam("includeDatabase") Boolean includeDatabase,
                            @QueryParam("includeBuildLogs") Boolean includeBuildLogs,
                            @QueryParam("includePersonalChanges") Boolean includePersonalChanges,
                            @InjectParam BackupProcessManager backupManager) {
    BackupConfig backupConfig = new BackupConfig();
    if (StringUtil.isNotEmpty(fileName)) {
      if (new File(fileName).isAbsolute()){
        throw new BadRequestException("Target file name should be relative path.", null);
      }
      if (addTimestamp != null) {
        backupConfig.setFileName(fileName, addTimestamp);
      } else {
        backupConfig.setFileName(fileName);
      }
    }else{
      throw new BadRequestException("No target file name specified.", null);
    }

    if (includeConfigs != null) backupConfig.setIncludeConfiguration(includeConfigs);
    if (includeDatabase != null) backupConfig.setIncludeDatabase(includeDatabase);
    if (includeBuildLogs != null) backupConfig.setIncludeBuildLogs(includeBuildLogs);
    if (includePersonalChanges != null) backupConfig.setIncludePersonalChanges(includePersonalChanges);

    try {
      backupManager.startBackup(backupConfig);
    } catch (MaintenanceProcessAlreadyRunningException e) {
      throw new InvalidStateException("Cannot start backup because another maintenance process is in progress", e);
    }
    return backupConfig.getResultFileName();
  }

  /**
   * @return current backup status
   */
  @GET
  @Path("/backup")
  @Produces({"text/plain"})
  public String getBackupStatus(@InjectParam BackupProcessManager backupManager) {
    final BackupProcess backupProcess = backupManager.getCurrentBackupProcess();
    if (backupProcess == null) {
      return "Idle";
    }
    return backupProcess.getProgressStatus().name();
  }
}
