package com.mayer.mvn.analyzer;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.artifact.DefaultArtifact;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Mojo(name = "analyze-transitive-deps")
public class DependencyAnalyzerMojo extends AbstractMojo {

	@Parameter(defaultValue = "${project}", readonly = true, required = true)
	private MavenProject project;

	@Component
	private RepositorySystem repoSystem;

	@Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
	private RepositorySystemSession repoSession;

	private Map<String, Set<String>> dependencyMap = new HashMap<>();

	@Override
	public void execute() {
		if (repoSystem == null) {
			getLog().error("RepositorySystem is not available.");
			return;
		}

		try {
			CollectRequest collectRequest = new CollectRequest();
			List<org.apache.maven.model.Dependency> dependencies = project.getDependencies();

			for (org.apache.maven.model.Dependency mavenDependency : dependencies) {
				Dependency aetherDependency = new Dependency(new DefaultArtifact(mavenDependency.getGroupId(),
						mavenDependency.getArtifactId(), mavenDependency.getClassifier(), mavenDependency.getType(),
						mavenDependency.getVersion()), mavenDependency.getScope());
				collectRequest.addDependency(aetherDependency);
			}

			DependencyNode rootNode = repoSystem.collectDependencies(repoSession, collectRequest).getRoot();
			DependencyRequest dependencyRequest = new DependencyRequest(rootNode, null);
			repoSystem.resolveDependencies(repoSession, dependencyRequest);

			collectTransitiveDependencies(rootNode);

			printTransitiveDependencies();
		} catch (Exception e) {
			getLog().error("Failed to analyze dependencies", e);
		}
	}

	private void collectTransitiveDependencies(DependencyNode node) {
		if (node == null || node.getChildren() == null)
			return;

		Dependency dependency = node.getDependency();
		if (dependency != null && !node.getChildren().isEmpty()) {
			String artifactKey = getArtifactKey(dependency.getArtifact());
			if (!dependencyMap.containsKey(artifactKey)) {
				Set<String> transitiveDeps = new HashSet<>();
				for (DependencyNode child : node.getChildren()) {
					Dependency childDependency = child.getDependency();
					if (childDependency != null) {
						transitiveDeps.add(getArtifactKey(childDependency.getArtifact()));
					}
				}
				dependencyMap.put(artifactKey, transitiveDeps);
			}
		}

		for (DependencyNode child : node.getChildren()) {
			collectTransitiveDependencies(child);
		}
	}

	private void printTransitiveDependencies() {
		getLog().info("Transitive dependencies:");
		for (Map.Entry<String, Set<String>> entry : dependencyMap.entrySet()) {
			String artifact = entry.getKey();
			Set<String> transitiveDeps = entry.getValue();

			getLog().info("Artifact with transitive dependency: " + artifact);
			for (String transitive : transitiveDeps) {
				getLog().info("  Brings in: " + transitive);
			}
		}
	}

	private String getArtifactKey(org.eclipse.aether.artifact.Artifact artifact) {
		return artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getVersion();
	}
}
