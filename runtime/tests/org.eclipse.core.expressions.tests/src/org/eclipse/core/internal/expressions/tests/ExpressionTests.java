/*******************************************************************************
 * Copyright (c) 2000, 2024 IBM Corporation and others.
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
package org.eclipse.core.internal.expressions.tests;

import static java.util.function.Predicate.not;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;
import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.osgi.framework.FrameworkUtil;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import org.eclipse.core.expressions.AndExpression;
import org.eclipse.core.expressions.CountExpression;
import org.eclipse.core.expressions.EqualsExpression;
import org.eclipse.core.expressions.EvaluationContext;
import org.eclipse.core.expressions.EvaluationResult;
import org.eclipse.core.expressions.Expression;
import org.eclipse.core.expressions.ExpressionConverter;
import org.eclipse.core.expressions.IEvaluationContext;
import org.eclipse.core.expressions.IVariableResolver;
import org.eclipse.core.expressions.OrExpression;
import org.eclipse.core.expressions.TestExpression;
import org.eclipse.core.expressions.WithExpression;
import org.eclipse.core.internal.expressions.AdaptExpression;
import org.eclipse.core.internal.expressions.EnablementExpression;
import org.eclipse.core.internal.expressions.ExpressionStatus;
import org.eclipse.core.internal.expressions.Expressions;
import org.eclipse.core.internal.expressions.InstanceofExpression;
import org.eclipse.core.internal.expressions.IterateExpression;
import org.eclipse.core.internal.expressions.NotExpression;
import org.eclipse.core.internal.expressions.ResolveExpression;
import org.eclipse.core.internal.expressions.SystemTestExpression;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.Platform;


@SuppressWarnings("restriction")
public class ExpressionTests {

	private static final int TYPE_ITERATIONS = 100000;

	public static class CollectionWrapper {
		public Collection<String> collection;
	}

	@Test
	public void testEscape() throws Exception {
		assertEquals("Str'ing", Expressions.unEscapeString("Str''ing")); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals("'", Expressions.unEscapeString("''")); //$NON-NLS-1$ //$NON-NLS-2$
		assertThrows(CoreException.class, () -> Expressions.unEscapeString("'")); //$NON-NLS-1$
	}

	@Test
	public void testArgumentConversion() throws Exception {
		assertThat(Expressions.convertArgument(null)).isNull();
		assertEquals("", Expressions.convertArgument("")); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals("", Expressions.convertArgument("''")); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals("eclipse", Expressions.convertArgument("eclipse")); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals("e'clips'e", Expressions.convertArgument("e'clips'e")); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals("eclipse", Expressions.convertArgument("'eclipse'")); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals("'ecl'ipse'", Expressions.convertArgument("'''ecl''ipse'''")); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals("true", Expressions.convertArgument("'true'")); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals("1.7", Expressions.convertArgument("'1.7'")); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals("007", Expressions.convertArgument("'007'")); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals(Boolean.TRUE, Expressions.convertArgument("true")); //$NON-NLS-1$
		assertEquals(Boolean.FALSE, Expressions.convertArgument("false")); //$NON-NLS-1$
		assertEquals(Integer.valueOf(100), Expressions.convertArgument("100")); //$NON-NLS-1$
		assertEquals(Float.valueOf(1.7f), Expressions.convertArgument("1.7")); //$NON-NLS-1$
	}

	@Test
	public void testArgumentParsing() throws Exception {
		Object[] result= null;

		result= Expressions.parseArguments(""); //$NON-NLS-1$
		assertEquals("", result[0]); //$NON-NLS-1$

		result= Expressions.parseArguments("s1"); //$NON-NLS-1$
		assertEquals("s1", result[0]); //$NON-NLS-1$

		result= Expressions.parseArguments(" s1 "); //$NON-NLS-1$
		assertEquals("s1", result[0]); //$NON-NLS-1$

		result= Expressions.parseArguments("s1,s2"); //$NON-NLS-1$
		assertEquals("s1", result[0]); //$NON-NLS-1$
		assertEquals("s2", result[1]); //$NON-NLS-1$

		result= Expressions.parseArguments(" s1 , s2 "); //$NON-NLS-1$
		assertEquals("s1", result[0]); //$NON-NLS-1$
		assertEquals("s2", result[1]); //$NON-NLS-1$

		result= Expressions.parseArguments("' s1 ',' s2 '"); //$NON-NLS-1$
		assertEquals(" s1 ", result[0]); //$NON-NLS-1$
		assertEquals(" s2 ", result[1]); //$NON-NLS-1$

		result= Expressions.parseArguments(" s1 , ' s2 '"); //$NON-NLS-1$
		assertEquals("s1", result[0]); //$NON-NLS-1$
		assertEquals(" s2 ", result[1]); //$NON-NLS-1$

		result= Expressions.parseArguments("' s1 ', s2 "); //$NON-NLS-1$
		assertEquals(" s1 ", result[0]); //$NON-NLS-1$
		assertEquals("s2", result[1]); //$NON-NLS-1$

		result= Expressions.parseArguments("''''"); //$NON-NLS-1$
		assertEquals("'", result[0]); //$NON-NLS-1$

		result= Expressions.parseArguments("''',''',','"); //$NON-NLS-1$
		assertEquals("','", result[0]);		 //$NON-NLS-1$
		assertEquals(",", result[1]); //$NON-NLS-1$

		result= Expressions.parseArguments("' s1 ', true "); //$NON-NLS-1$
		assertEquals(" s1 ", result[0]); //$NON-NLS-1$
		assertEquals(Boolean.TRUE, result[1]);

		assertThrows(CoreException.class, () ->Expressions.parseArguments("' s1")); //$NON-NLS-1$
		assertThrows(CoreException.class, () -> Expressions.parseArguments("'''s1")); //$NON-NLS-1$
	}

	@Test
	public void testSystemProperty() throws Exception {
		SystemTestExpression expression= new SystemTestExpression("os.name", System.getProperty("os.name")); //$NON-NLS-1$ //$NON-NLS-2$
		EvaluationResult result= expression.evaluate(new EvaluationContext(null, new Object()));
		assertThat(result).isEqualTo(EvaluationResult.TRUE);
	}

	@Test
	public void testAdaptExpression() throws Exception {
		AdaptExpression expression= new AdaptExpression("org.eclipse.core.internal.expressions.tests.Adapter"); //$NON-NLS-1$
		expression.add(new InstanceofExpression("org.eclipse.core.internal.expressions.tests.Adapter")); //$NON-NLS-1$
		EvaluationResult result= expression.evaluate(new EvaluationContext(null, new Adaptee()));
		assertThat(result).isEqualTo(EvaluationResult.TRUE);
	}

	@Test
	public void testAdaptExpressionAdaptable() throws Exception {
		AdaptExpression expression= new AdaptExpression("org.eclipse.core.internal.expressions.tests.Adapter"); //$NON-NLS-1$
		expression.add(new InstanceofExpression("org.eclipse.core.internal.expressions.tests.Adapter")); //$NON-NLS-1$
		EvaluationResult result= expression.evaluate(new EvaluationContext(null, new AdaptableAdaptee()));
		assertThat(result).isEqualTo(EvaluationResult.TRUE);
	}

	@Test
	public void testAdaptExpressionNotEqual() throws Exception {
		AdaptExpression expression1 = new AdaptExpression(
				"org.eclipse.core.internal.expressions.tests.Adapter"); //$NON-NLS-1$
		expression1.add(new InstanceofExpression(
				"org.eclipse.core.internal.expressions.tests.Adapter1")); //$NON-NLS-1$
		AdaptExpression expression2 = new AdaptExpression(
				"org.eclipse.core.internal.expressions.tests.Adapter"); //$NON-NLS-1$
		expression2.add(new InstanceofExpression(
				"org.eclipse.core.internal.expressions.tests.Adapter2")); //$NON-NLS-1$
		assertThat(expression1).isNotEqualTo(expression2);
	}

	@Test
	public void testAdaptExpressionHashCode() throws Exception {
		AdaptExpression expression1 = new AdaptExpression(
				"org.eclipse.core.internal.expressions.tests.Adapter"); //$NON-NLS-1$
		expression1.add(new InstanceofExpression(
				"org.eclipse.core.internal.expressions.tests.Adapter")); //$NON-NLS-1$
		AdaptExpression expression2 = new AdaptExpression(
				"org.eclipse.core.internal.expressions.tests.Adapter"); //$NON-NLS-1$
		expression2.add(new InstanceofExpression(
				"org.eclipse.core.internal.expressions.tests.Adapter")); //$NON-NLS-1$
		assertThat(expression1.hashCode()).as("Equal expressions should have the same hash code")
				.isEqualTo(expression2.hashCode());
	}

	@Test
	public void testAdaptExpressionFail() throws Exception {
		AdaptExpression expression= new AdaptExpression("org.eclipse.core.internal.expressions.tests.NotExisting"); //$NON-NLS-1$
		EvaluationResult result= expression.evaluate(new EvaluationContext(null, new Adaptee()));
		assertThat(result).isEqualTo(EvaluationResult.FALSE);
	}

	@Test
	public void testAdaptExpressionFail2() throws Exception {
		AdaptExpression expression= new AdaptExpression("org.eclipse.core.internal.expressions.tests.Adapter"); //$NON-NLS-1$
		expression.add(new InstanceofExpression("org.eclipse.core.internal.expressions.tests.NotExisting")); //$NON-NLS-1$
		EvaluationResult result= expression.evaluate(new EvaluationContext(null, new Adaptee()));
		assertThat(result).isEqualTo(EvaluationResult.FALSE);
	}

	/* Bug 484325 */
	@Test
	public void testAdaptExpressionWithNull() throws Exception {
		// it's surprisingly difficult to craft an EvaluationContext that
		// provides
		// a defaultVariable == null
		IEvaluationContext testContext = new EvaluationContext(null, new Adaptee());
		testContext.addVariable("nullCarrier", Arrays.asList((Object) null, (Object) null, (Object) null));

		WithExpression withExpression = new WithExpression("nullCarrier");
		IterateExpression iterateExpression = new IterateExpression("and");
		iterateExpression.add(new AdaptExpression("org.eclipse.core.internal.expressions.tests.NotExisting"));
		withExpression.add(iterateExpression);
		EvaluationResult result = withExpression.evaluate(testContext);
		assertThat(result).isEqualTo(EvaluationResult.FALSE);
	}

	@Test
	public void testAndExpressionNotEqual() throws Exception {
		AndExpression expression1 = new AndExpression();
		expression1.add(new InstanceofExpression(
				"org.eclipse.core.internal.expressions.tests.Adapter1")); //$NON-NLS-1$
		AndExpression expression2 = new AndExpression();
		expression2.add(new InstanceofExpression(
				"org.eclipse.core.internal.expressions.tests.Adapter2")); //$NON-NLS-1$
		assertThat(expression1).isNotEqualTo(expression2);
	}

	@Test
	public void testAndExpressionHashCode() throws Exception {
		AndExpression expression1 = new AndExpression();
		expression1.add(new InstanceofExpression(
				"org.eclipse.core.internal.expressions.tests.Adapter")); //$NON-NLS-1$
		AndExpression expression2 = new AndExpression();
		expression2.add(new InstanceofExpression(
				"org.eclipse.core.internal.expressions.tests.Adapter")); //$NON-NLS-1$
		assertThat(expression1.hashCode()).as("Equal expressions should have the same hash code")
				.isEqualTo(expression2.hashCode());
	}

	@Test
	public void testCountExpressionNotEqual() throws Exception {
		CountExpression expression1 = new CountExpression("+");
		CountExpression expression2 = new CountExpression("!");
		assertThat(expression1).isNotEqualTo(expression2);
	}

	@Test
	public void testCountExpressionHashCode() throws Exception {
		CountExpression expression1 = new CountExpression("*");
		CountExpression expression2 = new CountExpression("*");
		assertThat(expression1.hashCode()).as("Equal expressions should have the same hash code")
				.isEqualTo(expression2.hashCode());
	}

	@Test
	public void testEnablementExpressionNotEqual() throws Exception {
		EnablementExpression expression1 = new EnablementExpression((IConfigurationElement)null);
		expression1.add(new InstanceofExpression(
				"org.eclipse.core.internal.expressions.tests.Adapter1")); //$NON-NLS-1$
		EnablementExpression expression2 = new EnablementExpression((IConfigurationElement)null);
		expression2.add(new InstanceofExpression(
				"org.eclipse.core.internal.expressions.tests.Adapter2")); //$NON-NLS-1$
		assertThat(expression1).isNotEqualTo(expression2);
	}

	@Test
	public void testEnablementExpressionHashCode() throws Exception {
		EnablementExpression expression1 = new EnablementExpression((IConfigurationElement)null);
		expression1.add(new InstanceofExpression(
				"org.eclipse.core.internal.expressions.tests.Adapter")); //$NON-NLS-1$
		EnablementExpression expression2 = new EnablementExpression((IConfigurationElement)null);
		expression2.add(new InstanceofExpression(
				"org.eclipse.core.internal.expressions.tests.Adapter")); //$NON-NLS-1$
		assertThat(expression1.hashCode()).as("Equal expressions should have the same hash code")
				.isEqualTo(expression2.hashCode());
	}

	@Test
	public void testEqualsExpressionNotEqual() throws Exception {
		EqualsExpression expression1 = new EqualsExpression("+");
		EqualsExpression expression2 = new EqualsExpression("!");
		assertThat(expression1).isNotEqualTo(expression2);
	}

	@Test
	public void testEqualsExpressionHashCode() throws Exception {
		EqualsExpression expression1 = new EqualsExpression("*");
		EqualsExpression expression2 = new EqualsExpression("*");
		assertThat(expression1.hashCode()).as("Equal expressions should have the same hash code")
				.isEqualTo(expression2.hashCode());
	}

	@Test
	public void testInstanceOfExpressionNotEqual() throws Exception {
		InstanceofExpression expression1 = new InstanceofExpression("+");
		InstanceofExpression expression2 = new InstanceofExpression("!");
		assertThat(expression1).isNotEqualTo(expression2);
	}

	@Test
	public void testInstanceOfExpressionHashCode() throws Exception {
		InstanceofExpression expression1 = new InstanceofExpression("*");
		InstanceofExpression expression2 = new InstanceofExpression("*");
		assertThat(expression1.hashCode()).as("Equal expressions should have the same hash code")
				.isEqualTo(expression2.hashCode());
	}

	@Test
	public void testIterateExpressionNotEqual() throws Exception {
		IterateExpression expression1 = new IterateExpression("or");
		expression1.add(new InstanceofExpression(
				"org.eclipse.core.internal.expressions.tests.Adapter")); //$NON-NLS-1$
		IterateExpression expression2 = new IterateExpression("and");
		expression2.add(new InstanceofExpression(
				"org.eclipse.core.internal.expressions.tests.Adapter")); //$NON-NLS-1$
		assertThat(expression1).isNotEqualTo(expression2);
	}

	@Test
	public void testIterateExpressionHashCode() throws Exception {
		IterateExpression expression1 = new IterateExpression("and");
		expression1.add(new InstanceofExpression(
				"org.eclipse.core.internal.expressions.tests.Adapter")); //$NON-NLS-1$
		IterateExpression expression2 = new IterateExpression("and");
		expression2.add(new InstanceofExpression(
				"org.eclipse.core.internal.expressions.tests.Adapter")); //$NON-NLS-1$
		assertThat(expression1.hashCode()).as("Equal expressions should have the same hash code")
				.isEqualTo(expression2.hashCode());
	}

	@Test
	public void testNotExpressionNotEqual() throws Exception {
		NotExpression expression1 = new NotExpression(new InstanceofExpression(
				"org.eclipse.core.internal.expressions.tests.Adapter1"));
		NotExpression expression2 = new NotExpression(new InstanceofExpression(
				"org.eclipse.core.internal.expressions.tests.Adapter2"));
		assertThat(expression1).isNotEqualTo(expression2);
	}

	@Test
	public void testNotExpressionHashCode() throws Exception {
		NotExpression expression1 = new NotExpression(new InstanceofExpression(
				"org.eclipse.core.internal.expressions.tests.Adapter"));
		NotExpression expression2 = new NotExpression(new InstanceofExpression(
				"org.eclipse.core.internal.expressions.tests.Adapter"));
		assertThat(expression1.hashCode()).as("Equal expressions should have the same hash code")
				.isEqualTo(expression2.hashCode());
	}

	@Test
	public void testOrExpressionNotEqual() throws Exception {
		OrExpression expression1 = new OrExpression();
		expression1.add(new InstanceofExpression(
				"org.eclipse.core.internal.expressions.tests.Adapter1")); //$NON-NLS-1$
		OrExpression expression2 = new OrExpression();
		expression2.add(new InstanceofExpression(
				"org.eclipse.core.internal.expressions.tests.Adapter2")); //$NON-NLS-1$
		assertThat(expression1).isNotEqualTo(expression2);
	}

	@Test
	public void testOrExpressionHashCode() throws Exception {
		OrExpression expression1 = new OrExpression();
		expression1.add(new InstanceofExpression(
				"org.eclipse.core.internal.expressions.tests.Adapter")); //$NON-NLS-1$
		OrExpression expression2 = new OrExpression();
		expression2.add(new InstanceofExpression(
				"org.eclipse.core.internal.expressions.tests.Adapter")); //$NON-NLS-1$
		assertThat(expression1.hashCode()).as("Equal expressions should have the same hash code")
				.isEqualTo(expression2.hashCode());
	}

	@Test
	public void testResolveExpressionNotEqual() throws Exception {
		ResolveExpression expression1 = new ResolveExpression("variable1",
				new Object[0]);
		expression1.add(new InstanceofExpression(
				"org.eclipse.core.internal.expressions.tests.Adapter1")); //$NON-NLS-1$
		ResolveExpression expression2 = new ResolveExpression("variable2",
				new Object[0]);
		expression2.add(new InstanceofExpression(
				"org.eclipse.core.internal.expressions.tests.Adapter2")); //$NON-NLS-1$
		assertThat(expression1).isNotEqualTo(expression2);
	}

	@Test
	public void testResolveExpressionHashCode() throws Exception {
		ResolveExpression expression1 = new ResolveExpression("variable",
				new Object[0]);
		expression1.add(new InstanceofExpression(
				"org.eclipse.core.internal.expressions.tests.Adapter")); //$NON-NLS-1$
		ResolveExpression expression2 = new ResolveExpression("variable",
				new Object[0]);
		expression2.add(new InstanceofExpression(
				"org.eclipse.core.internal.expressions.tests.Adapter")); //$NON-NLS-1$
		assertThat(expression1.hashCode()).as("Equal expressions should have the same hash code")
				.isEqualTo(expression2.hashCode());
	}

	@Test
	public void testSystemTestExpressionNotEqual() throws Exception {
		SystemTestExpression expression1 = new SystemTestExpression("prop",
				"value1");
		SystemTestExpression expression2 = new SystemTestExpression("prop",
				"value2");
		assertThat(expression1).isNotEqualTo(expression2);
	}

	@Test
	public void testSystemTestExpressionHashCode() throws Exception {
		SystemTestExpression expression1 = new SystemTestExpression("prop",
				"value");
		SystemTestExpression expression2 = new SystemTestExpression("prop",
				"value");
		assertThat(expression1.hashCode()).as("Equal expressions should have the same hash code")
				.isEqualTo(expression2.hashCode());
	}

	@Test
	public void testTestExpressionNotEqual() throws Exception {
		TestExpression expression1 = new TestExpression("namespace", "prop",
				new Object[0], "value1");
		TestExpression expression2 = new TestExpression("namespace", "prop",
				new Object[0], "value2");
		assertThat(expression1).isNotEqualTo(expression2);
	}

	@Test
	public void testTestExpressionHashCode() throws Exception {
		TestExpression expression1 = new TestExpression("namespace", "prop",
				new Object[0], "value");
		TestExpression expression2 = new TestExpression("namespace", "prop",
				new Object[0], "value");
		assertThat(expression1.hashCode()).as("Equal expressions should have the same hash code")
				.isEqualTo(expression2.hashCode());
	}

	@Test
	public void testWithExpressionNotEqual() throws Exception {
		WithExpression expression1 = new WithExpression("variable1");
		expression1.add(new InstanceofExpression(
				"org.eclipse.core.internal.expressions.tests.Adapter1")); //$NON-NLS-1$
		WithExpression expression2 = new WithExpression("variable2");
		expression2.add(new InstanceofExpression(
				"org.eclipse.core.internal.expressions.tests.Adapter2")); //$NON-NLS-1$
		assertThat(expression1).isNotEqualTo(expression2);
	}

	@Test
	public void testWithExpressionHashCode() throws Exception {
		WithExpression expression1 = new WithExpression("variable");
		expression1.add(new InstanceofExpression(
				"org.eclipse.core.internal.expressions.tests.Adapter")); //$NON-NLS-1$
		WithExpression expression2 = new WithExpression("variable");
		expression2.add(new InstanceofExpression(
				"org.eclipse.core.internal.expressions.tests.Adapter")); //$NON-NLS-1$
		assertThat(expression1.hashCode()).as("Equal expressions should have the same hash code")
				.isEqualTo(expression2.hashCode());
	}

	@Test
	public void testWithExpressionNoVariable() throws Exception {
		WithExpression expr = new WithExpression("variable");
		expr.add(new EqualsExpression(new Object()));
		EvaluationContext context = new EvaluationContext(null, new Object());
		assertThrows(CoreException.class, () -> expr.evaluate(context));
	}

	@Test
	public void testWithExpressionUndefinedVariable() throws Exception {
		WithExpression expr = new WithExpression("variable");
		expr.add(new EqualsExpression(new Object()));
		EvaluationContext context = new EvaluationContext(null, new Object());
		context.addVariable("variable", IEvaluationContext.UNDEFINED_VARIABLE);
		assertThat(expr.evaluate(context)).isEqualTo(EvaluationResult.FALSE);
	}

	@Test
	public void testVariableResolver() throws Exception {
		final Object result= new Object();
		IVariableResolver resolver = (name, args) -> {
			assertEquals("variable", name); //$NON-NLS-1$
			assertEquals("arg1", args[0]); //$NON-NLS-1$
			assertEquals(Boolean.TRUE, args[1]);
			return result;
		};
		EvaluationContext context= new EvaluationContext(null, new Object(), new IVariableResolver[] { resolver });
		assertThat(result).isEqualTo(context.resolveVariable("variable", new Object[] { "arg1", Boolean.TRUE })); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Test
	public void testEqualsExpression() throws Exception {
		EqualsExpression exp= new EqualsExpression("name"); //$NON-NLS-1$
		EvaluationContext context= new EvaluationContext(null, "name"); //$NON-NLS-1$
		assertThat(exp.evaluate(context)).isEqualTo(EvaluationResult.TRUE);

		exp= new EqualsExpression(Boolean.TRUE);
		context= new EvaluationContext(null, Boolean.TRUE);
		assertThat(exp.evaluate(context)).isEqualTo(EvaluationResult.TRUE);

		exp= new EqualsExpression("name"); //$NON-NLS-1$
		context= new EvaluationContext(null, Boolean.TRUE);
		assertThat(exp.evaluate(context)).isEqualTo(EvaluationResult.FALSE);
	}

	@Test
	public void testCountExpressionAnyNumber() throws Exception {
		CountExpression exp= new CountExpression("*"); //$NON-NLS-1$

		EvaluationContext context = new EvaluationContext(null, List.of());
		assertThat(exp.evaluate(context)).isEqualTo(EvaluationResult.TRUE);

		context = new EvaluationContext(null, List.of("one"));
		assertThat(exp.evaluate(context)).isEqualTo(EvaluationResult.TRUE);

		context = new EvaluationContext(null, List.of("one", "two", "three"));
		assertThat(exp.evaluate(context)).isEqualTo(EvaluationResult.TRUE);
	}

	@Test
	public void testCountExpressionExact() throws Exception {
		CountExpression exp= new CountExpression("2"); //$NON-NLS-1$

		EvaluationContext context = new EvaluationContext(null, List.of("one"));
		assertThat(exp.evaluate(context)).isEqualTo(EvaluationResult.FALSE);

		context = new EvaluationContext(null, List.of("one", "two"));
		assertThat(exp.evaluate(context)).isEqualTo(EvaluationResult.TRUE);

		context = new EvaluationContext(null, List.of("one", "two", "three"));
		assertThat(exp.evaluate(context)).isEqualTo(EvaluationResult.FALSE);
	}

	@Test
	public void testCountExpressionNoneOrOne() throws Exception {
		CountExpression exp= new CountExpression("?"); //$NON-NLS-1$

		EvaluationContext context = new EvaluationContext(null, List.of());
		assertThat(exp.evaluate(context)).isEqualTo(EvaluationResult.TRUE);

		context = new EvaluationContext(null, List.of("one"));
		assertThat(exp.evaluate(context)).isEqualTo(EvaluationResult.TRUE);

		context = new EvaluationContext(null, List.of("one", "two"));
		assertThat(exp.evaluate(context)).isEqualTo(EvaluationResult.FALSE);
	}

	@Test
	public void testCountExpressionOneOrMore() throws Exception {
		CountExpression exp= new CountExpression("+"); //$NON-NLS-1$

		EvaluationContext context = new EvaluationContext(null, List.of());
		assertThat(exp.evaluate(context)).isEqualTo(EvaluationResult.FALSE);

		context = new EvaluationContext(null, List.of("one"));
		assertThat(exp.evaluate(context)).isEqualTo(EvaluationResult.TRUE);

		context = new EvaluationContext(null, List.of("one", "two"));
		assertThat(exp.evaluate(context)).isEqualTo(EvaluationResult.TRUE);
	}

	@Test
	public void testCountExpressionNone() throws Exception {
		CountExpression exp= new CountExpression("!"); //$NON-NLS-1$

		EvaluationContext context = new EvaluationContext(null, List.of());
		assertThat(exp.evaluate(context)).isEqualTo(EvaluationResult.TRUE);

		context = new EvaluationContext(null, List.of("one"));
		assertThat(exp.evaluate(context)).isEqualTo(EvaluationResult.FALSE);

		context = new EvaluationContext(null, List.of("one", "two"));
		assertThat(exp.evaluate(context)).isEqualTo(EvaluationResult.FALSE);
	}

	@Test
	public void testCountExpressionNoneWithAdapterManager() throws Exception {
		CountExpression exp= new CountExpression("!"); //$NON-NLS-1$

		List<String> list= new ArrayList<>();
		CollectionWrapper wrapper= new CollectionWrapper();
		wrapper.collection= list;

		EvaluationContext context= new EvaluationContext(null, wrapper);
		assertThat(exp.evaluate(context)).isEqualTo(EvaluationResult.TRUE);

		list.clear();
		list.add("one"); //$NON-NLS-1$
		context= new EvaluationContext(null, wrapper);
		assertThat(exp.evaluate(context)).isEqualTo(EvaluationResult.FALSE);

		list.clear();
		list.add("one"); //$NON-NLS-1$
		list.add("two"); //$NON-NLS-1$
		context= new EvaluationContext(null, wrapper);
		assertThat(exp.evaluate(context)).isEqualTo(EvaluationResult.FALSE);
	}

	@Test
	public void testCountExpressionFailure() throws Exception {
		CountExpression exp= new CountExpression("!"); //$NON-NLS-1$

		EvaluationContext context= new EvaluationContext(null, new Object());
		try {
			EvaluationResult result= exp.evaluate(context);
			fail("Count should've failed for non-Collection variable.  Result = " + result);
		} catch (CoreException e) {
			assertEquals(ExpressionStatus.VARIABLE_IS_NOT_A_COLLECTION, e.getStatus().getCode());
		}
	}

	@Test
	public void testInstanceofTrue() throws Exception {
		B b= new B();
		EvaluationContext context= new EvaluationContext(null, b);

		InstanceofExpression exp= new InstanceofExpression("org.eclipse.core.internal.expressions.tests.B"); //$NON-NLS-1$
		assertThat(exp.evaluate(context)).isEqualTo(EvaluationResult.TRUE);

		exp= new InstanceofExpression("org.eclipse.core.internal.expressions.tests.A"); //$NON-NLS-1$
		assertThat(exp.evaluate(context)).isEqualTo(EvaluationResult.TRUE);

		exp= new InstanceofExpression("org.eclipse.core.internal.expressions.tests.I"); //$NON-NLS-1$
		assertThat(exp.evaluate(context)).isEqualTo(EvaluationResult.TRUE);
	}

	@Test
	public void testInstanceofFalse() throws Exception {
		A a= new A();
		EvaluationContext context= new EvaluationContext(null, a);

		InstanceofExpression exp= new InstanceofExpression("org.eclipse.core.internal.expressions.tests.B"); //$NON-NLS-1$
		assertThat(exp.evaluate(context)).isEqualTo(EvaluationResult.FALSE);
	}

	@Test
	public void testIterateExpressionAndTrue() throws Exception {
		final List<Object> result= new ArrayList<>();
		Expression myExpression= new Expression() {
			@Override
			public EvaluationResult evaluate(IEvaluationContext context) throws CoreException {
				result.add(context.getDefaultVariable());
				return EvaluationResult.TRUE;
			}
		};
		IterateExpression exp= new IterateExpression("and"); //$NON-NLS-1$
		exp.add(myExpression);
		List<String> input = List.of("one", "two"); //$NON-NLS-1$
		EvaluationContext context= new EvaluationContext(null, input);
		assertThat(exp.evaluate(context)).isEqualTo(EvaluationResult.TRUE);
		assertThat(result).isEqualTo(input);
	}

	@Test
	public void testIterateExpressionAndFalse() throws Exception {
		final List<Object> result= new ArrayList<>();
		Expression myExpression= new Expression() {
			@Override
			public EvaluationResult evaluate(IEvaluationContext context) throws CoreException {
				result.add(context.getDefaultVariable());
				return EvaluationResult.FALSE;
			}
		};
		IterateExpression exp= new IterateExpression("and"); //$NON-NLS-1$
		exp.add(myExpression);
		EvaluationContext context = new EvaluationContext(null, List.of("one", "two"));
		assertThat(exp.evaluate(context)).isEqualTo(EvaluationResult.FALSE);
		assertThat(result).singleElement().isEqualTo("one"); //$NON-NLS-1$
	}

	@Test
	public void testIterateExpressionOrTrue() throws Exception {
		final List<Object> result= new ArrayList<>();
		Expression myExpression= new Expression() {
			@Override
			public EvaluationResult evaluate(IEvaluationContext context) throws CoreException {
				result.add(context.getDefaultVariable());
				return EvaluationResult.TRUE;
			}
		};
		IterateExpression exp= new IterateExpression("or"); //$NON-NLS-1$
		exp.add(myExpression);
		EvaluationContext context = new EvaluationContext(null, List.of("one", "two"));
		assertThat(exp.evaluate(context)).isEqualTo(EvaluationResult.TRUE);
		assertThat(result).singleElement().isEqualTo("one"); //$NON-NLS-1$
	}

	@Test
	public void testIterateExpressionOrFalse() throws Exception {
		final List<Object> result= new ArrayList<>();
		Expression myExpression= new Expression() {
			@Override
			public EvaluationResult evaluate(IEvaluationContext context) throws CoreException {
				result.add(context.getDefaultVariable());
				return EvaluationResult.FALSE;
			}
		};
		IterateExpression exp= new IterateExpression("or"); //$NON-NLS-1$
		exp.add(myExpression);
		List<String> input = List.of("one", "two");
		EvaluationContext context = new EvaluationContext(null, input);
		assertThat(exp.evaluate(context)).isEqualTo(EvaluationResult.FALSE);
		assertThat(result).isEqualTo(input);
	}

	@Test
	public void testIterateExpressionOrMultiChildren() throws Exception {
		//Test for Bug 260522: <iterate> iterates over collection elements,
		//thereby *and*-ing all evaluated child expressions
		IterateExpression exp= new IterateExpression("or"); //$NON-NLS-1$
		exp.add(Expression.FALSE);
		exp.add(Expression.TRUE);
		EvaluationContext context = new EvaluationContext(null, List.of("one"));
		assertThat(exp.evaluate(context)).isEqualTo(EvaluationResult.FALSE);
	}

	@Test
	public void testIterateExpressionAndMultiChildren() throws Exception {
		//Test for Bug 260522: <iterate> iterates over collection elements,
		//thereby *and*-ing all evaluated child expressions
		IterateExpression exp= new IterateExpression("and"); //$NON-NLS-1$
		exp.add(Expression.FALSE);
		exp.add(Expression.TRUE);
		EvaluationContext context = new EvaluationContext(null, List.of("one", "two"));
		assertThat(exp.evaluate(context)).isEqualTo(EvaluationResult.FALSE);
	}

	@Test
	public void testIterateExpressionEmptyOr() throws Exception {
		IterateExpression exp= new IterateExpression("or"); //$NON-NLS-1$
		EvaluationContext context = new EvaluationContext(null, List.of());
		assertThat(exp.evaluate(context)).isEqualTo(EvaluationResult.FALSE);
	}

	@Test
	public void testIterateExpressionEmptyAnd() throws Exception {
		IterateExpression exp= new IterateExpression("and"); //$NON-NLS-1$
		EvaluationContext context = new EvaluationContext(null, List.of());
		assertThat(exp.evaluate(context)).isEqualTo(EvaluationResult.TRUE);
	}

	@Test
	public void testIterateExpressionAnd_IfEmptyTrue() throws Exception {
		IterateExpression exp= new IterateExpression("and", "true"); //$NON-NLS-1$
		EvaluationContext context = new EvaluationContext(null, List.of());
		assertThat(exp.evaluate(context)).isEqualTo(EvaluationResult.TRUE);
	}

	@Test
	public void testIterateExpressionAnd_IfEmptyFalse() throws Exception {
		IterateExpression exp= new IterateExpression("and", "false"); //$NON-NLS-1$
		EvaluationContext context = new EvaluationContext(null, List.of());
		assertThat(exp.evaluate(context)).isEqualTo(EvaluationResult.FALSE);
	}

	@Test
	public void testIterateExpressionOr_IfEmptyTrue() throws Exception {
		IterateExpression exp= new IterateExpression("or", "true"); //$NON-NLS-1$
		EvaluationContext context = new EvaluationContext(null, List.of());
		assertThat(exp.evaluate(context)).isEqualTo(EvaluationResult.TRUE);
	}

	@Test
	public void testIterateExpressionOr_IfEmptyFalse() throws Exception {
		IterateExpression exp= new IterateExpression("or", "false"); //$NON-NLS-1$
		EvaluationContext context = new EvaluationContext(null, List.of());
		assertThat(exp.evaluate(context)).isEqualTo(EvaluationResult.FALSE);
	}

	@Test
	public void testIterateExpressionWithAdapterManager() throws Exception {
		final List<Object> result= new ArrayList<>();
		Expression myExpression= new Expression() {
			@Override
			public EvaluationResult evaluate(IEvaluationContext context) throws CoreException {
				result.add(context.getDefaultVariable());
				return EvaluationResult.FALSE;
			}
		};
		IterateExpression exp= new IterateExpression("or"); //$NON-NLS-1$
		exp.add(myExpression);
		final List<String> input = List.of("one", "two");
		CollectionWrapper wrapper= new CollectionWrapper();
		wrapper.collection= input;
		EvaluationContext context= new EvaluationContext(null, wrapper);
		assertThat(exp.evaluate(context)).isEqualTo(EvaluationResult.FALSE);
		assertThat(result).isEqualTo(input);
	}

	@Test
	public void testIterateExpressionWithAdapterManagerEmptyAnd() throws Exception {
		IterateExpression exp= new IterateExpression("and"); //$NON-NLS-1$
		CollectionWrapper wrapper= new CollectionWrapper();
		wrapper.collection = List.of();
		EvaluationContext context= new EvaluationContext(null, wrapper);
		assertThat(exp.evaluate(context)).isEqualTo(EvaluationResult.TRUE);
	}

	@Test
	public void testIterateExpressionWithAdapterManagerEmptyOr() throws Exception {
		IterateExpression exp= new IterateExpression("or"); //$NON-NLS-1$
		CollectionWrapper wrapper= new CollectionWrapper();
		wrapper.collection = List.of();
		EvaluationContext context= new EvaluationContext(null, wrapper);
		assertThat(exp.evaluate(context)).isEqualTo(EvaluationResult.FALSE);
	}

	@Test
	public void testIterateExpressionWithAdapterManagerIfEmptyFalse() throws Exception {
		IterateExpression exp= new IterateExpression("or", "false"); //$NON-NLS-1$
		CollectionWrapper wrapper= new CollectionWrapper();
		wrapper.collection = List.of();
		EvaluationContext context= new EvaluationContext(null, wrapper);
		assertThat(exp.evaluate(context)).isEqualTo(EvaluationResult.FALSE);
	}

	@Test
	public void testIterateExpressionWithAdapterManagerIfEmptyTrue() throws Exception {
		IterateExpression exp= new IterateExpression("or", "true"); //$NON-NLS-1$
		CollectionWrapper wrapper= new CollectionWrapper();
		wrapper.collection = List.of();
		EvaluationContext context= new EvaluationContext(null, wrapper);
		assertThat(exp.evaluate(context)).isEqualTo(EvaluationResult.TRUE);
	}

	@Test
	public void testIterateExpressionFailure() throws Exception {
		IterateExpression exp= new IterateExpression((String)null);

		EvaluationContext context= new EvaluationContext(null, new Object());
		try {
			EvaluationResult result= exp.evaluate(context);
			fail("Count should've failed for non-Collection variable.  Result = " + result);
		} catch (CoreException e) {
			assertEquals(ExpressionStatus.VARIABLE_IS_NOT_A_COLLECTION, e.getStatus().getCode());
		}
	}

	@Test
	public void testReadXMLExpression() throws Exception {
		IExtensionRegistry registry= Platform.getExtensionRegistry();
		IConfigurationElement[] ces= registry.getConfigurationElementsFor("org.eclipse.core.expressions.tests", "testParticipants"); //$NON-NLS-1$ //$NON-NLS-2$

		IConfigurationElement enable= findExtension(ces, "test1").getChildren("enablement")[0]; //$NON-NLS-1$ //$NON-NLS-2$
		ExpressionConverter.getDefault().perform(enable);
	}

	@Test
	public void testReadDOMExpression() throws Exception {
		IExtensionRegistry registry= Platform.getExtensionRegistry();
		IConfigurationElement[] ces= registry.getConfigurationElementsFor("org.eclipse.core.expressions.tests", "testParticipants"); //$NON-NLS-1$ //$NON-NLS-2$

		URL url = FrameworkUtil.getBundle(ExpressionTests.class).getEntry("plugin.xml");
		Document document;
		try (var in = url.openStream()) {
			document = org.eclipse.core.internal.runtime.XmlProcessorFactory.parseWithErrorOnDOCTYPE(in);
		}
		NodeList testParticipants= document.getElementsByTagName("testParticipant");
		for (int i= 0; i < testParticipants.getLength(); i++) {
			Element elem= (Element)testParticipants.item(i);
			String id = elem.getAttribute("id");
			Element enable1= (Element)elem.getElementsByTagName("enablement").item(0);
			IConfigurationElement enable2= findExtension(ces, id).getChildren("enablement")[0]; //$NON-NLS-1$

			Expression exp1= ExpressionConverter.getDefault().perform(enable1);
			Expression exp2= ExpressionConverter.getDefault().perform(enable2);
			assertEquals(exp1, exp2);
		}
	}

	@Test
	public void testForcePluginActivation() throws Exception {
		IExtensionRegistry registry= Platform.getExtensionRegistry();
		IConfigurationElement[] ces= registry.getConfigurationElementsFor("org.eclipse.core.expressions.tests", "testParticipants"); //$NON-NLS-1$ //$NON-NLS-2$

		IConfigurationElement enable= findExtension(ces, "test2").getChildren("enablement")[0]; //$NON-NLS-1$ //$NON-NLS-2$
		EnablementExpression exp= (EnablementExpression) ExpressionConverter.getDefault().perform(enable);
		Expression[] children= exp.getChildren();
		assertThat(children).hasSize(3).allSatisfy(child -> assertThat(child).isInstanceOf(TestExpression.class));
		assertThat((TestExpression) children[0]).matches(TestExpression::testGetForcePluginActivation);
		assertThat((TestExpression) children[1]).matches(not(TestExpression::testGetForcePluginActivation));
		assertThat((TestExpression) children[2]).matches(not(TestExpression::testGetForcePluginActivation));
	}

	@Test
	public void testPlatformPropertyTester() throws Exception {
		IExtensionRegistry registry= Platform.getExtensionRegistry();
		IConfigurationElement[] ces= registry.getConfigurationElementsFor("org.eclipse.core.expressions.tests", "testParticipants"); //$NON-NLS-1$ //$NON-NLS-2$

		IConfigurationElement enable= findExtension(ces, "test3").getChildren("enablement")[0]; //$NON-NLS-1$ //$NON-NLS-2$
		Expression exp= ExpressionConverter.getDefault().perform(enable);
		EvaluationContext context = new EvaluationContext(null, Platform.class);
		assertEquals(EvaluationResult.TRUE, exp.evaluate(context));
	}

	private IConfigurationElement findExtension(IConfigurationElement[] ces, String id) {
		for (IConfigurationElement ce : ces) {
			if (id.equals(ce.getAttribute("id"))) //$NON-NLS-1$
				return ce;
		}
		return null;
	}

	@Test
	public void testDefinitionExpression() throws Exception {
		IExtensionRegistry registry= Platform.getExtensionRegistry();
		IConfigurationElement[] ces= registry.getConfigurationElementsFor("org.eclipse.core.expressions", "definitions");
		IConfigurationElement expr= findExtension(ces, "org.eclipse.core.expressions.tests.activeProblemsView");
		assertNotNull(expr);
		Expression probExpr= ExpressionConverter.getDefault().perform(expr.getChildren()[0]);
		EvaluationContext context = new EvaluationContext(null, Collections.EMPTY_LIST);
		assertThatThrownBy(() -> probExpr.evaluate(context), "Should report error with no variable",
				CoreException.class);

		context.addVariable("activePartId", "org.eclipse.ui.views.TasksView");
		assertEquals(EvaluationResult.FALSE, probExpr.evaluate(context));

		context.addVariable("activePartId", "org.eclipse.ui.views.ProblemsView");
		assertEquals(EvaluationResult.TRUE, probExpr.evaluate(context));
	}

	@Test
	public void testReferenceExpression() throws Exception {
		IExtensionRegistry registry= Platform.getExtensionRegistry();
		IConfigurationElement[] ces= registry.getConfigurationElementsFor("org.eclipse.core.expressions.tests", "testParticipants"); //$NON-NLS-1$ //$NON-NLS-2$

		IConfigurationElement enable= findExtension(ces, "refTest1").getChildren("enablement")[0]; //$NON-NLS-1$ //$NON-NLS-2$
		EnablementExpression probExpr= (EnablementExpression)ExpressionConverter.getDefault().perform(enable);
		EvaluationContext context= new EvaluationContext(null, Collections.EMPTY_LIST);

		assertThatThrownBy(() -> probExpr.evaluate(context), "Should report error with no variable",
				CoreException.class);

		context.addVariable("activePartId", "org.eclipse.ui.views.TasksView");
		assertEquals(EvaluationResult.FALSE, probExpr.evaluate(context));

		context.addVariable("activePartId", "org.eclipse.ui.views.ProblemsView");
		assertEquals(EvaluationResult.TRUE, probExpr.evaluate(context));
	}

	@Test
	public void testTwoReferences() throws Exception {
		IExtensionRegistry registry= Platform.getExtensionRegistry();
		IConfigurationElement[] ces= registry.getConfigurationElementsFor("org.eclipse.core.expressions.tests", "testParticipants"); //$NON-NLS-1$ //$NON-NLS-2$

		IConfigurationElement enable= findExtension(ces, "refTest2").getChildren("enablement")[0]; //$NON-NLS-1$ //$NON-NLS-2$
		EnablementExpression probExpr= (EnablementExpression)ExpressionConverter.getDefault().perform(enable);
		EvaluationContext context= new EvaluationContext(null, Collections.EMPTY_LIST);

		assertThatThrownBy(() -> probExpr.evaluate(context), "Should report error with no variable",
				CoreException.class);
		context.addVariable("activePartId", "org.eclipse.ui.views.TasksView");
		assertEquals(EvaluationResult.FALSE, probExpr.evaluate(context));

		context.addVariable("activePartId", "org.eclipse.ui.views.ProblemsView");
		// we still have no selection in the default variable
		assertEquals(EvaluationResult.FALSE, probExpr.evaluate(context));

		EvaluationContext context2 = new EvaluationContext(context, Collections.singletonList(probExpr));
		assertEquals(EvaluationResult.TRUE, probExpr.evaluate(context2));
	}

	@Test
	public void testSubType() throws Exception {
		EvaluationContext context= new EvaluationContext(null, IEvaluationContext.UNDEFINED_VARIABLE);
		ArrayList<AbstractCollection<?>> list = new ArrayList<>();
		HashSet<?> o1 = new HashSet<>();
		EvaluationContext c1= new EvaluationContext(null, o1);
		list.add(o1);
		ArrayList<?> o2 = new ArrayList<>();
		EvaluationContext c2= new EvaluationContext(null, o2);
		list.add(o2);
		LinkedList<?> o3 = new LinkedList<>();
		EvaluationContext c3= new EvaluationContext(null, o3);
		list.add(o3);
		context.addVariable("selection", list);

		WithExpression with= new WithExpression("selection");
		IterateExpression iterate= new IterateExpression("and", "false");
		with.add(iterate);
		InstanceofExpression iCollection= new InstanceofExpression("java.util.Collection");
		iterate.add(iCollection);

		InstanceofExpression iSet= new InstanceofExpression("java.util.Set");
		InstanceofExpression iList= new InstanceofExpression("java.util.List");

		assertEquals(EvaluationResult.TRUE, iSet.evaluate(c1));
		assertEquals(EvaluationResult.FALSE, iList.evaluate(c1));
		assertEquals(EvaluationResult.FALSE, iSet.evaluate(c2));
		assertEquals(EvaluationResult.TRUE, iList.evaluate(c2));
		assertEquals(EvaluationResult.FALSE, iSet.evaluate(c3));
		assertEquals(EvaluationResult.TRUE, iList.evaluate(c3));

		assertEquals(EvaluationResult.TRUE, with.evaluate(context));
	}

	@Test
	@Disabled("CI test environment too unstable for performance tests")
	public void testSubTypeTiming() throws Exception {
		HashSet<?> o1 = new HashSet<>();

		System.gc();
		long cachedStart= System.currentTimeMillis();
		for (int i= 0; i < TYPE_ITERATIONS; i++) {
			assertTrue(Expressions.isInstanceOf(o1, "java.util.Set"));
			assertFalse(Expressions.isInstanceOf(o1, "java.util.List"));
		}
		long cachedDelta= System.currentTimeMillis() - cachedStart;

		System.gc();
		long instanceStart= System.currentTimeMillis();
		for (int i= 0; i < TYPE_ITERATIONS; i++) {
			assertTrue(Expressions.uncachedIsSubtype(o1.getClass(), "java.util.Set"));
			assertFalse(Expressions.uncachedIsSubtype(o1.getClass(), "java.util.List"));
		}
		long instanceDelta= System.currentTimeMillis() - instanceStart;

		assertThat(cachedDelta).as("assert cachedDelta is less than instanceDelta" + instanceDelta)
				.isLessThan(instanceDelta);
	}

}
