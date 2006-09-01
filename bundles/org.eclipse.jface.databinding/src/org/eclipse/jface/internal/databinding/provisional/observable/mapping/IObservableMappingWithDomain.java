/*******************************************************************************
 * Copyright (c) 2006 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 ******************************************************************************/

package org.eclipse.jface.internal.databinding.provisional.observable.mapping;

import org.eclipse.jface.databinding.observable.set.IObservableSet;

/**
 * A mapping with domain whose changes can be tracked by mapping change listeners.
 * <p>
 * This interface is not intended to be implemented by clients. Clients should
 * instead subclass one of the classes that implement this interface. Note that
 * direct implementers of this interface outside of the framework will be broken
 * in future releases when methods are added to this interface.
 * </p>
 * 
 * @since 1.0
 *
 */
public interface IObservableMappingWithDomain extends IObservableMapping {

	/**
	 * @return the domain of this mapping (the set of elements that can be mapped)
	 */
	public IObservableSet getDomain();
	
}
