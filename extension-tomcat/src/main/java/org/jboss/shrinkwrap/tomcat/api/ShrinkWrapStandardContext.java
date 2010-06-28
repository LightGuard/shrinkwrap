/*
 * JBoss, Home of Professional Open Source
 * Copyright 2010, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.shrinkwrap.tomcat.api;

import java.io.File;
import java.io.IOException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.logging.Logger;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.session.StandardManager;
import org.apache.catalina.startup.ContextConfig;

import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.Assignable;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.WebArchive;

/**
 * Tomcat's {@link StandardContext} backed by a ShrinkWrap
 * {@link Archive}; capable of being deployed into 
 * the Tomcat Embedded container
 * 
 * @author Dan Allen
 * @version $Revision: $
 */
public class ShrinkWrapStandardContext extends StandardContext implements Assignable
{

   //-------------------------------------------------------------------------------------||
   // Class Members ----------------------------------------------------------------------||
   //-------------------------------------------------------------------------------------||

   /**
    * Logger
    */
   private static final Logger log = Logger.getLogger(ShrinkWrapStandardContext.class.getName());

   /**
    * System property denoting the name of the temp directory
    */
   private static final String SYSPROP_KEY_TMP_DIR = "java.io.tmpdir";

   /**
    * The prefix assigned to the temporary file where the archive is exported
    */
   private static final String EXPORT_FILE_PREFIX = "export";

   /**
    * The folder name of the Tomcat work directory
    */
   private static final String WORK_FOLDER = "work";

   /**
    * Temporary directory into which we'll extract the {@link WebArchive}s
    */
   private static final File TMP_DIR;
   static
   {
      TMP_DIR = new File(AccessController.doPrivileged(new PrivilegedAction<String>()
      {

         @Override
         public String run()
         {
            return System.getProperty(SYSPROP_KEY_TMP_DIR);
         }

      }));
      // If the temp location doesn't exist or isn't a directory
      if (!TMP_DIR.exists() || !TMP_DIR.isDirectory())
      {
         throw new IllegalStateException("Could not obtain temp directory \"" + TMP_DIR.getAbsolutePath() + "\"");
      }
   }

   //-------------------------------------------------------------------------------------||
   // Instance Members -------------------------------------------------------------------||
   //-------------------------------------------------------------------------------------||

   /**
    * Underlying delegate
    */
   private final Archive<?> archive;

   //-------------------------------------------------------------------------------------||
   // Constructor ------------------------------------------------------------------------||
   //-------------------------------------------------------------------------------------||

   /**
    * Creates a new {@link ShrinkWrapStandardContext} using the
    * specified underlying archive
    * 
    * @throws IllegalArgumentException If the archive is not specified
    */
   public ShrinkWrapStandardContext(final Archive<?> archive) throws IllegalArgumentException
   {
      // Invoke super
      super();

      // Precondition checks
      if (archive == null)
      {
         throw new IllegalArgumentException("archive must be specified");
      }

      // Flush to file
      final String archiveName = archive.getName();
      final int extensionOffset = archiveName.lastIndexOf('.');
      final String baseName = extensionOffset >= 0 ? archiveName.substring(0, extensionOffset) : archiveName;
      final File exported;
      try
      {
         // If this method returns successfully then it is guaranteed that:
         // 1. The file denoted by the returned abstract pathname did not exist before this method was invoked, and
         // 2. Neither this method nor any of its variants will return the same abstract pathname again in the current invocation of the virtual machine.
         exported = File.createTempFile(EXPORT_FILE_PREFIX, archiveName, TMP_DIR);
      }
      catch (IOException e)
      {
         throw new RuntimeException("Could not create temporary File in " + TMP_DIR + " to write exported archive", e);
      }
      // We are overwriting the temporary file placeholder reserved by File#createTemplateFile()
      archive.as(ZipExporter.class).exportZip(exported, true);

      // Mark to delete when we come down
      exported.deleteOnExit();

      // Add the context
      log.info("Webapp archive location: " + exported.getAbsolutePath());
      this.setDocBase(exported.getAbsolutePath());
      // path should not begin with a / or else Tomcat fails to deploy the webapp properly
      this.setPath(baseName);
      this.setUnpackWAR(false);
      // QUESTION are we over-stepping the bounds w/ ContextConfig, StandardManager and work folder?
      ContextConfig config = new ContextConfig();
      ((Lifecycle) this).addLifecycleListener(config);
      this.setManager(new StandardManager());
      this.setWorkDir(new File(TMP_DIR, WORK_FOLDER).getAbsolutePath());

      // Remember the archive from which we're created
      this.archive = archive;
   }

   //-------------------------------------------------------------------------------------||
   // Required Implementations -----------------------------------------------------------||
   //-------------------------------------------------------------------------------------||

   /**
    * {@inheritDoc}
    * @see org.jboss.shrinkwrap.api.Assignable#as(java.lang.Class)
    */
   @Override
   public <TYPE extends Assignable> TYPE as(final Class<TYPE> clazz)
   {
      return archive.as(clazz);
   }
}