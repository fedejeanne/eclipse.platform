/*******************************************************************************
 * Copyright (c) 2006, 2017 IBM Corporation and others.
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
package org.eclipse.compare.internal.patch;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.List;

import org.eclipse.compare.CompareUI;
import org.eclipse.compare.IEncodedStreamContentAccessor;
import org.eclipse.compare.ITypedElement;
import org.eclipse.compare.internal.CompareUIPlugin;
import org.eclipse.compare.internal.core.patch.DiffProject;
import org.eclipse.compare.internal.core.patch.FileDiffResult;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.resource.LocalResourceManager;
import org.eclipse.swt.graphics.Image;

public class PatchFileTypedElement implements ITypedElement,
		IEncodedStreamContentAccessor {

	private final FileDiffResult result;
	private final boolean isAfterState;

	public PatchFileTypedElement(FileDiffResult result, boolean isAfterState) {
		this.result = result;
		this.isAfterState = isAfterState;
	}

	@Override
	public Image getImage() {
		IFile file = getPatcher().getTargetFile(result.getDiff());
		if (file == null) {
			// We don't get a target file if the file doesn't exist
			DiffProject project = result.getDiff().getProject();
			if (project != null) {
				file = Utilities.getProject(project).getFile(
						result.getDiff().getPath(
								result.getConfiguration().isReversed()));
			} else {
				IResource target = getPatcher().getTarget();
				if (target instanceof IFile) {
					file = (IFile) target;
				} else if (target instanceof IContainer container) {
					file = container.getFile(result.getTargetPath());
				}
			}
		}
		Image image = null;
		if (file != null) {
			image = CompareUI.getImage(file);
		}
		if (result.containsProblems()) {
			LocalResourceManager imageCache = PatchCompareEditorInput
					.getImageCache(result.getConfiguration());
			image = HunkTypedElement.getHunkErrorImage(image, imageCache, true);
		}
		return image;
	}

	@Override
	public String getName() {
		return result.getTargetPath().toString();
	}

	@Override
	public String getType() {
		return result.getTargetPath().getFileExtension();
	}

	@Override
	public String getCharset() throws CoreException {
		return result.getCharset();
	}

	@Override
	public InputStream getContents() throws CoreException {
		// If there are cached contents, use them
		if (isAfterState && getPatcher().hasCachedContents(result.getDiff())) {
			return new ByteArrayInputStream(getPatcher().getCachedContents(
					result.getDiff()));
		}
		// Otherwise, get the lines from the diff result
		List<String> lines;
		if (isAfterState) {
			lines = result.getAfterLines();
		} else {
			lines = result.getBeforeLines();
		}
		String contents = LineReader.createString(getPatcher()
				.isPreserveLineDelimiters(), lines);
		String charSet = getCharset();
		byte[] bytes = null;
		if (charSet != null) {
			try {
				bytes = contents.getBytes(charSet);
			} catch (UnsupportedEncodingException e) {
				CompareUIPlugin.log(e);
			}
		}
		if (bytes == null) {
			bytes = contents.getBytes();
		}
		return new ByteArrayInputStream(bytes);
	}

	private Patcher getPatcher() {
		return Patcher.getPatcher(result.getConfiguration());
	}
}
