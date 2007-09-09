/*******************************************************************************
 * Copyright (c) 2006 Brad Reynolds and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Brad Reynolds - initial API and implementation
 ******************************************************************************/

package org.eclipse.jface.tests.internal.databinding.internal.swt;

import junit.framework.Test;
import junit.framework.TestCase;

import org.eclipse.core.databinding.observable.IObservable;
import org.eclipse.core.databinding.observable.Realm;
import org.eclipse.core.databinding.observable.value.IObservableValue;
import org.eclipse.jface.conformance.databinding.AbstractObservableValueContractDelegate;
import org.eclipse.jface.conformance.databinding.SWTMutableObservableValueContractTest;
import org.eclipse.jface.conformance.databinding.SWTObservableValueContractTest;
import org.eclipse.jface.conformance.databinding.SuiteBuilder;
import org.eclipse.jface.internal.databinding.internal.swt.ButtonObservableValue;
import org.eclipse.jface.tests.databinding.EventTrackers.ValueChangeEventTracker;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Shell;

/**
 * @since 3.2
 */
public class ButtonObservableValueTest extends TestCase {
	private Shell shell;
	private Button button;
	private ButtonObservableValue observableValue;
	private ValueChangeEventTracker listener;
	
	/* (non-Javadoc)
	 * @see junit.framework.TestCase#setUp()
	 */
	protected void setUp() throws Exception {
		super.setUp();
		
		shell = new Shell();
		button = new Button(shell, SWT.CHECK);
		observableValue = new ButtonObservableValue(
				button);
		listener = new ValueChangeEventTracker();
	}
	
	/* (non-Javadoc)
	 * @see junit.framework.TestCase#tearDown()
	 */
	protected void tearDown() throws Exception {
		shell.dispose();
		
		super.tearDown();		
	}
	
	public void testSelection_ChangeNotifiesObservable() throws Exception {
		observableValue.addValueChangeListener(listener);
		button.setSelection(true);

		// precondition
		assertEquals(0, listener.count);
		button.notifyListeners(SWT.Selection, null);

		assertEquals("Selection event should notify observable.", 1,
				listener.count);
	}
	
	public void testSelection_NoChange() throws Exception {
		button.setSelection(true);
		button.notifyListeners(SWT.Selection, null);
		observableValue.addValueChangeListener(listener);
		
		//precondition
		assertEquals(0, listener.count);
		
		button.notifyListeners(SWT.Selection, null);
		assertEquals("Value did not change.  Listeners should not have been notified.", 0, listener.count);
	}

	public static Test suite() {
		Delegate delegate = new Delegate();

		return new SuiteBuilder().addTests(ButtonObservableValueTest.class)
				.addObservableContractTest(
						SWTObservableValueContractTest.class, delegate)
				.addObservableContractTest(
						SWTMutableObservableValueContractTest.class, delegate)
				.build();
	}

	/* package */static class Delegate extends
			AbstractObservableValueContractDelegate {
		Shell shell;

		Button button;

		public void setUp() {
			super.setUp();

			shell = new Shell();
			button = new Button(shell, SWT.CHECK);
		}

		public void tearDown() {
			super.tearDown();

			shell.dispose();
		}

		public IObservableValue createObservableValue(Realm realm) {
			return new ButtonObservableValue(realm, button);
		}

		public Object getValueType(IObservableValue observable) {
			return Boolean.TYPE;
		}

		public void change(IObservable observable) {
			IObservableValue observableValue = (IObservableValue) observable;
			observableValue.setValue(createValue(observableValue));
		}
		
		public Object createValue(IObservableValue observable) {
			if (Boolean.TRUE.equals(observable.getValue())) {
				return Boolean.FALSE;
			}
			
			return Boolean.TRUE;
		}
	}
}
