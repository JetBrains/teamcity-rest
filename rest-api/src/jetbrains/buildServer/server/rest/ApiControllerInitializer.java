package jetbrains.buildServer.server.rest;


import com.intellij.openapi.diagnostic.Logger;
import java.util.*;
import java.util.stream.Collectors;
import jetbrains.buildServer.controllers.AuthorizationInterceptor;
import jetbrains.buildServer.plugins.PluginManager;
import jetbrains.buildServer.plugins.bean.PluginInfo;
import jetbrains.buildServer.plugins.bean.ServerPluginInfo;
import jetbrains.buildServer.server.rest.jersey.JerseyWebComponentInitializer;
import jetbrains.buildServer.server.rest.request.Constants;
import jetbrains.buildServer.server.rest.util.PluginUtil;
import jetbrains.buildServer.serverSide.BuildServerAdapter;
import jetbrains.buildServer.serverSide.SBuildServer;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.openapi.WebControllerManager;
import jetbrains.buildServer.web.util.WebUtil;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import static jetbrains.buildServer.server.rest.APIController.PATHS_WITHOUT_AUTH;

/**
 * This class is responsible for collecting bind paths and initializing APIController with them. We need this
 * to be able to pre-route requests to APIController before Jersey kicks in with it's internal routing.
 */
@Component
public final class ApiControllerInitializer extends BuildServerAdapter {
  public static final String REST_PREFER_OWN_BIND_PATHS = "rest.allow.bind.paths.override.for.plugins";
  public static final String LATEST_REST_API_PLUGIN_NAME = "rest-api";
  private final Logger LOG;
  private final boolean myInternalAuthProcessing = TeamCityProperties.getBoolean("rest.cors.optionsRequest.allowUnauthorized");
  private final APIController myController;
  private final PluginDescriptor myPluginDescriptor;
  private final WebControllerManager myWebControllerManager;
  private final PluginManager myPluginManager;
  private final AuthorizationInterceptor myAuthorizationInterceptor;
  private final RequestPathTransformInfo myRequestPathTransformInfo;
  private final JerseyWebComponentInitializer myWebControllerInitiailizer;

  public ApiControllerInitializer(
    @NotNull SBuildServer server,
    @NotNull APIController controller,
    @NotNull PluginDescriptor pluginDescriptor,
    @NotNull PluginManager pluginManager,
    @NotNull WebControllerManager webControllerManager,
    @NotNull AuthorizationInterceptor authorizationInterceptor,
    @NotNull RequestPathTransformInfo requestPathTransformInfo,
    @NotNull JerseyWebComponentInitializer jerseyWebComponentInitializer
  ) {
    myController = controller;
    myPluginDescriptor = pluginDescriptor;
    myPluginManager = pluginManager;
    myWebControllerManager = webControllerManager;
    myAuthorizationInterceptor = authorizationInterceptor;
    myRequestPathTransformInfo = requestPathTransformInfo;
    myWebControllerInitiailizer = jerseyWebComponentInitializer;

    LOG = PluginUtil.getLoggerWithPluginName(ApiControllerInitializer.class, myPluginDescriptor);
    server.addListener(this);
  }

  @Override
  public void pluginsLoaded() {
    initializeController();
  }

  private void initializeController() {
    final List<String> unfilteredOriginalBindPaths = getBindPaths();
    if (unfilteredOriginalBindPaths.isEmpty()) {
      final String message = "Error while initializing " + PluginUtil.getIdentifyingText(myPluginDescriptor) + ": No bind paths found. Corrupted plugin?";
      LOG.error(message + " Reporting plugin load error.");
      throw new RuntimeException(message);
    }

    final List<String> originalBindPaths = filterOtherPlugins(unfilteredOriginalBindPaths);
    if (originalBindPaths.isEmpty()) {
      final String message = "Error while initializing " + PluginUtil.getIdentifyingText(myPluginDescriptor) + ": No unique bind paths found. Conflicting plugins set is installed.";
      LOG.error(message + " Reporting plugin load error.");
      throw new RuntimeException(message);
    }

    LOG.info("Listening for paths " + originalBindPaths + " in " + PluginUtil.getIdentifyingText(myPluginDescriptor));

    // For GraphQL API we don't want path-related features that we have for REST paths.
    List<String> graphqlPaths = originalBindPaths.stream().filter(path -> path.startsWith(Constants.GRAPHQL_API_URL)).collect(Collectors.toList());
    originalBindPaths.removeAll(graphqlPaths);

    List<String> bindPaths = new ArrayList<>(originalBindPaths);
    bindPaths.addAll(addPrefix(originalBindPaths, StringUtil.removeTailingSlash(WebUtil.HTTP_AUTH_PREFIX)));
    bindPaths.addAll(addPrefix(originalBindPaths, StringUtil.removeTailingSlash(WebUtil.GUEST_AUTH_PREFIX)));

    Map<String, String> transformBindPaths = new HashMap<>();
    bindPaths.forEach(bindPath -> transformBindPaths.put(bindPath, Constants.API_URL));

    myRequestPathTransformInfo.setPathMapping(transformBindPaths);
    LOG.debug("Will use request mapping: " + myRequestPathTransformInfo);

    originalBindPaths.addAll(graphqlPaths);
    registerController(originalBindPaths);

    if (LATEST_REST_API_PLUGIN_NAME.equals(myPluginDescriptor.getPluginName())) {
      //initialize on start only the latest plugin; others will be initialized on first request
      myWebControllerInitiailizer.initJerseyWebComponentAsync();
    }
  }

  private void registerController(final List<String> bindPaths) {
    try {
      Set<String> unauthenticatedPaths = new HashSet<>();
      for (String controllerBindPath : bindPaths) {
        LOG.debug("Binding " + PluginUtil.getIdentifyingText(myPluginDescriptor) + " to path '" + controllerBindPath + "'");
        myWebControllerManager.registerController(controllerBindPath + "/**", myController);
        if (myInternalAuthProcessing && !controllerBindPath.equals(Constants.API_URL)) {
          // this is a special case as it contains paths of other plugins under it. Thus, it cannot be registered as not requiring auth
          myAuthorizationInterceptor.addPathNotRequiringAuth(APIController.class, controllerBindPath + "/**");
          for (String path : PATHS_WITHOUT_AUTH) {
            unauthenticatedPaths.add(controllerBindPath + path);
          }
        } else {
          for (String path : PATHS_WITHOUT_AUTH) {
            myAuthorizationInterceptor.addPathNotRequiringAuth(APIController.class, controllerBindPath + path);
            unauthenticatedPaths.add(controllerBindPath + path);
          }
        }
      }

      myController.setUnathenticatedPaths(unauthenticatedPaths);
    } catch (Exception e) {
      LOG.error("Error registering controller in " + PluginUtil.getIdentifyingText(myPluginDescriptor), e);
    }
  }

  @NotNull
  public List<String> getBindPaths() {
    String bindPath = myPluginDescriptor.getParameterValue(Constants.BIND_PATH_PROPERTY_NAME);
    if (bindPath == null) {
      LOG.error("No property '" + Constants.BIND_PATH_PROPERTY_NAME + "' found in plugin descriptor file in " + PluginUtil.getIdentifyingText(myPluginDescriptor) + ". Corrupted plugin?");
      return Collections.emptyList();
    }

    final String[] bindPaths = bindPath.split(",");

    if (bindPath.length() == 0) {
      LOG.error("Invalid bind path in plugin descriptor: '" + bindPath + "' in " + PluginUtil.getIdentifyingText(myPluginDescriptor) + ". Corrupted plugin?");
      return Collections.emptyList();
    }

    return Arrays.asList(bindPaths);
  }

  @NotNull
  private List<String> filterOtherPlugins(final List<String> bindPaths) {
    //by default allow only the latest/main plugin paths to be overriden
    final String pluginNames = TeamCityProperties.getProperty(REST_PREFER_OWN_BIND_PATHS, LATEST_REST_API_PLUGIN_NAME);
    final String[] pluginNamesList = pluginNames.split(",");

    final String ownPluginName = myPluginDescriptor.getPluginName();
    boolean overridesAllowed = false;
    for (String pluginName : pluginNamesList) {
      if (ownPluginName.equals(pluginName.trim())) {
        overridesAllowed = true;
        break;
      }
    }
    if (!overridesAllowed) {
      return bindPaths;
    }
    final ArrayList<String> result = new ArrayList<>(bindPaths);

    final Collection<PluginInfo> allPlugins = myPluginManager.getDetectedPlugins();
    //is the plugin actually loaded? Might need to check only the successfully loaded plugins
    for (PluginInfo plugin : allPlugins) {
      if (ownPluginName.equals(plugin.getPluginName())) {
        continue;
      }
      if (plugin instanceof ServerPluginInfo) {
        final PluginDescriptor pluginDescriptor = (ServerPluginInfo)plugin; //TeamCity API issue: cast
        String bindPath = pluginDescriptor.getParameterValue(Constants.BIND_PATH_PROPERTY_NAME);
        if (!StringUtil.isEmpty(bindPath)) {
          final List<String> pathToExclude = getBindPaths();
          if (result.removeAll(pathToExclude)) {
            LOG.info("Excluding paths from handling by plugin '" + ownPluginName + "' as they are handled by plugin '" + plugin.getPluginName() + "': " +
                     pathToExclude + ". Set " + REST_PREFER_OWN_BIND_PATHS + " internal property to empty value to prohibit overriding." +
                     " (The property sets comma-separated list of plugin names which bind paths can be overriden.)");
          }
        }
      } else {
        LOG.warn("Cannot get plugin info for plugin '" + plugin.getPluginName() + "', ignoring it while filtering out REST bind paths. " +
                 "This is a bug in the REST plugin, please report it to JetBrains.");
      }
    }
    return result;
  }

  private static List<String> addPrefix(final List<String> paths, final String prefix) {
    List<String> result = new ArrayList<>(paths.size());
    for (String path : paths) {
      result.add(prefix + path);
    }
    return result;
  }
}
