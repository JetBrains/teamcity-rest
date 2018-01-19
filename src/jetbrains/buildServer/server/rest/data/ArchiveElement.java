/*
 * Copyright 2000-2018 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.data;

import com.intellij.openapi.diagnostic.Logger;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.StreamingOutput;
import jetbrains.buildServer.server.rest.errors.InvalidStateException;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.browser.Browser;
import jetbrains.buildServer.util.browser.BrowserException;
import jetbrains.buildServer.util.browser.Element;
import jetbrains.buildServer.web.artifacts.browser.ArtifactTreeElement;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 19.09.2014
 */
public class ArchiveElement implements Element {
  private static final Logger LOG = Logger.getInstance(ArchiveElement.class.getName());

  private final List<ArtifactTreeElement> myArtifacts;
  private final String myName;

  public ArchiveElement(final List<ArtifactTreeElement> artifacts, final String name) {
    myArtifacts = artifacts;
    myName = name;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @NotNull
  public String getFullName() {
    return myName;
  }

  public boolean isLeaf() {
    return true;
  }

  @Nullable
  public Iterable<Element> getChildren() throws BrowserException {
    return null;
  }

  public boolean isContentAvailable() {
    return true;
  }

  public StreamingOutput getStreamingOutput(@Nullable final Long startOffset, @Nullable final Long length) {
    if (startOffset != null || length != null){
      throw new IllegalStateException("Partial streaming is not yet supported");
    }

    return new StreamingOutput() {
      public void write(final OutputStream out) throws WebApplicationException {
        final ZipArchiveOutputStream resultOutput = new ZipArchiveOutputStream(new BufferedOutputStream(out));
        resultOutput.setEncoding(null); // TW-12815
        try {
          for (ArtifactTreeElement artifact : myArtifacts) { //todo: need to read-lock artifacts???
            if (!artifact.isLeaf()){
              //process a directory
              String directoryFullName = artifact.getFullName();
              if (!directoryFullName.endsWith("/")){
                directoryFullName += "/";
              }
              ZipArchiveEntry entry = new ZipArchiveEntry(directoryFullName);
              final Long lastModified = artifact.getLastModified();
              if (lastModified != null) entry.setTime(lastModified);
              try {
                resultOutput.putArchiveEntry(entry);
                resultOutput.closeArchiveEntry();
              } catch (IOException e) {
                throw new InvalidStateException("Error processing directory '" + artifact.getFullName() + "': " + e.toString(), e);
              }
            }

            if (!artifact.isContentAvailable()) continue;

            try {
              InputStream stream = artifact.getInputStream();
              try {
                ZipArchiveEntry entry = new ZipArchiveEntry(artifact.getFullName());
                final Long lastModified = artifact.getLastModified();
                if (lastModified != null) entry.setTime(lastModified); //might need to add more, see com.intellij.util.io.ZipUtil.addFileToZip()
                resultOutput.putArchiveEntry(entry);
                FileUtil.copyStreams(stream, resultOutput);
                resultOutput.closeArchiveEntry();
              } finally {
                FileUtil.close(stream);
              }
            } catch (IOException e) {
              throw new InvalidStateException("Error processing artifact '" + artifact.getFullName() + "': " + e.toString(), e);
            }
          }
        } finally {
          try {
            resultOutput.close();
          } catch (Exception e) {
            LOG.info("Error closing archived stream: " + e.toString());
            LOG.debug("Error closing archived stream: " + e.toString(), e);
          }
        }
      }
    };
  }


  @NotNull
  public InputStream getInputStream() throws IllegalStateException, IOException, BrowserException {
    throw new IllegalStateException("Operation is not supported");
  }

  public long getSize() throws IllegalStateException {
    return -1;
  }

  @NotNull
  public Browser getBrowser() {
    throw new IllegalStateException("Operation is not supported");
  }
}
