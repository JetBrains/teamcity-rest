/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.data.problem;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import jetbrains.buildServer.BuildProblemData;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.responsibility.BuildProblemResponsibilityEntry;
import jetbrains.buildServer.responsibility.BuildProblemResponsibilityFacade;
import jetbrains.buildServer.server.rest.data.ProjectFinder;
import jetbrains.buildServer.server.rest.data.investigations.InvestigationWrapper;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.mute.CurrentMuteInfo;
import jetbrains.buildServer.serverSide.mute.MuteInfo;
import jetbrains.buildServer.serverSide.mute.ProblemMutingService;
import jetbrains.buildServer.serverSide.problems.BuildProblemInfo;
import jetbrains.buildServer.serverSide.problems.BuildProblemManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a problem (which can have occurrencies in the builds) as there is no appropriate class in TeamCity API (TeamCity API issue)
 *
 * @author Yegor.Yarko
 *         Date: 21.11.13
 */
public class ProblemWrapper implements Comparable<ProblemWrapper>{
  private final Integer id;
  private final String type;
  private final String identity;
  private List<MuteInfo> mutes;
  private List<InvestigationWrapper> investigations;

  @NotNull private final ServiceLocator myServiceLocator;

  public ProblemWrapper(final int problemId, final @NotNull ServiceLocator serviceLocator) {
    id = problemId;
    myServiceLocator = serviceLocator;

    final BuildProblemData problemData = serviceLocator.getSingletonService(BuildProblemManager.class).findProblemDataById(problemId);
    if (problemData != null) {
      type = problemData.getType();
      identity = problemData.getIdentity();
    }else{
      type = null;
      identity = null;
    }
    //TeamCity API: would also be great to add type desciption
  }

  /**
   * The same as above, just with a bit better performance
   * @param problemId
   * @param buildProblemData
   * @param serviceLocator
   */
  public ProblemWrapper(final int problemId, @NotNull final BuildProblemData buildProblemData, final @NotNull ServiceLocator serviceLocator) {
    id = problemId;
    myServiceLocator = serviceLocator;
    type = buildProblemData.getType();
    identity = buildProblemData.getIdentity();
  }

  @NotNull
  public Long getId() {
    return Long.valueOf(id);
  }

  @Nullable
  public String getType() {
    return type;
  }

  @Nullable
  public String getIdentity() {
    return identity;
  }

  @NotNull
  public List<MuteInfo> getMutes() {
    if (mutes == null) {
      Set<MuteInfo> mutesSet = new TreeSet<MuteInfo>();
      final SProject rootProject = myServiceLocator.getSingletonService(ProjectManager.class).getRootProject();
      final CurrentMuteInfo currentMuteInfo = myServiceLocator.getSingletonService(ProblemMutingService.class).getBuildProblemCurrentMuteInfo(rootProject.getProjectId(), id);
      if (currentMuteInfo != null) {
        mutesSet.addAll(currentMuteInfo.getProjectsMuteInfo().values());
        mutesSet.addAll(currentMuteInfo.getBuildTypeMuteInfo().values());
      }
      mutes = new ArrayList<MuteInfo>(mutesSet);
    }
    return mutes;
  }

  @NotNull
  public List<InvestigationWrapper> getInvestigations() {
    if (investigations == null){
      final String rootProjectId = myServiceLocator.getSingletonService(ProjectFinder.class).getRootProject().getProjectId();
      final List<BuildProblemResponsibilityEntry> responsibilities = myServiceLocator.getSingletonService(
        BuildProblemResponsibilityFacade.class).findBuildProblemResponsibilities(new BuildProblemInfo() {
        //TeamCity API issue: requires creating some fake object
        public int getId() {
          return id;
        }

        @NotNull
        public String getProjectId() {
          return rootProjectId;
        }

        @Nullable
        public String getBuildProblemDescription() {
          return null;
        }
      }, rootProjectId);

      final Set<InvestigationWrapper> investigationsSet = new TreeSet<InvestigationWrapper>();
      for (BuildProblemResponsibilityEntry responsibility : responsibilities) {
        investigationsSet.add(new InvestigationWrapper(responsibility));
      }
      investigations = new ArrayList<InvestigationWrapper>(investigationsSet);
    }
    return investigations;
  }

  //todo: review all methods below
  public int compareTo(@NotNull final ProblemWrapper o) {
    return Long.valueOf(id).compareTo(o.getId());
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final ProblemWrapper problemWrapper = (ProblemWrapper)o;

    return id.equals(problemWrapper.id);
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }
}
