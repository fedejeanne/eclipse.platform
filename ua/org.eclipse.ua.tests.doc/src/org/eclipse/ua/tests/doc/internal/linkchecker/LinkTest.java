/*******************************************************************************
 * Copyright (c) 2017, 2019 Red Hat Inc. and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Mickael Istria (Red Hat Inc.) - initial API and implementation
 *******************************************************************************/
package org.eclipse.ua.tests.doc.internal.linkchecker;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.help.internal.base.BaseHelpSystem;
import org.eclipse.help.internal.search.ISearchHitCollector;
import org.eclipse.help.internal.search.ISearchQuery;
import org.eclipse.help.internal.search.QueryTooComplexException;
import org.eclipse.help.internal.search.SearchHit;
import org.eclipse.help.internal.search.SearchQuery;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.help.IWorkbenchHelpSystem;
import org.junit.jupiter.api.Test;

public class LinkTest {

	@Test
	public void testAllLinks() {
		IWorkbenchHelpSystem helpSystem = PlatformUI.getWorkbench().getHelpSystem();
		ISearchQuery query = new SearchQuery("*", false, Collections.emptyList(), Platform.getNL());
		final Set<URI> indexedPagesURIs = new HashSet<>();

		Set<Exception> hrefErrors = Collections.synchronizedSet(new LinkedHashSet<>());
		ISearchHitCollector collector = new ISearchHitCollector() {
			@Override
			public void addQTCException(QueryTooComplexException exception) throws QueryTooComplexException {
				throw exception;
			}

			@Override
			public void addHits(List<SearchHit> hits, String wordsSearched) {
				hits.stream().map(SearchHit::getHref).map(href -> {
					try {
						return helpSystem.resolve(href, false).toURI();
					} catch (Exception e) {
						hrefErrors
								.add(new IllegalStateException("Error resolving '" + href + "': " + e.getMessage(), e));
						return null;
					}
				}).filter(Objects::nonNull).sorted().peek(System.err::println).forEach(indexedPagesURIs::add);
			}
		};
		BaseHelpSystem.getSearchManager().search(query, collector, new NullProgressMonitor());

		if (!hrefErrors.isEmpty()) {
			Exception first = hrefErrors.iterator().next();
			throw new AssertionError(first.getMessage(), first);
		}

		Set<String> linkFailures = Collections.synchronizedSet(new TreeSet<>());
		Set<Exception> ex = Collections.synchronizedSet(new LinkedHashSet<>());
		Set<URI> allKnownPageURIs = Collections.synchronizedSet(new TreeSet<>(indexedPagesURIs));
		indexedPagesURIs.parallelStream().forEach(t -> {
			String path = t.getPath();
			if (path.lastIndexOf('/') > 0) {
				path = path.substring(path.lastIndexOf('/'));
			}
			boolean notFile = false;
			if (!path.contains(".")) {
				notFile = true;
				System.out.println("Not a file?: " + t);
			}
			try (InputStream stream = t.toURL().openStream()) {
				linkFailures.addAll(checkLinks(stream, t, allKnownPageURIs));
			} catch (IOException e) {
				if (!notFile) {
					ex.add(e);
				}
			}
		});
		assertThat(ex).isEmpty();
		assertThat(linkFailures).isEmpty();
	}

	private static final Pattern HREF = Pattern.compile("<a href=\"([^\"]+)\"");

	private Set<String> checkLinks(InputStream stream, URI currentDoc, Set<URI> knownPagesURIs) throws IOException {
		Set<String> res = new HashSet<>();
		try (BufferedReader in = new BufferedReader(new InputStreamReader(stream))) {
			for (String inputLine; (inputLine = in.readLine()) != null;) {
				for (Matcher matcher = HREF.matcher(inputLine); matcher.find();) {
					URI href = URI.create(matcher.group(1).replace(" ", "%20"));
					if (href.isAbsolute()) {
						continue;
					}
					URI linkURI = URI.create(currentDoc.toString() + "/../" + href).normalize();
					if (knownPagesURIs.contains(linkURI)) { // page already indexed or successfully visited
						// we already know this help page exists as it is indexed
						continue;
					} else { // page isn't indexed: can be generated navigation page
						// check whether it's existing anyway
						HttpURLConnection connection = (HttpURLConnection) linkURI.toURL().openConnection();
						connection.setRequestMethod("HEAD");
						connection.connect();
						if (connection.getResponseCode() != 200) {
							res.add("Link from " + currentDoc + " to " + href + " is broken: target URI " + linkURI
									+ " doens't exist.");
						} else {
							knownPagesURIs.add(linkURI);
						}
					}
				}
			}
		}
		return res;
	}

}
