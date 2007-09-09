/*******************************************************************************
 * Copyright (c) 2007 Brad Reynolds and others.
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
import org.eclipse.jface.databinding.swt.ISWTObservable;
import org.eclipse.jface.internal.databinding.internal.swt.CComboSingleSelectionObservableValue;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CCombo;
import org.eclipse.swt.widgets.Shell;

/**
 * @since 3.2
 */
public class CComboSingleSelectionObservableValueTest extends TestCase {
	public static Test suite() {
		Delegate delegate = new Delegate();
		
		return new SuiteBuilder().addObservableContractTest(
				SWTObservableValueContractTest.class, delegate)
				.addObservableContractTest(
						SWTMutableObservableValueContractTest.class, delegate)
				.build();
	}

	/* package */static class Delegate extends
			AbstractObservableValueContractDelegate {
		private CCombo combo;
		private Shell shell;

		public void setUp() {
			shell = new Shell();
			combo = new CCombo(shell, SWT.NONE);
			combo.add("0");
			combo.add("1");
		}

		public void tearDown() {
			shell.dispose();
		}

		public IObservableValue createObservableValue(Realm realm) {
			return new CComboSingleSelectionObservableValue(realm, combo);
		}

		public void change(IObservable observable) {
			int index = _createValue((IObservableValue) observable);
			combo.select(index);
			combo.notifyListeners(SWT.Selection, null);
		}

		public Object getValueType(IObservableValue observable) {
			return Integer.TYPE;
		}

		public Object createValue(IObservableValue observable) {
			return new Integer(_createValue(observable));
		}
		
		private int _createValue(IObservableValue observable) {
			CCombo combo = ((CCombo) ((ISWTObservable) observable).getWidget());
			int value = Math.max(0, combo.getSelectionIndex());
			
			//returns either 0 or 1 depending upon current value
			return Math.abs(value - 1);
		}
	}
}
