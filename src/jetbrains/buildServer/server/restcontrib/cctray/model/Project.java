/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

package jetbrains.buildServer.server.restcontrib.cctray.model;

import com.sun.org.apache.xerces.internal.jaxp.datatype.XMLGregorianCalendarImpl;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlSchemaType;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.adapters.CollapsedStringAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import javax.xml.datatype.XMLGregorianCalendar;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.serverSide.*;

/**
 * <p>Java class for anonymous complex type.
 * <p/>
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p/>
 * <pre>
 * &lt;complexType>
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;attribute name="name" use="required" type="{http://www.w3.org/2001/XMLSchema}NMTOKEN" />
 *       &lt;attribute name="activity" use="required">
 *         &lt;simpleType>
 *           &lt;restriction base="{http://www.w3.org/2001/XMLSchema}NMTOKEN">
 *             &lt;enumeration value="Sleeping"/>
 *             &lt;enumeration value="Building"/>
 *             &lt;enumeration value="CheckingModifications"/>
 *           &lt;/restriction>
 *         &lt;/simpleType>
 *       &lt;/attribute>
 *       &lt;attribute name="lastBuildStatus" use="required">
 *         &lt;simpleType>
 *           &lt;restriction base="{http://www.w3.org/2001/XMLSchema}NMTOKEN">
 *             &lt;enumeration value="Exception"/>
 *             &lt;enumeration value="Success"/>
 *             &lt;enumeration value="Failure"/>
 *             &lt;enumeration value="Unknown"/>
 *           &lt;/restriction>
 *         &lt;/simpleType>
 *       &lt;/attribute>
 *       &lt;attribute name="lastBuildLabel" use="required" type="{http://www.w3.org/2001/XMLSchema}NMTOKEN" />
 *       &lt;attribute name="lastBuildTime" use="required" type="{http://www.w3.org/2001/XMLSchema}dateTime" />
 *       &lt;attribute name="nextBuildTime" type="{http://www.w3.org/2001/XMLSchema}dateTime" />
 *       &lt;attribute name="webUrl" use="required" type="{http://www.w3.org/2001/XMLSchema}string" />
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 */
@XmlType(name = "", namespace = "http://cctray")
public class Project {

    private SBuildType myBuildType;
    protected ServiceLocator myServiceLocator;

    public Project() {
    }

    public Project(ServiceLocator serviceLocator, SBuildType buildType) {
        this.myServiceLocator = serviceLocator;
        myBuildType = buildType;
    }

    /**
     * Gets the value of the name property.
     *
     * @return possible object is
     *         {@link String }
     */
    @XmlAttribute(name = "name", required = true)
    @XmlJavaTypeAdapter(CollapsedStringAdapter.class)
    @XmlSchemaType(name = "NMTOKEN")
    public String getName() {
        return myBuildType.getFullName();
    }

    /**
     * Gets the value of the activity property.
     *
     * @return possible object is
     *         {@link String }
     */
    @XmlAttribute(name = "activity", required = true)
    @XmlJavaTypeAdapter(CollapsedStringAdapter.class)
    public String getActivity() {
        final List<SRunningBuild> runningBuilds = myBuildType.getRunningBuilds();
        if (runningBuilds.size() > 0) {
            return "Building";
        }
//        if (myBuildType.isInQueue()) {
//            return "Waiting in queue"; // non standard, makes cctray hanging
//        }
        if (myBuildType.isPaused()) {
            return "Paused"; // non standard
        }
        if (myBuildType.getPendingChanges().size() > 0) {
            return "Has pending changes"; // non standard
        }
        return "Sleeping";
    }

    /**
     * Gets the value of the lastBuildStatus property.
     *
     * @return possible object is
     *         {@link String }
     */
    @XmlAttribute(name = "lastBuildStatus", required = true)
    @XmlJavaTypeAdapter(CollapsedStringAdapter.class)
    public String getLastBuildStatus() {
        if (myBuildType.getStatus().isFailed()) {
            return "Failure";
        }
        if (myBuildType.getStatus().isSuccessful()) {
            return "Success";
        }
        return "Unknown";
    }

    /**
     * Gets the value of the lastBuildLabel property.
     *
     * @return possible object is
     *         {@link String }
     */
    @XmlAttribute(name = "lastBuildLabel", required = true)
    @XmlJavaTypeAdapter(CollapsedStringAdapter.class)
    @XmlSchemaType(name = "NMTOKEN")
    public String getLastBuildLabel() {
        final SFinishedBuild lastBuild = myBuildType.getLastChangesFinished();
        if (lastBuild == null) {
            // is this OK?
            return null;
        }
        return String.valueOf(lastBuild.getBuildNumber());
    }

    /**
     * Gets the value of the lastBuildTime property.
     *
     * @return possible object is
     *         {@link javax.xml.datatype.XMLGregorianCalendar }
     */
    @XmlAttribute(name = "lastBuildTime", required = true)
    @XmlSchemaType(name = "dateTime")
    public XMLGregorianCalendar getLastBuildTime() {
        final GregorianCalendar calendar = new GregorianCalendar();
        final SFinishedBuild lastBuild = myBuildType.getLastChangesFinished();
        if (lastBuild == null) {
            // is this OK?
            return null;
        }
        calendar.setTime(lastBuild.getStartDate());
        return new XMLGregorianCalendarImpl(calendar);
    }

    /**
     * Gets the value of the nextBuildTime property.
     *
     * @return possible object is
     *         {@link javax.xml.datatype.XMLGregorianCalendar }
     */
    @XmlAttribute(name = "nextBuildTime")
    @XmlSchemaType(name = "dateTime")
    public XMLGregorianCalendar getNextBuildTime() {
        final GregorianCalendar result = new GregorianCalendar();

        final List<SQueuedBuild> queuedBuilds = myBuildType.getQueuedBuilds(null);
        for (SQueuedBuild build : queuedBuilds) {
            if (!build.isPersonal()) {
                final BuildEstimates estimate = build.getBuildEstimates();
                if (estimate != null) {
                    final TimeInterval interval = estimate.getTimeInterval();
                    if (interval != null && isValid(interval)) {
                        result.setTime(interval.getStartPoint().getAbsoluteTime());
                        return new XMLGregorianCalendarImpl(result);
                    }
                }
            }
        }
        //todo: get next VCS checking/scheduled time
        return null;
    }

  private static boolean isValid(final TimeInterval interval) {
    // todo (TeamCity) can return some huge number for some reason: TW-19894
    final long fromNow_ms = interval.getStartPoint().getAbsoluteTime().getTime() - (new Date()).getTime();
    if (fromNow_ms > 1000*60*60*24*100 || fromNow_ms < 0){
      return false;
    }
    return true;
  }

  /**
     * Gets the value of the webUrl property.
     *
     * @return possible object is
     *         {@link String }
     */
    @XmlAttribute(name = "webUrl", required = true)
    public String getWebUrl() {
        final WebLinks webLinks = myServiceLocator.getSingletonService(WebLinks.class);
        return webLinks.getConfigurationHomePageUrl(myBuildType);
    }
}
