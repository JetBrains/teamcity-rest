package jetbrains.buildServer.server.rest.data;

import java.util.ArrayList;
import java.util.List;
import jetbrains.buildServer.parameters.impl.MapParametersProviderImpl;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.serverSide.BuildAgentManager;
import jetbrains.buildServer.serverSide.SBuildAgent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 25.12.13
 */
public class AgentFinder extends AbstractFinder<SBuildAgent> {
  public static final String CONNECTED = "connected";
  public static final String AUTHORIZED = "authorized";
  public static final String PARAMETER = "parameter";
  @NotNull private final BuildAgentManager myAgentManager;

  public AgentFinder(final @NotNull BuildAgentManager agentManager) {
    super(new String[]{DIMENSION_ID, CONNECTED, AUTHORIZED, PARAMETER, Locator.LOCATOR_SINGLE_VALUE_UNUSED_NAME, PagerData.START, PagerData.COUNT});
    myAgentManager = agentManager;
  }

  @NotNull
  @Override
  public List<SBuildAgent> getAllItems() {
    final List<SBuildAgent> result = myAgentManager.getRegisteredAgents(true);
    result.addAll(myAgentManager.getUnregisteredAgents());
    return result;
  }

  @Override
  @Nullable
  protected SBuildAgent findSingleItem(@NotNull final Locator locator) {

    if (locator.isSingleValue()) {
      // no dimensions found, assume it's name
      final SBuildAgent agent = myAgentManager.findAgentByName(locator.getSingleValue(), true);
      if (agent == null) {
        throw new NotFoundException("No agent can be found by name '" + locator.getSingleValue() + "'.");
      }
      locator.checkLocatorFullyProcessed();
      return agent;
    }

    Long id = locator.getSingleDimensionValueAsLong("id");
    if (id != null) {
      final SBuildAgent agent = myAgentManager.findAgentById(id.intValue(), true);
      if (agent == null) {
        throw new NotFoundException("No agent can be found by id '" + locator.getSingleDimensionValue("id") + "'.");
      }
      locator.checkLocatorFullyProcessed();
      return agent;
    }

    String name = locator.getSingleDimensionValue("name");
    if (name != null) {
      final SBuildAgent agent =  myAgentManager.findAgentByName(name, true);
      if (agent != null) {
        locator.checkLocatorFullyProcessed();
        return agent;
      }
      throw new NotFoundException("No agent can be found by name '" + name + "'.");
    }

    return null;
  }

  @Override
  protected AbstractFilter<SBuildAgent> getFilter(final Locator locator) {
    if (locator.isSingleValue()) {
      throw new BadRequestException("Single value locator '" + locator.getSingleValue() + "' is not supported for several items query.");
    }

    final Long countFromFilter = locator.getSingleDimensionValueAsLong(PagerData.COUNT);
    final MultiCheckerFilter<SBuildAgent> result =
      new MultiCheckerFilter<SBuildAgent>(locator.getSingleDimensionValueAsLong(PagerData.START), countFromFilter != null ? countFromFilter.intValue() : null, null);


    final Boolean authorizedDimension = locator.getSingleDimensionValueAsBoolean(AUTHORIZED);
    if (authorizedDimension != null) {
      result.add(new FilterConditionChecker<SBuildAgent>() {
        public boolean isIncluded(@NotNull final SBuildAgent item) {
          return FilterUtil.isIncludedByBooleanFilter(authorizedDimension, item.isAuthorized());
        }
      });
    }

    final Boolean connectedDimension = locator.getSingleDimensionValueAsBoolean(CONNECTED);
    if (connectedDimension != null) {
      result.add(new FilterConditionChecker<SBuildAgent>() {
        public boolean isIncluded(@NotNull final SBuildAgent item) {
          return FilterUtil.isIncludedByBooleanFilter(connectedDimension, item.isRegistered());
        }
      });
    }

    final String parameterDimension = locator.getSingleDimensionValue(PARAMETER);
    if (parameterDimension != null) {
      final ParameterCondition parameterCondition = ParameterCondition.create(parameterDimension);
      result.add(new FilterConditionChecker<SBuildAgent>() {
        public boolean isIncluded(@NotNull final SBuildAgent item) {
          return parameterCondition.matches(new MapParametersProviderImpl(item.getAvailableParameters()));
        }
      });
    }

    return result;
  }

  @Override
  protected List<SBuildAgent> getPrefilteredItems(@NotNull final Locator locator) {
    List<SBuildAgent> result = new ArrayList<SBuildAgent>();

    final Boolean authorizedDimension = locator.getSingleDimensionValueAsBoolean(AUTHORIZED);
    if (authorizedDimension == null || authorizedDimension){
      result.addAll(myAgentManager.getRegisteredAgents(true));
    } else{
      result.addAll(myAgentManager.getRegisteredAgents(false));
    }

    final Boolean connectedDimension = locator.getSingleDimensionValueAsBoolean(CONNECTED);
    if (connectedDimension == null || !connectedDimension){
      result.addAll(myAgentManager.getUnregisteredAgents());
    }

    return result;
  }
}
