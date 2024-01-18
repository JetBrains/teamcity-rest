package jetbrains.buildServer.server.rest.data.finder.syntax;

import jetbrains.buildServer.server.rest.data.locator.*;
import jetbrains.buildServer.server.rest.data.locator.definition.FinderLocatorDefinition;
import jetbrains.buildServer.server.rest.swagger.annotations.LocatorResource;
import jetbrains.buildServer.server.rest.swagger.constants.LocatorName;

@LocatorResource(value = LocatorName.AGENT_POOL,
  baseEntity = "AgentPool",
  examples = {
    "`name:Default` — find `Default` agent pool details.",
    "`project:(<projectLocator>)` — find pool associated with project found by `projectLocator`."
  }
)
public class AgentPoolDimensions implements FinderLocatorDefinition {
  public static final Dimension SINGLE_VALUE = CommonLocatorDimensions.SINGLE_VALUE("Agent pool id.", PlainValue.int64());

  public static final Dimension ID = Dimension.ofName("id")
                                              .description("Agent pool id.")
                                              .syntax(PlainValue.int64()).build();

  public static final Dimension NAME = Dimension.ofName("name")
                                                .description("Agent pool name.")
                                                .syntax(PlainValue.string()).build();

  public static final Dimension AGENT = Dimension.ofName("agent")
                                                 .description("Pool's associated agents locator.")
                                                 .syntax(Syntax.forLocator(LocatorName.AGENT))
                                                 .build();

  public static final Dimension PROJECT = Dimension.ofName("project")
                                                   .description("Pool's associated projects locator.")
                                                   .syntax(Syntax.forLocator(LocatorName.PROJECT))
                                                   .build();
  public static final Dimension PROJECT_POOL = Dimension.ofName("projectPool")
                                                        .description("Filter project agent pools.")
                                                        .hidden() // Hidden reason: might want to rethink naming
                                                        .syntax(BooleanValue::new)
                                                        .build();

  public static final Dimension OWNER_PROJECT = Dimension.ofName("ownerProject")
                                                         .description("Project which defines the project pool")
                                                         .hidden()  // Hidden reason: might want to rethink naming
                                                         .syntax(Syntax.forLocator(LocatorName.PROJECT))
                                                         .build();

  public static final Dimension ORPHANED_POOL = Dimension.ofName("orphanedPool")
                                                         .description("Project pool which owner project was deleted from the server.")
                                                         .hidden() // This is more of a debug endpoint, not really needed in production.
                                                         .syntax(BooleanValue::new)
                                                         .build();
}
