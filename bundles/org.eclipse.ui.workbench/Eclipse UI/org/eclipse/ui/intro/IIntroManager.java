/*******************************************************************************
 * Copyright (c) 2004, 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.ui.intro;

import org.eclipse.ui.IWorkbenchWindow;

/**
 * Manages the intro part that introduces the product to new users.
 * The intro part is typically shown the first time a product is started up.
 * <p>
 * The initial behavior of the intro part is controlled by the application
 * from via the {@link org.eclipse.ui.application.WorkbenchWindowAdvisor#openIntro()}
 * method.
 * </p>
 * <p>
 * See {@link org.eclipse.ui.intro.IIntroPart} for details on where intro parts
 * come from.
 * </p>
 * <p>
 * This interface is not intended to be extended or implemented by clients.
 * </p>
 * 
 * @see org.eclipse.ui.IWorkbench#getIntroManager()
 * @since 3.0
 */
public interface IIntroManager {

    /**
     * Closes the given intro part.
     * 
     * @param part the intro part
     * @return <code>true</code> if the intro part was closed, and 
     * <code>false</code> otherwise.  <code>false</code> is returned 
     * if part is <code>null</code> or it is not the intro part returned
     * by {@link #getIntro()}.
     */
    public boolean closeIntro(IIntroPart part);

    /**
     * Returns the intro part. Returns <code>null</code> if there is no intro
     * part, if it has been previously closed via {@link #closeIntro(IIntroPart)}
     * or if there is an intro part but {@link #showIntro(IWorkbenchWindow, boolean)} 
     * has not yet been called to create it.
     * 
     * @return the intro part, or <code>null</code> if none is available
     */
    public IIntroPart getIntro();

    /**
     * Return whether an intro is available. Note that this checks whether
     * there is an applicable intro part that could be instantiated and shown
     * to the user. 
     * Use {@link #getIntro()} to discover whether an intro part has already
     * been created.
     * 
     * @return <code>true</code> if there is an intro that could be shown, and
     * <code>false</code> if there is no intro
     */
    public boolean hasIntro();

    /**
     * Return the standby state of the given intro part.
     * 
     * @param part the intro part
     * @return <code>true</code> if the part in its partially
     * visible standy mode, and <code>false</code> if in its fully visible state.
     * <code>false</code> is returned if part is <code>null</code> or it is not 
     * the intro part returned by {@link #getIntro()}.    
     */
    boolean isIntroStandby(IIntroPart part);

    /**
     * Sets the standby state of the given intro part. Intro part usually should
     * render themselves differently in the full and standby modes. In standby
     * mode, the part should be partially visible to the user but otherwise
     * allow them to work. In full mode, the part should be fully visible and
     * be the center of the user's attention.
     * <p>
     * This method does nothing if the part is <code>null</code> or is not 
     * the intro part returned by {@link #getIntro()}.
     * </p>
     * 
     * @param part the intro part, or <code>null</code>
     * @param standby <code>true</code> to put the part in its partially
     * visible standy mode, and <code>false</code> to make it fully visible.  
     */
    public void setIntroStandby(IIntroPart part, boolean standby);

    /**
     * Shows the intro part in the given workbench window. If the intro part has
     * not been created yet, one will be created. If the intro part is currently
     * being shown in some workbench window, that other window is made active.
     * 
     * @param preferredWindow the preferred workbench window, or 
     * <code>null</code> to indicate the currently active workbench window
     * @param standby <code>true</code> to put the intro part in its partially
     * visible standy mode, and <code>false</code> to make it fully visible
     * @return the newly-created or existing intro part, or <code>null</code>
     * if no intro part is available or if <code>preferredWindow</code> is
     * <code>null</code> and there is no currently active workbench window
     */
    public IIntroPart showIntro(IWorkbenchWindow preferredWindow,
            boolean standby);
}
