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

package jetbrains.buildServer.server.rest.model.problem;

import java.util.Objects;
import javax.xml.bind.annotation.XmlAttribute;

@SuppressWarnings({"WeakerAccess", "unused"})
public class ParsedTestName {

  @XmlAttribute public String testPackage;
  @XmlAttribute public String testSuite;
  @XmlAttribute public String testClass;
  @XmlAttribute public String testShortName;
  @XmlAttribute public String testNameWithoutPrefix;
  @XmlAttribute public String testMethodName;
  @XmlAttribute public String testNameWithParameters;

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final ParsedTestName that = (ParsedTestName)o;
    return Objects.equals(testPackage, that.testPackage) &&
           Objects.equals(testSuite, that.testSuite) &&
           Objects.equals(testClass, that.testClass) &&
           Objects.equals(testShortName, that.testShortName) &&
           Objects.equals(testNameWithoutPrefix, that.testNameWithoutPrefix) &&
           Objects.equals(testMethodName, that.testMethodName) &&
           Objects.equals(testNameWithParameters, that.testNameWithParameters);
  }

  @Override
  public int hashCode() {
    return Objects.hash(testPackage, testSuite, testClass, testShortName, testNameWithoutPrefix, testMethodName, testNameWithParameters);
  }
}
