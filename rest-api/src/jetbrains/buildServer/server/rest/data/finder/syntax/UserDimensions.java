package jetbrains.buildServer.server.rest.data.finder.syntax;

import jetbrains.buildServer.server.rest.data.finder.AbstractFinder;
import jetbrains.buildServer.server.rest.data.locator.*;
import jetbrains.buildServer.server.rest.data.locator.definition.FinderLocatorDefinition;
import jetbrains.buildServer.server.rest.data.locator.definition.LocatorDefinition;
import jetbrains.buildServer.server.rest.swagger.annotations.LocatorResource;
import jetbrains.buildServer.server.rest.swagger.constants.CommonLocatorDimensionsList;
import jetbrains.buildServer.server.rest.swagger.constants.LocatorDimensionDataType;
import jetbrains.buildServer.server.rest.swagger.constants.LocatorName;
import jetbrains.buildServer.serverSide.auth.Permission;

@LocatorResource(value = LocatorName.USER,
  extraDimensions = {
    CommonLocatorDimensionsList.PROPERTY,
    CommonLocatorDimensionsList.CURRENT,
    AbstractFinder.DIMENSION_ITEM
  },
  baseEntity = "User",
  examples = {
    "`name:John Smith` — find user with name `John Smith`.",
    "`group:<groupLocator>` — find all users in user group found by `groupLocator`."
  }
)
public class UserDimensions implements FinderLocatorDefinition {
  public static final Dimension SINGLE_VALUE = CommonLocatorDimensions.SINGLE_VALUE("Username or 'current' for current user.");

  public static final Dimension ID = Dimension.ofName("id")
                                              .description("User id.")
                                              .syntax(PlainValue.int64())
                                              .build();

  public static final Dimension USERNAME = Dimension.ofName("username")
                                                    .description("Username of a user.")
                                                    .syntax(PlainValue.string())
                                                    .build();

  public static final Dimension GROUP = Dimension.ofName("group")
                                                 .description("User group (direct parent) locator, includes the user directly.")
                                                 .syntax(Syntax.forLocator(LocatorName.USER_GROUP))
                                                 .build();

  public static final Dimension AFFECTED_GROUP = Dimension.ofName("affectedGroup")
                                                          .description("User group (direct or indirect parent) locator, includes the user considering group hierarchy.")
                                                          .syntax(Syntax.forLocator(LocatorName.USER_GROUP))
                                                          .build();

  public static final Dimension PROPERTY = CommonLocatorDimensions.PROPERTY;

  public static final Dimension EMAIL = Dimension.ofName("email")
                                                 .description("User email.")
                                                 .syntax(PlainValue.string())
                                                 .build();

  public static final Dimension NAME = Dimension.ofName("name")
                                                .description("User's display name")
                                                .dimensions(ValueCondition.class)
                                                .build();
  public static final Dimension HAS_PASSWORD = Dimension.ofName("hasPassword")
                                                        .description("user has not empty password")
                                                        .syntax(BooleanValue::new)
                                                        .hidden()
                                                        .build();

  public static final Dimension PASSWORD = Dimension.ofName("password")
                                                    .description("Find users by their password value. Disabled by default.")
                                                    .syntax(PlainValue.string())
                                                    .hidden()
                                                    .build();

  public static final Dimension LAST_LOGIN_TIME = Dimension.ofName("lastLogin")
                                                           .description("User's last login time formatted as 'yyyyMMddTHHmmss+ZZZZ'")
                                                           .syntax(Syntax.forLocator(LocatorDimensionDataType.TIMESTAMP))
                                                           .build();

  public static final Dimension ROLE = Dimension.ofName("role")
                                                .description("User's role")
                                                .syntax(Syntax.forLocator(LocatorName.ROLE))
                                                .build();
  public static final Dimension PERMISSION = Dimension.ofName("permission")
                                                      .description("user's permission (experimental)")
                                                      .dimensions(PermissionCheckDimensions.class)
                                                      .hidden()
                                                      .build();

  public static class PermissionCheckDimensions implements LocatorDefinition {
    public static final Dimension PROJECT = Dimension.ofName("project")
                                                     .description("Project to check permission in (matching when permission is present at least in one of the matched projects), when omitted checking globally.")
                                                     .syntax(Syntax.forLocator(LocatorName.PROJECT))
                                                     .build();
    public static final Dimension PERMISSION = Dimension.ofName("permission")
                                                        .description("permission to check, should be present")
                                                        .syntax(EnumValue.of(Permission.class))
                                                        .build();
  }
}
