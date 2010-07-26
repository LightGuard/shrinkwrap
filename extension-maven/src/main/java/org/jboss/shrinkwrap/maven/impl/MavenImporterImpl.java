package org.jboss.shrinkwrap.maven.impl;

import java.io.File;
import java.util.Arrays;
import java.util.Properties;
import java.util.zip.ZipFile;

import org.apache.maven.Maven;
import org.apache.maven.artifact.repository.ArtifactRepositoryPolicy;
import org.apache.maven.artifact.repository.MavenArtifactRepository;
import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout;
import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionResult;
import org.apache.maven.model.Build;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.PlexusContainerException;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.logging.console.ConsoleLoggerManager;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.importer.ZipImporter;
import org.jboss.shrinkwrap.impl.base.AssignableBase;
import org.jboss.shrinkwrap.maven.api.MavenImporter;

public class MavenImporterImpl extends AssignableBase implements MavenImporter
{
   private static DefaultPlexusContainer container = null; 
   
   private PlexusContainer getInstance() 
   {
      if(container == null)
      {
         try
         {
            container = new DefaultPlexusContainer();
            container.setLoggerManager(new ConsoleLoggerManager("ERROR"));
         } 
         catch (PlexusContainerException e) 
         {
            throw new RuntimeException("Could not initiate Maven Container", e);
         }
      }
      return container;
   }
   
   
   private Archive<?> archive;
   
   public MavenImporterImpl(Archive<?> archive)
   {
      if(archive == null)
      {
         throw new IllegalArgumentException("Archive must be specified");
      }
      this.archive = archive;
   }

   @Override
   protected Archive<?> getArchive()
   {
      return archive;
   }
   
   @Override
   public MavenImporter from(File pom)
   {
      importArtifact(generateArtifact(pom));
      return this;
   }
   
   private void importArtifact(File artifact)
   {
      try
      {
         archive.as(ZipImporter.class).importZip(new ZipFile(artifact));
      }
      catch (Exception e) 
      {
         throw new RuntimeException("Could not import artifact " + artifact.getAbsolutePath(), e);
      }
   }

   private File generateArtifact(File pom) 
   {
      
      ClassLoader previousTCCL = Thread.currentThread().getContextClassLoader();
      
      PlexusContainer container = getInstance();
      ArtifactRepositoryLayout layout;
      try
      {
         layout = container.lookup(ArtifactRepositoryLayout.class);
      }
      catch (ComponentLookupException e)
      {
         throw new RuntimeException("Could not lookup " + ArtifactRepositoryLayout.class, e);
      }
      Maven maven;
      try
      {
         maven = container.lookup(Maven.class);
      }
      catch (ComponentLookupException e)
      {
         throw new RuntimeException("Could not lookup " + Maven.class, e);
      }

      MavenExecutionRequest request = new DefaultMavenExecutionRequest();
      request.setLocalRepository(new MavenArtifactRepository(
            "local", 
            "file:///home/aslak/.m2/repository", 
            layout, 
            new ArtifactRepositoryPolicy(true, ArtifactRepositoryPolicy.UPDATE_POLICY_NEVER, ArtifactRepositoryPolicy.CHECKSUM_POLICY_WARN), 
            new ArtifactRepositoryPolicy(true, ArtifactRepositoryPolicy.UPDATE_POLICY_NEVER, ArtifactRepositoryPolicy.CHECKSUM_POLICY_WARN)));

      request.setPom(pom);
      request.setBaseDirectory(pom.getParentFile());
      request.setGoals(Arrays.asList("package"));
      request.setInteractiveMode(false);
      request.setOffline(true);
      
      Properties userProps = new Properties();
      userProps.setProperty("maven.test.skip", "true");
      
      request.setUserProperties(userProps);
      
      
      MavenExecutionResult result = maven.execute(request);
      
      MavenProject project = result.getProject();
      Build build = project.getBuild();
      
      Thread.currentThread().setContextClassLoader(previousTCCL);
      
      return new File(build.getDirectory(), build.getFinalName() + "." + project.getPackaging());
   }
}