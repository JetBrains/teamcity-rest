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

package jetbrains.buildServer.server.rest.model.user;

import java.util.Date;
import javax.xml.bind.annotation.*;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.FeatureToggle;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.auth.AuthenticationToken;
import jetbrains.buildServer.serverSide.auth.PermanentTokenConstants;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jetbrains.buildServer.server.rest.model.user.Token.TYPE;

/**
 * @author Dmitrii Bogdanov
 */
@XmlRootElement(name = TYPE)
@XmlAccessorType
@XmlType(name = TYPE, propOrder = {
  "name",
  "creationTime",
  "value",
  "expirationTime",
  "permissionRestrictions"
})
public class Token {
  @NotNull
  static final String TYPE = "token";
  @Nullable
  private String name;
  @Nullable
  private Date expirationTime;
  @Nullable
  private Date creationTime;
  @Nullable
  private String value;
  @Nullable
  private PermissionRestrictions permissionRestrictions;

  public Token() {
    setCreationTime(null);
    setExpirationTime(null);
  }

  public Token(@NotNull final AuthenticationToken t, @NotNull final Fields fields, @NotNull final BeanContext beanContext) {
    this(t, null, fields, beanContext);
  }

  public Token(@NotNull final AuthenticationToken t, @Nullable String tokenValue, @NotNull final Fields fields, @NotNull final BeanContext beanContext) {
    if (fields.isIncluded("name", true, true)) {
      name = t.getName();
    }
    if (fields.isIncluded("expirationTime", false, true)) {
      expirationTime = PermanentTokenConstants.NO_EXPIRE.equals(t.getExpirationTime()) ? null : t.getExpirationTime();
    }
    if (fields.isIncluded("creationTime", false, true)) {
      creationTime = t.getCreationTime();
    }
    if (fields.isIncluded("value", true, true)) {
      value = tokenValue;
    }
    permissionRestrictions = ValueWithDefault.decideDefault(fields.isIncluded("permissionRestrictions", false, true),
                                                            FeatureToggle.withToggleDeferred("teamcity.internal.accessTokens.enablePermissionScopes", () -> {
                                                              final AuthenticationToken.PermissionsRestriction permissionsRestriction = t.getPermissionsRestriction();
                                                              return permissionsRestriction != null ? new PermissionRestrictions(permissionsRestriction,
                                                                                                                                 fields.getNestedField("permissionRestrictions"),
                                                                                                                                 beanContext) : null;
                                                            }));
  }

  @XmlAttribute
  @Nullable
  public String getName() {
    return name;
  }

  public void setName(@NotNull String name) {
    if (StringUtil.isEmpty(name)) {
      throw new BadRequestException("name should not be empty");
    }
    this.name = name;
  }

  @XmlAttribute
  @Nullable
  public Date getExpirationTime() {
    return expirationTime;
  }

  public void setExpirationTime(@Nullable Date expirationTime) {
    if (expirationTime == null) {
      expirationTime = new Date(PermanentTokenConstants.NO_EXPIRE.getTime());
    }
    final Date currentDate = new Date();
    if (currentDate.after(expirationTime)) {
      throw new BadRequestException("expirationDate should be after the current date");
    } else {
      this.expirationTime = expirationTime;
    }
  }

  @XmlElement(name = "permissionRestrictions")
  @Nullable
  public PermissionRestrictions getPermissionRestrictions() {
    return permissionRestrictions;
  }

  public void setPermissionRestrictions(@Nullable PermissionRestrictions permissionRestrictions) {
    FeatureToggle.withToggle("teamcity.internal.accessTokens.enablePermissionScopes", () -> this.permissionRestrictions = permissionRestrictions);
  }

  @XmlAttribute
  @Nullable
  public Date getCreationTime() {
    return creationTime;
  }

  public void setCreationTime(@Nullable Date ignored) {
    this.creationTime = new Date();
  }

  @XmlAttribute
  @Nullable
  public String getValue() {
    return value;
  }
}
