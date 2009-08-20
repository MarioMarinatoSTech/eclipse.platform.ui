/*******************************************************************************
 * Copyright (c) 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Serge Beauchamp (Freescale Semiconductor) - initial API and implementation
 *******************************************************************************/
package org.eclipse.ui.internal.ide.misc;


import org.eclipse.core.filesystem.IFileInfo;
import org.eclipse.core.resources.IFilter;
import org.eclipse.core.resources.IFilterType;
import org.eclipse.core.resources.IFilterTypeFactory;
import org.eclipse.core.resources.IProject;

/** A Resource Filter Type Factory for supporting the AND logical preposition
 * @since 3.4
 *
 */
public class AndFilter extends CompoundFilter implements IFilterTypeFactory {

	class AndFilterType extends FilterType {
		public AndFilterType(IProject project, IFilter[] filters) {
			super (project, filters);
		}
		public boolean matches(IFileInfo fileInfo) {
			for (int i = 0; i < filterTypes.length; i++) {
				if (!filterTypes[i].matches(fileInfo))
					return false;
			}
			return true;
		}
	}
	public IFilterType instantiate(IProject project, String arguments) {
		return new AndFilterType(project, unserialize(project, arguments));
	}
}
