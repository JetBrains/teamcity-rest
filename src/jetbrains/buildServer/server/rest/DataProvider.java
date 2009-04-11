/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.MultiValuesMap;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.List;
import java.util.ArrayList;

import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.auth.Role;
import jetbrains.buildServer.serverSide.auth.RoleScope;
import jetbrains.buildServer.serverSide.auth.RolesManager;
import jetbrains.buildServer.util.ItemProcessor;
import jetbrains.buildServer.users.User;
import jetbrains.buildServer.users.UserModel;
import jetbrains.buildServer.users.SUser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: Yegor Yarko
 * Date: 28.03.2009
 */
public class DataProvider {
  private static final Logger LOG = Logger.getInstance(DataProvider.class.getName());

  private SBuildServer myServer;
  private BuildHistory myBuildHistory;
  private UserModel myUserModel;
  private RolesManager myRolesManager;
  private static final String DIMENSION_NAME_VALUE_DELIMITER = ":";
  private static final String DIMENSIONS_DELIMITER = ";";

  public DataProvider(SBuildServer myServer,
                      BuildHistory myBuildHistory,
                      UserModel userModel,
                      final RolesManager rolesManager) {
    this.myServer = myServer;
    this.myBuildHistory = myBuildHistory;
    this.myUserModel = userModel;
    myRolesManager = rolesManager;
  }

  @Nullable
  public String getFieldValue(final SBuildType buildType, final String field) {
    if ("id".equals(field)) {
      return buildType.getBuildTypeId();
    } else if ("description".equals(field)) {
      return buildType.getDescription();
    } else if ("name".equals(field)) {
      return buildType.getName();
    }
    throw new NotFoundException("Field '" + field + "' is not supported.");
  }

  @Nullable
  public String getFieldValue(final SProject project, final String field) {
    if ("id".equals(field)) {
      return project.getProjectId();
    } else if ("description".equals(field)) {
      return project.getDescription();
    } else if ("name".equals(field)) {
      return project.getName();
    }
    throw new NotFoundException("Field '" + field + "' is not supported.");
  }

  @Nullable
  public String getFieldValue(@NotNull final SBuild build, @Nullable final String field) {
    if ("number".equals(field)) {
      return build.getBuildNumber();
    } else if ("status".equals(field)) {
      return build.getStatusDescriptor().getStatus().getText();
    } else if ("id".equals(field)) {
      return (new Long(build.getBuildId())).toString();
    } else if ("startDate".equals(field)) {
      return (new SimpleDateFormat("yyyyMMdd'T'HHmmssZ")).format(build.getStartDate());
    } else if ("finishDate".equals(field)) {
      return (new SimpleDateFormat("yyyyMMdd'T'HHmmssZ")).format(build.getFinishDate());
    } else if ("buildTypeId".equals(field)) {
      return (build.getBuildTypeId());
    }
    throw new NotFoundException("Field '" + field + "' is not supported.");
  }

  @Nullable
  public String getServerFieldValue(@Nullable final String field) {
    if ("version".equals(field)) {
      return myServer.getFullServerVersion();
    } else if ("build".equals(field)) {
      return myServer.getBuildNumber();
    } else if ("majorVersion".equals(field)) {
      return Byte.toString(myServer.getServerMajorVersion());
    } else if ("minorVersion".equals(field)) {
      return Byte.toString(myServer.getServerMinorVersion());
    }
    throw new NotFoundException("Field '" + field + "' is not supported.");
  }


  @NotNull
  public SBuild getBuild(@Nullable final SBuildType buildType, @Nullable final String buildLocator) {
    if (buildLocator == null) {
      throw new BadRequestException("Empty build locator is not supported.");
    }

    if (!hasDimensions(buildLocator)) {
      // no dimensions found, assume it's a number
      if (buildType == null) {
        throw new BadRequestException("Cannot find build by number '" + buildLocator + "' without build type specified.");
      }
      SBuild build = myServer.findBuildInstanceByBuildNumber(buildType.getBuildTypeId(), buildLocator);
      if (build == null) {
        throw new NotFoundException("No build can be found by number '" + buildLocator + "' in build configuration " + buildType + ".");
      }
      return build;
    }

    MultiValuesMap<String, String> buildLocatorDimensions = decodeLocator(buildLocator);

    String idString = getSingleDimensionValue(buildLocatorDimensions, "id");
    if (idString != null) {
      Long id;
      try {
        id = Long.parseLong(idString);
      } catch (NumberFormatException e) {
        throw new BadRequestException("Invalid build id '" + idString + "'. Should be a number.");
      }
      SBuild build = myServer.findBuildInstanceById(id);
      if (build == null) {
        throw new NotFoundException("No build can be found by id '" + id + "'.");
      }
      if (buildType != null && !buildType.getBuildTypeId().equals(build.getBuildTypeId())) {
        throw new NotFoundException("No build can be found by id '" + id + "' in build type " + buildType + ".");
      }
      if (buildLocatorDimensions.keySet().size() > 1) {
        LOG.info("Build locator '" + buildLocator + "' has 'id' dimension and others. Others are ignored.");
      }
      return build;
    }

    if (buildType == null) {
      throw new BadRequestException("Cannot find build by other locator then 'id' without build type specified.");
    }

    String number = getSingleDimensionValue(buildLocatorDimensions, "number");
    if (number != null) {
      SBuild build = myServer.findBuildInstanceByBuildNumber(buildType.getBuildTypeId(), number);
      if (build == null) {
        throw new NotFoundException("No build can be found by number '" + number + "' in build configuration " + buildType + ".");
      }
      if (buildLocatorDimensions.keySet().size() > 1) {
        LOG.info("Build locator '" + buildLocator + "' has 'number' dimension and others. Others are ignored.");
      }
      return build;
    }

    final String status = getSingleDimensionValue(buildLocatorDimensions, "status");
    if (status != null) {
      final SFinishedBuild[] foundBuild = new SFinishedBuild[1];
      //todo: support all the parameters from URL
      myBuildHistory.processEntries(buildType.getBuildTypeId(), null, false, true, true, new ItemProcessor<SFinishedBuild>() {
        public boolean processItem(final SFinishedBuild build) {
          if (status.equalsIgnoreCase(build.getStatusDescriptor().getStatus().getText())) {
            foundBuild[0] = build;
            return false;
          }
          return true;
        }
      });
      if (foundBuild[0] != null) {
        return foundBuild[0];
      }
      throw new NotFoundException("No build with status '" + status + "'can be found in build configuration " + buildType + ".");
    }

    throw new NotFoundException("Build locator '" + buildLocator + "' is not supported");
  }

  @NotNull
  public SBuildType getBuildType(@Nullable final SProject project, @Nullable final String buildTypeLocator) {
    if (buildTypeLocator == null) {
      throw new BadRequestException("Empty build type locator is not supported.");
    }

    if (!hasDimensions(buildTypeLocator)) {
      // no dimensions found, assume it's a name
      SBuildType buildType = findBuildTypeByName(project, buildTypeLocator);
      if (buildType == null) {
        throw new NotFoundException("No build type is found by name '" + buildTypeLocator + "'.");
      }
      return buildType;
    }

    MultiValuesMap<String, String> buildTypeLocatorDimensions = decodeLocator(buildTypeLocator);

    String id = getSingleDimensionValue(buildTypeLocatorDimensions, "id");
    if (id != null) {
      SBuildType buildType = myServer.getProjectManager().findBuildTypeById(id);
      if (buildType == null) {
        throw new NotFoundException("No build type is found by id '" + id + "'.");
      }
      if (project != null && !buildType.getProject().equals(project)) {
        throw new NotFoundException("Build type with id '" + id + "' does not belog to project " + project + ".");
      }
      if (buildTypeLocatorDimensions.keySet().size() > 1) {
        LOG.info("Build type locator '" + buildTypeLocator + "' has 'id' dimension and others. Others are ignored.");
      }
      return buildType;
    }

    String name = getSingleDimensionValue(buildTypeLocatorDimensions, "name");
    if (name != null) {
      SBuildType buildType = findBuildTypeByName(project, name);
      if (buildType == null) {
        throw new NotFoundException("No build type is found by name '" + name + "'.");
      }
      if (buildTypeLocatorDimensions.keySet().size() > 1) {
        LOG.info("Build type locator '" + buildTypeLocator + "' has 'name' dimension and others. Others are ignored.");
      }
      return buildType;
    }
    throw new BadRequestException("Build type locator '" + buildTypeLocator + "' is not supported.");
  }

  @NotNull
  public SBuildServer getServer() {
    return myServer;
  }

  @NotNull
  public SProject getProject(String projectLocator) {
    if (projectLocator == null) {
      throw new BadRequestException("Empty project locator is not supported.");
    }

    if (!hasDimensions(projectLocator)) {
      // no dimensions found, assume it's a name
      SProject project = myServer.getProjectManager().findProjectByName(projectLocator);
      if (project == null) {
        throw new NotFoundException(
          "No project found by locator '" + projectLocator + "'. Project cannot be found by name '" + projectLocator + "'.");
      }
      return project;
    }

    MultiValuesMap<String, String> projectLocatorDimensions = decodeLocator(projectLocator);

    String id = getSingleDimensionValue(projectLocatorDimensions, "id");
    if (id != null) {
      SProject project = myServer.getProjectManager().findProjectById(id);
      if (project == null) {
        throw new NotFoundException("No project found by locator '" + projectLocator + ". Project cannot be found by id '" + id + "'.");
      }
      if (projectLocatorDimensions.keySet().size() > 1) {
        LOG.info("Project locator '" + projectLocator + "' has 'id' dimension and others. Others are ignored.");
      }
      return project;
    }

    String name = getSingleDimensionValue(projectLocatorDimensions, "name");
    if (name != null) {
      SProject project = myServer.getProjectManager().findProjectByName(name);
      if (project == null) {
        throw new NotFoundException("No project found by locator '" + projectLocator + ". Project cannot be found by name '" + name + "'.");
      }
      if (projectLocatorDimensions.keySet().size() > 1) {
        LOG.info("Project locator '" + projectLocator + "' has 'name' dimension and others. Others are ignored.");
      }
      return project;
    }
    throw new BadRequestException("Project locator '" + projectLocator + "' is not supported.");
  }

  /**
   * @param project project to search build type in. Can be 'null' to search in all the build types on the server.
   * @param name    name of the build type to search for.
   * @return build type with the name 'name'. If 'project' is not null, the search is performed only within 'project'.
   * @throws BadRequestException if several build types with the same name are found
   */
  @Nullable
  public SBuildType findBuildTypeByName(@Nullable final SProject project, @NotNull final String name) {
    if (project != null) {
      return project.findBuildTypeByName(name);
    }
    List<SBuildType> allBuildTypes = myServer.getProjectManager().getAllBuildTypes();
    SBuildType foundBuildType = null;
    for (SBuildType buildType : allBuildTypes) {
      if (name.equalsIgnoreCase(buildType.getName())) {
        if (foundBuildType == null) {
          foundBuildType = buildType;
        } else {
          //second match found
          throw new BadRequestException("Several matching build types found for name '" + name + "'.");
        }
      }
    }
    return foundBuildType;
  }

  /**
   * Extracts the single dimension value from dimensions.
   *
   * @param dimensions    dimensions to extract value from.
   * @param dimensionName the name of the dimension to extract value.
   * @return 'null' if no such dimension is found, value of the dimension otherwise.
   * @throws BadRequestException if there are more then a single dimension defiition for a 'dimensionName' name or the dimension has no value specified.
   */
  @Nullable
  public String getSingleDimensionValue(@NotNull final MultiValuesMap<String, String> dimensions, @NotNull final String dimensionName) {
    Collection<String> idDimension = dimensions.get(dimensionName);
    if (idDimension == null || idDimension.size() == 0) {
      return null;
    }
    if (idDimension.size() > 1) {
      throw new BadRequestException("Only single '" + dimensionName + "' dimension is supported in locator. Found: " + idDimension);
    }
    String result = idDimension.iterator().next();
    if (result == null) {
      throw new BadRequestException("Value is empty for dimension '" + dimensionName + "'.");
    }
    return result;
  }

  @NotNull
  public MultiValuesMap<String, String> decodeLocator(@NotNull final String locator) {
    MultiValuesMap<String, String> result = new MultiValuesMap<String, String>();
    for (String dimension : locator.split(DIMENSIONS_DELIMITER)) {
      int delimiterIndex = dimension.indexOf(DIMENSION_NAME_VALUE_DELIMITER);
      if (delimiterIndex > 0) {
        result.put(dimension.substring(0, delimiterIndex), dimension.substring(delimiterIndex + 1));
      } else {
        throw new NotFoundException(
          "Bad locator syntax: '" + locator + "'. Can't find dimension name in dimension string '" + dimension + "'");
      }
    }
    return result;
  }


  public boolean hasDimensions(@NotNull final String locator) {
    return locator.indexOf(DIMENSION_NAME_VALUE_DELIMITER) != -1;
  }


  /**
   * Finds builds by the specified criteria within specified range
   * @param buildType
   * @param user
   * @param includePersonalBuildsIfUserNotSpecified
   * @param includeCanceled
   * @param orderByChanges
   * @param start the index of the first build to return (begins with 0)
   * @param finish the index up to which (excluding) the builds will be returned
   * @return the builds found
   */
  public List<SFinishedBuild> getBuilds(final SBuildType buildType,
                                        @Nullable final User user,
                                        final boolean includePersonalBuildsIfUserNotSpecified,
                                        final boolean includeCanceled,
                                        final boolean orderByChanges,
                                        @Nullable final Long start,
                                        @Nullable final Long finish) {
    final ArrayList<SFinishedBuild> list = new ArrayList<SFinishedBuild>();
    myServer.getHistory().processEntries(buildType.getBuildTypeId(), user,
            includePersonalBuildsIfUserNotSpecified,
            includeCanceled,
            orderByChanges,
            new ItemProcessor<SFinishedBuild>() {
              long currentIndex = 0;

              public boolean processItem(final SFinishedBuild item) {
                if ((start == null || currentIndex >= start) && (finish == null || currentIndex < finish)) {
                  list.add(item);
                }
                ++currentIndex;
                return finish == null || currentIndex <= finish;
              }
            });
    return list;
  }

  @NotNull
  public SUser getUser(String userLocator) {
    if (userLocator == null) {
      throw new BadRequestException("Empty user locator is not supported.");
    }

    if (!hasDimensions(userLocator)) {
      // no dimensions found, assume it's username
      SUser user = myUserModel.findUserAccount(null, userLocator);
      if (user == null) {
        throw new NotFoundException("No user can be found by username '" + userLocator + "'.");
      }
      return user;
    }

    MultiValuesMap<String, String> userLocatorDimensions = decodeLocator(userLocator);

    String idString = getSingleDimensionValue(userLocatorDimensions, "id");
    if (idString != null) {
      Long id;
      try {
        id = Long.parseLong(idString);
      } catch (NumberFormatException e) {
        throw new BadRequestException("Invalid user id '" + idString + "'. Should be a number.");
      }
      SUser user = myUserModel.findUserById(id);
      if (user == null) {
        throw new NotFoundException("No user can be found by id '" + id + "'.");
      }
      if (userLocatorDimensions.keySet().size() > 1) {
        LOG.info("User locator '" + userLocator + "' has 'id' dimension and others. Others are ignored.");
      }
      return user;
    }

    String username = getSingleDimensionValue(userLocatorDimensions, "username");
    if (username != null) {
      SUser user = myUserModel.findUserAccount(null, username);
      if (user == null) {
        throw new NotFoundException("No user can be found by username '" + username + "'.");
      }
      return user;
    }
    throw new NotFoundException("User locator '" + userLocator + "' is not supported.");
  }

  @NotNull
  public Role getRoleById(String roleId) {
    if (roleId == null) {
      throw new BadRequestException("Cannot file role by empty id.");
    }
    Role role = myRolesManager.findRoleById(roleId);
    if (role == null){
      throw new NotFoundException("Cannot find role by id '" + roleId + "'.");
    }
    return role;
  }

  @NotNull
  public RoleScope getScope(String scopeData) {
    if (scopeData == null) {
      return RoleScope.globalScope();
    }
    RoleScope scope = RoleScope.projectScope(scopeData);
    if (scope == null){
      throw new NotFoundException("Cannot find scope by '" + scopeData + "'.");
    }
    return scope;
  }


  public Collection<SUser> getAllUsers() {
    return myUserModel.getAllUsers().getUsers();
  }
}
