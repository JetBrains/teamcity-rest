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

package jetbrains.buildServer.server.rest.request;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.buildTriggers.scheduler.CronParseException;
import jetbrains.buildServer.controllers.FileSecurityUtil;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.metrics.ServerMetricsReader;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.DataProvider;
import jetbrains.buildServer.server.rest.data.PermissionChecker;
import jetbrains.buildServer.server.rest.data.finder.impl.BuildArtifactsFinder;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.InvalidStateException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.Util;
import jetbrains.buildServer.server.rest.model.metrics.Metrics;
import jetbrains.buildServer.server.rest.model.plugin.PluginInfos;
import jetbrains.buildServer.server.rest.model.server.CleanupSettings;
import jetbrains.buildServer.server.rest.model.server.*;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.BeanFactory;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.serverSide.cleanup.ServerCleanupManager;
import jetbrains.buildServer.serverSide.impl.MainConfigManager;
import jetbrains.buildServer.serverSide.maintenance.*;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.browser.Element;
import jetbrains.buildServer.web.util.WebUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Path(ServerRequest.API_SERVER_URL)
@Api("Server")
public class ServerRequest {

  public static final String SERVER_VERSION_RQUEST_PATH = "version";
  public static final String SERVER_REQUEST_PATH = "/server";
  public static final String API_SERVER_URL = Constants.API_URL + SERVER_REQUEST_PATH;

  protected static final String LICENSING_DATA = "/licensingData";
  protected static final String LICENSING_KEYS = LICENSING_DATA + "/licenseKeys";
  protected static final String CLEANUP = "/cleanup";

  @Context
  private DataProvider myDataProvider;
  @Context
  private ServiceLocator myServiceLocator;
  @Context
  private ApiUrlBuilder myApiUrlBuilder;
  @Context
  private BeanFactory myFactory;

  @SuppressWarnings("NullableProblems") @Context @NotNull
  private BeanContext myBeanContext;

  @SuppressWarnings("NullableProblems") @Context @NotNull
  private PermissionChecker myPermissionChecker;

  public void initForTests(
    @NotNull ServiceLocator serviceLocator,
    @NotNull ApiUrlBuilder apiUrlBuilder,
    @NotNull BeanFactory beanFactory,
    @NotNull BeanContext beanContext,
    @NotNull PermissionChecker permissionChecker) {
    myServiceLocator = serviceLocator;
    myApiUrlBuilder = apiUrlBuilder;
    myFactory = beanFactory;
    myBeanContext = beanContext;
    myPermissionChecker = permissionChecker;
  }

  public static String getLicenseKeysListHref() {
    return API_SERVER_URL + LICENSING_KEYS;
  }

  @GET
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value = "Get the server info.", nickname = "getServerInfo")
  public Server serveServerInfo(@QueryParam("fields") String fields) {
    return new Server(new Fields(fields), myServiceLocator, myApiUrlBuilder);
  }

  @GET
  @Path("/{field}")
  @Produces({"text/plain"})
  @ApiOperation(value = "Get a field of the server info.", nickname = "getServerField")
  public String serveServerVersion(@PathParam("field") String fieldName) {
    return Server.getFieldValue(fieldName, myServiceLocator);
  }

  @GET
  @Path("/plugins")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value = "Get all plugins.", nickname = "getAllPlugins")
  public PluginInfos servePlugins(@QueryParam("fields") String fields) {
    myDataProvider.checkGlobalPermission(Permission.VIEW_SERVER_SETTINGS);
    return new PluginInfos(myDataProvider.getPlugins(), new Fields(fields), myBeanContext);
  }

  @GET
  @Path("/metrics")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value = "Get metrics.", nickname = "getAllMetrics")
  public Metrics serveMetrics(@QueryParam("fields") String fields) {
    myDataProvider.checkGlobalPermission(Permission.VIEW_USAGE_STATISTICS);

    return new Metrics(new Fields(fields), myServiceLocator.findSingletonService(ServerMetricsReader.class));
  }

  /**
   * @param fileName               relative file name to save backup to (will be saved into
   *                               the default backup directory (<tt>.BuildServer/backup</tt>
   *                               if not overriden in main-config.xml)
   * @param addTimestamp           whether to add timestamp to the file or not
   * @param includeConfigs         whether to include configs into the backup or not
   * @param includeDatabase        whether to include database into the backup or not
   * @param includeBuildLogs       whether to include build logs into the backup or not
   * @param includePersonalChanges whether to include personal changes into the backup or not
   * @return the resulting file name that the backup will be saved to
   */
  @POST
  @Path("/backup")
  @Produces({"text/plain"})
  @ApiOperation(value = "Start a new backup.", nickname = "startBackup")
  public String startBackup(@QueryParam("fileName") String fileName,
                            @QueryParam("addTimestamp") Boolean addTimestamp,
                            @QueryParam("includeConfigs") Boolean includeConfigs,
                            @QueryParam("includeDatabase") Boolean includeDatabase,
                            @QueryParam("includeBuildLogs") Boolean includeBuildLogs,
                            @QueryParam("includePersonalChanges") Boolean includePersonalChanges,
                            @QueryParam("includeRunningBuilds") Boolean includeRunningBuilds,
                            @QueryParam("includeSupplimentaryData") Boolean includeSupplimentaryData) {
    BackupProcessManager backupManager = myServiceLocator.getSingletonService(BackupProcessManager.class);
    BackupConfig backupConfig = new BackupConfig();
    if (StringUtil.isNotEmpty(fileName)) {
      if (!TeamCityProperties.getBoolean("rest.request.server.backup.allowAnyTargetPath")) {
        File backupDir = new File(myDataProvider.getBean(ServerPaths.class).getBackupDir());
        try {
          FileSecurityUtil.checkInsideDirectory(FileUtil.resolvePath(backupDir, fileName), backupDir);
        } catch (Exception e) {
          //the message contains absolute paths
          if (myPermissionChecker.hasGlobalPermission(Permission.MANAGE_SERVER_INSTALLATION)) {
            throw e;
          }
          throw new BadRequestException("Target file name (" + fileName + ") should be relative path.", null);
        }
      }
      if (addTimestamp != null) {
        backupConfig.setFileName(fileName, addTimestamp);
      } else {
        backupConfig.setFileName(fileName);
      }
    } else {
      throw new BadRequestException("No target file name specified.", null);
    }

    if (includeConfigs != null) backupConfig.setIncludeConfiguration(includeConfigs);
    if (includeDatabase != null) backupConfig.setIncludeDatabase(includeDatabase);
    if (includeBuildLogs != null) backupConfig.setIncludeBuildLogs(includeBuildLogs);
    if (includePersonalChanges != null) backupConfig.setIncludePersonalChanges(includePersonalChanges);
    if (includeRunningBuilds != null) backupConfig.setIncludeRunningBuilds(includeRunningBuilds);
    if (includeSupplimentaryData != null) backupConfig.setIncludeSupplementaryData(includeSupplimentaryData);

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
  @ApiOperation(value = "Get the latest backup status.", nickname = "getBackupStatus")
  public String getBackupStatus() {
    MaintenanceLock maintenanceLock = myServiceLocator.getSingletonService(MaintenanceLock.class);
    MaintenanceLock.ProcessInfo process = maintenanceLock.getCurrentProcess();
    if (process == null || process.getKind() != MaintenanceProcessKind.Backup) return  "Idle";

    BackupProcessManager backupManager = myServiceLocator.getSingletonService(BackupProcessManager.class);
    BackupProcess backupProcess = backupManager.getCurrentBackupProcess();

    if (backupProcess != null) {
      return backupProcess.getProgressStatus().name();
    }

    // we don't know the actual stage if the backup is running on another node
    return ProgressStatus.Running.name();
  }

  /**
   * @return list server license keys
   */
  @GET
  @Path(LICENSING_DATA)
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value = "Get the licensing data.", nickname = "getLicensingData")
  public LicensingData getLicensingData(@QueryParam("fields") String fieldsText) {
    Fields fields = new Fields(fieldsText);
    return new LicensingData(myBeanContext.getSingletonService(BuildServerEx.class).getLicenseKeysManager(), fields, myBeanContext);
  }

  @GET
  @Path(LICENSING_KEYS)
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value = "Get all license keys.", nickname = "getLicenseKeys")
  public LicenseKeyEntities getLicenseKeys(@QueryParam("fields") String fields) {
    myDataProvider.checkGlobalPermission(Permission.VIEW_SERVER_SETTINGS);
    LicenseList licenseList = myBeanContext.getSingletonService(BuildServerEx.class).getLicenseKeysManager().getLicenseList();
    return new LicenseKeyEntities(licenseList.getAllLicenses(), licenseList.getActiveLicenses(), ServerRequest.getLicenseKeysListHref(), new Fields(fields), myBeanContext);
  }

  private static final Pattern DELIMITERS = Pattern.compile("[\\n\\r, ]");

  /**
   * Adds newline-delimited list of license keys to the server or returns textual description is there are not valid keys
   */
  @POST
  @Path(LICENSING_KEYS)
  @Consumes({"text/plain"})
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value = "Add license keys.", nickname = "addLicenseKeys")
  public LicenseKeyEntities addLicenseKeys(final String licenseKeyCodes, @QueryParam("fields") String fields) {
    myDataProvider.checkGlobalPermission(Permission.CHANGE_SERVER_SETTINGS);
    LicenseKeysManager licenseKeysManager = myBeanContext.getSingletonService(BuildServerEx.class).getLicenseKeysManager();
    List<String> keysToAdd = Stream.of(DELIMITERS.split(licenseKeyCodes)).map(String::trim).filter(s -> !StringUtil.isEmpty(s)).collect(Collectors.toList());
    List<LicenseKey> validatedKeys = licenseKeysManager.validateKeys(keysToAdd); //TeamCity API issue: why return good keys?
    if (!validatedKeys.isEmpty()) {
      // is there a way to return entity with not 200 result code???
      StringBuilder resultMessage = new StringBuilder();
      resultMessage.append("Invalid keys:\n");
      boolean invalidKeysFound = false;
      for (LicenseKey validatedKey : validatedKeys) {
        invalidKeysFound = invalidKeysFound || !validatedKey.isValid();
        String validateError = validatedKey.getValidateError();
        resultMessage.append(validatedKey.getKey()).append(" - ").append(validateError).append("\n");
      }
      if (invalidKeysFound) {
        throw new BadRequestException(resultMessage.toString());
      }
    }
    return new LicenseKeyEntities(licenseKeysManager.addKeys(keysToAdd), null, null, new Fields(fields), myBeanContext);
  }

  @GET
  @Path(LICENSING_KEYS + "/{licenseKey}")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value = "Get a license key.", nickname = "getLicenseKey")
  public LicenseKeyEntity getLicenseKey(@PathParam("licenseKey") final String licenseKey, @QueryParam("fields") String fields) {
    myDataProvider.checkGlobalPermission(Permission.VIEW_SERVER_SETTINGS);
    LicenseKeysManager licenseKeysManager = myBeanContext.getSingletonService(BuildServerEx.class).getLicenseKeysManager();
    LicenseKey key = getLicenseKey(licenseKey, licenseKeysManager);
    return new LicenseKeyEntity(key, licenseKeysManager.getLicenseList().getActiveLicenses().contains(key), new Fields(fields));
  }

  @DELETE
  @Path(LICENSING_KEYS + "/{licenseKey}")
  @ApiOperation(value = "Delete a license key.", nickname = "deleteLicenseKey")
  public void deleteLicenseKey(@PathParam("licenseKey") final String licenseKey) {
    myDataProvider.checkGlobalPermission(Permission.CHANGE_SERVER_SETTINGS);
    LicenseKeysManager licenseKeysManager = myBeanContext.getSingletonService(BuildServerEx.class).getLicenseKeysManager();
    getLicenseKey(licenseKey, licenseKeysManager);
    licenseKeysManager.removeKey(licenseKey);
  }

  @NotNull
  private static LicenseKey getLicenseKey(@NotNull final String licenseKey, @NotNull final LicenseKeysManager licenseKeysManager) {
    //todo: return actual license key data even if not added to the server
    LicenseList licenseList = licenseKeysManager.getLicenseList();
    for (LicenseKey license : licenseList.getAllLicenses()) {
      if (licenseKey.equals(license.getKey())) {
        return license;
      }
    }
    throw new NotFoundException("No license with key '" + licenseKey + "' is found");
  }

  @GET
  @Path(CLEANUP)
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value = "Get clean-up settings.", nickname = "getCleanupSettings")
  public CleanupSettings getCleanupSettings() {
    myPermissionChecker.checkGlobalPermission(Permission.VIEW_SERVER_SETTINGS);
    return new CleanupSettings(myBeanContext.getSingletonService(ServerCleanupManager.class));
  }

  @PUT
  @Path(CLEANUP)
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value = "Set clean-up settings.", nickname = "setCleanupSettings")
  public CleanupSettings setCleanupSettings(CleanupSettings cleanupSettings) {
    myPermissionChecker.checkGlobalPermission(Permission.CONFIGURE_SERVER_DATA_CLEANUP);
    ServerCleanupManager serverCleanupManager = myBeanContext.getSingletonService(ServerCleanupManager.class);

    CleanupDaily daily = cleanupSettings.daily;
    CleanupCron cron = cleanupSettings.cron;
    if (daily != null && cron != null) {
      throw new BadRequestException("Cannot set both daily and cron schedule at the same time");
    }
    try {
      if (daily != null) {
        serverCleanupManager.setCleanupStartCron("0 " + daily.minute + " " + daily.hour + " * * ?");
      }
      if (cron != null) {
        serverCleanupManager.setCleanupStartCron(
          "0 " +
          cron.minute + " " +
          cron.hour + " " +
          cron.day + " " +
          cron.month + " " +
          cron.dayWeek
        );
      }
    } catch (CronParseException e) {
      throw new BadRequestException("Incorrect cron expression");
    }

    Boolean enabled = cleanupSettings.enabled;
    Integer maxCleanupDuration = cleanupSettings.maxCleanupDuration;
    if (enabled != null) {
      serverCleanupManager.setCleanupEnabled(enabled);
    }
    if (maxCleanupDuration != null) {
      serverCleanupManager.setMaxCleanupDuration(maxCleanupDuration);
    }

    myBeanContext.getSingletonService(MainConfigManager.class).persistConfiguration();
    return new CleanupSettings(myBeanContext.getSingletonService(ServerCleanupManager.class));
  }

  @Path("/files/{areaId}")
  public FilesSubResource getFilesSubResource(@PathParam("areaId") final String areaId) {
    myPermissionChecker.checkGlobalPermission(getAreaPermission(areaId));
    final String urlPrefix = getUrlPrefix(areaId);

    return new FilesSubResource(new FilesSubResource.Provider() {
      @Override
      @NotNull
      public Element getElement(@NotNull final String path, @NotNull Purpose purpose) {
        return BuildArtifactsFinder.getItem(getAreaRoot(areaId), path, "server " + areaId, myBeanContext.getServiceLocator());
      }

      @Override
      @NotNull
      public String getArchiveName(@NotNull final String path) {
        String nodeIdPart = "";
        if (!CurrentNodeInfo.isMainNode()) { //assuming there is only single main server and it does not need node id in the file name
          nodeIdPart = "_" + CurrentNodeInfo.getNodeId().toLowerCase();
        }
        return "server_" + nodeIdPart + areaId + (StringUtil.isEmpty(path) ? "" : "-" + path.replaceAll("[^a-zA-Z0-9-#.]+", "_"));
      }

      @NotNull
      @Override
      public String preprocess(@Nullable final String path) {
        String result = super.preprocess(path);
        result = StringUtil.replace(result, "%timestamp%", new SimpleDateFormat("yyyy-MM-dd_HHmm").format(new Date()));
        return result;
      }

      @Override
      public boolean fileContentServed(@Nullable final String path, @NotNull final HttpServletRequest request) {
        Loggers.AUTH.info("Served file \"" + path + "\" from server's \"" + areaId + "\" for request " + WebUtil.getRequestDump(request));
        return super.fileContentServed(path, request);
      }
    }, urlPrefix, myBeanContext, false);
  }


  @NotNull
  private String getUrlPrefix(final String areaId) {
    return Util.concatenatePath(myBeanContext.getApiUrlBuilder().transformRelativePath(API_SERVER_URL), "/files/", areaId);
  }

  @NotNull
  private File getAreaRoot(final @PathParam("areaId") String areaId) {
    File rootPath;
    if ("logs".equals(areaId)) {
      rootPath = myDataProvider.getBean(ServerPaths.class).getLogsPath();
    } else if ("backups".equals(areaId)) {
      rootPath = new File(myDataProvider.getBean(ServerPaths.class).getBackupDir());
    } else if ("dataDirectory".equals(areaId)) {
      rootPath = myDataProvider.getBean(ServerPaths.class).getDataDirectory();
    } else if (Boolean.getBoolean("teamcity.unrestrictedOsAccess.enabled") //only if defined via -D so that it is not possible to turn on via internal properties
               && !StringUtil.isEmpty(areaId) && areaId.startsWith("custom.")) {
      final String customAreaId = areaId.substring("custom.".length());
      rootPath = new File(TeamCityProperties.getProperty("rest.request.server.files.customArea." + customAreaId));
    } else {
      throw new BadRequestException("Unknown area id '" + areaId + "'. Known are: " + "logs, backups, dataDirectory");
    }
    return rootPath;
  }

  @NotNull
  private Permission getAreaPermission(final @PathParam("areaId") String areaId) {
    return "logs".equals(areaId) ? Permission.MANAGE_SERVER_INSTALLATION : Permission.VIEW_SERVER_SETTINGS;
  }

}
