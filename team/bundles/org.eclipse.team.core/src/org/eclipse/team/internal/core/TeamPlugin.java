/*******************************************************************************
 * Copyright (c) 2000, 2017 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.team.internal.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.StringTokenizer;

import org.eclipse.core.resources.IStorage;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtension;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Plugin;
import org.eclipse.core.runtime.QualifiedName;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.content.IContentDescription;
import org.eclipse.core.runtime.content.IContentTypeManager;
import org.eclipse.osgi.service.debug.DebugOptions;
import org.eclipse.osgi.service.debug.DebugOptionsListener;
import org.eclipse.team.core.RepositoryProvider;
import org.eclipse.team.core.RepositoryProviderType;
import org.eclipse.team.core.Team;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.core.mapping.DelegatingStorageMerger;
import org.eclipse.team.internal.core.mapping.IStreamMergerDelegate;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

/**
 * <code>TeamPlugin</code> is the plug-in runtime class for the Team
 * resource management plugin.
 *
 * @see Team
 * @see RepositoryProvider
 *
 * @since 2.0
 */
final public class TeamPlugin extends Plugin {

	// The id of the core team plug-in
	public static final String ID = "org.eclipse.team.core"; //$NON-NLS-1$

	// The id of the providers extension point
	public static final String PROVIDER_EXTENSION = "repository-provider-type"; //$NON-NLS-1$

	// The id of the file types extension point
	public static final String FILE_TYPES_EXTENSION = "fileTypes"; //$NON-NLS-1$

	// The id of the global ignore extension point
	public static final String IGNORE_EXTENSION = "ignore"; //$NON-NLS-1$
	// The id of the project set extension point
	public static final String PROJECT_SET_EXTENSION = "projectSets"; //$NON-NLS-1$
	// The id of the repository extension point
	public static final String REPOSITORY_EXTENSION = "repository"; //$NON-NLS-1$
	// The id of the default file modification validator extension point
	public static final String DEFAULT_FILE_MODIFICATION_VALIDATOR_EXTENSION = "defaultFileModificationValidator"; //$NON-NLS-1$

	// The id used to associate a provider with a project
	public final static QualifiedName PROVIDER_PROP_KEY =
		new QualifiedName("org.eclipse.team.core", "repository");  //$NON-NLS-1$  //$NON-NLS-2$

	// The id for the Bundle Import extension point
	public static final String EXTENSION_POINT_BUNDLE_IMPORTERS = ID + ".bundleImporters"; //$NON-NLS-1$

	// The one and only plug-in instance
	private static TeamPlugin plugin;

	private ServiceRegistration debugRegistration;
	private IStreamMergerDelegate mergerDelegate;

	/**
	 * Constructs a plug-in runtime class.
	 */
	public TeamPlugin() {
		super();
		plugin = this;
	}

	@Override
	public void start(BundleContext context) throws Exception {
		super.start(context);

		// register debug options listener
		Hashtable<String, String> properties = new Hashtable<>(2);
		properties.put(DebugOptions.LISTENER_SYMBOLICNAME, ID);
		debugRegistration = context.registerService(DebugOptionsListener.class, Policy.DEBUG_OPTIONS_LISTENER, properties);

		Team.startup();
	}

	@Override
	public void stop(BundleContext context) throws Exception {
		try {
			// unregister debug options listener
			debugRegistration.unregister();
			debugRegistration = null;

			Team.shutdown();
			ResourceVariantCache.shutdown();
		} finally {
			super.stop(context);
		}
	}

	/**
	 * Returns the Team plug-in.
	 *
	 * @return the single instance of this plug-in runtime class
	 */
	public static TeamPlugin getPlugin() {
		return plugin;
	}

	/**
	 * Log the given exception allowing with the provided message and severity indicator
	 * @param severity the severity
	 * @param message the message
	 * @param e the exception
	 */
	public static void log(int severity, String message, Throwable e) {
		plugin.getLog().log(new Status(severity, ID, 0, message, e));
	}

	/**
	 * Log the given CoreException in a manner that will include the stacktrace of
	 * the exception in the log.
	 * @param e the exception
	 */
	public static void log(CoreException e) {
		log(e.getStatus().getSeverity(), e.getMessage(), e);
	}

	/*
	 * Static helper methods for creating exceptions
	 */
	public static TeamException wrapException(CoreException e) {
		IStatus status = e.getStatus();
		return new TeamException(new Status(status.getSeverity(), ID, status.getCode(), status.getMessage(), e));
	}

	public static String getCharset(String name, InputStream stream) throws IOException {
		IContentDescription description = getContentDescription(name, stream);
		return description == null ? null : description.getCharset();

	}
	public static IContentDescription getContentDescription(String name, InputStream stream) throws IOException  {
		// tries to obtain a description for this file contents
		IContentTypeManager contentTypeManager = Platform.getContentTypeManager();
		try {
			return contentTypeManager.getDescriptionFor(stream, name, IContentDescription.ALL);
		} finally {
			if (stream != null) {
				try {
					stream.close();
				} catch (IOException e) {
					// Ignore exceptions on close
				}
			}
		}
	}

	public static RepositoryProviderType getAliasType(String id) {
		IExtensionPoint extension = Platform.getExtensionRegistry().getExtensionPoint(TeamPlugin.ID, TeamPlugin.REPOSITORY_EXTENSION);
		if (extension != null) {
			IExtension[] extensions =  extension.getExtensions();
			for (IExtension ext : extensions) {
				IConfigurationElement[] configElements = ext.getConfigurationElements();
				for (IConfigurationElement configElement : configElements) {
					String aliasId = configElement.getAttribute("canImportId"); //$NON-NLS-1$
					if (aliasId != null && aliasId.equals(id)) {
						String extensionId = configElement.getAttribute("id"); //$NON-NLS-1$
						if (extensionId != null) {
							return RepositoryProviderType.getProviderType(extensionId);
						}
					}
				}
			}
		}
		return null;
	}

	public static IPath[] getMetaFilePaths(String id) {
		IExtensionPoint extension = Platform.getExtensionRegistry().getExtensionPoint(TeamPlugin.ID, TeamPlugin.REPOSITORY_EXTENSION);
		if (extension != null) {
			IExtension[] extensions =  extension.getExtensions();
			for (IExtension ext : extensions) {
				IConfigurationElement[] configElements = ext.getConfigurationElements();
				for (IConfigurationElement configElement : configElements) {
					String extensionId = configElement.getAttribute("id"); //$NON-NLS-1$
					String metaFilePaths = configElement.getAttribute("metaFilePaths"); //$NON-NLS-1$
					if (extensionId != null && extensionId.equals(id) && metaFilePaths != null) {
						return getPaths(metaFilePaths);

					}
				}
			}
		}
		return null;
	}

	private static IPath[] getPaths(String metaFilePaths) {
		List<IPath> result = new ArrayList<>();
		StringTokenizer t = new StringTokenizer(metaFilePaths, ","); //$NON-NLS-1$
		while (t.hasMoreTokens()) {
			String next = t.nextToken();
			IPath path = new Path(null, next);
			result.add(path);
		}
		return result.toArray(new IPath[result.size()]);
	}

	/**
	 * Set the file merger that is used by the {@link DelegatingStorageMerger#merge(OutputStream, String, IStorage, IStorage, IStorage, IProgressMonitor)}
	 * method. It is the responsibility of subclasses to provide a merger.
	 * If a merger is not provided, subclasses must override <code>performThreeWayMerge</code>.
	 * @param merger the merger used to merge files
	 */
	public void setMergerDelegate(IStreamMergerDelegate merger) {
		mergerDelegate = merger;
	}

	public IStreamMergerDelegate getMergerDelegate() {
		return mergerDelegate;
	}

}
