/*******************************************************************************
 * Copyright (c) 2000, 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.ui.internal.decorators;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.StringTokenizer;

import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtension;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.dynamicHelpers.IExtensionAdditionHandler;
import org.eclipse.core.runtime.dynamicHelpers.IExtensionRemovalHandler;
import org.eclipse.core.runtime.dynamicHelpers.IExtensionTracker;
import org.eclipse.jface.util.ListenerList;
import org.eclipse.jface.util.SafeRunnable;
import org.eclipse.jface.viewers.IBaseLabelProvider;
import org.eclipse.jface.viewers.IColorDecorator;
import org.eclipse.jface.viewers.IDelayedLabelDecorator;
import org.eclipse.jface.viewers.IFontDecorator;
import org.eclipse.jface.viewers.ILabelDecorator;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.ILightweightLabelDecorator;
import org.eclipse.jface.viewers.LabelProviderChangedEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.IDecoratorManager;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.internal.IPreferenceConstants;
import org.eclipse.ui.internal.IWorkbenchConstants;
import org.eclipse.ui.internal.LegacyResourceSupport;
import org.eclipse.ui.internal.Workbench;
import org.eclipse.ui.internal.WorkbenchMessages;
import org.eclipse.ui.internal.WorkbenchPlugin;
import org.eclipse.ui.internal.util.Util;
import org.eclipse.ui.progress.WorkbenchJob;

/**
 * The DecoratorManager is the class that handles all of the
 * decorators defined in the image.
 * 
 * @since 2.0
 */
public class DecoratorManager implements IDelayedLabelDecorator,
        ILabelProviderListener, IDecoratorManager, IFontDecorator, IColorDecorator, IExtensionAdditionHandler, IExtensionRemovalHandler {

	private static String EXTENSIONPOINT_UNIQUE_ID = WorkbenchPlugin.PI_WORKBENCH + "." + IWorkbenchConstants.PL_DECORATORS; //$NON-NLS-1$
	
    /**
     * The family for the decorate job.
     */
    public static final Object FAMILY_DECORATE = new Object();

    private DecorationScheduler scheduler;

    private LightweightDecoratorManager lightweightManager;

    //Hold onto the list of listeners to be told if a change has occured
    private ListenerList listeners = new ListenerList();

    //The full definitions read from the registry
    private FullDecoratorDefinition[] fullDefinitions;

    private FullTextDecoratorRunnable fullTextRunnable = new FullTextDecoratorRunnable();

    private FullImageDecoratorRunnable fullImageRunnable = new FullImageDecoratorRunnable();

    private static final FullDecoratorDefinition[] EMPTY_FULL_DEF = new FullDecoratorDefinition[0];

    private final String PREFERENCE_SEPARATOR = ","; //$NON-NLS-1$

    private final String VALUE_SEPARATOR = ":"; //$NON-NLS-1$

    private final String P_TRUE = "true"; //$NON-NLS-1$

    private final String P_FALSE = "false"; //$NON-NLS-1$

    /**
     * Create a new instance of the receiver and load the
     * settings from the installed plug-ins.
     */
    public DecoratorManager() {
        
        scheduler = new DecorationScheduler(this);
        IExtensionTracker tracker = PlatformUI.getWorkbench()
				.getExtensionTracker();
        tracker.registerAdditionHandler(this);
        tracker.registerRemovalHandler(this);
    }

    /**
	 * Initalize the decorator definitions.
	 */
	private void initializeDecoratorDefinitions() {
		DecoratorRegistryReader reader = new DecoratorRegistryReader();
        Collection values = reader
                .readRegistry(Platform.getExtensionRegistry());

        ArrayList full = new ArrayList();
        ArrayList lightweight = new ArrayList();
        Iterator allDefinitions = values.iterator();
        IExtensionTracker configurationElementTracker = PlatformUI
				.getWorkbench().getExtensionTracker();
        while (allDefinitions.hasNext()) {
            DecoratorDefinition nextDefinition = (DecoratorDefinition) allDefinitions
                    .next();
            if (nextDefinition.isFull())
                full.add(nextDefinition);
            else
                lightweight.add(nextDefinition);
                        
			configurationElementTracker.registerObject(nextDefinition.getConfigurationElement().getDeclaringExtension(), nextDefinition, IExtensionTracker.REF_WEAK);
        }

        fullDefinitions = new FullDecoratorDefinition[full.size()];
        full.toArray(fullDefinitions);

        LightweightDecoratorDefinition[] lightweightDefinitions = new LightweightDecoratorDefinition[lightweight
                .size()];
        lightweight.toArray(lightweightDefinitions);

        lightweightManager = new LightweightDecoratorManager(
                lightweightDefinitions);
        
        applyDecoratorsPreference();
	}

	/**
     * For dynamic UI
     * 
     * @param definition the definition to add
     * @since 3.0
     */
    public void addDecorator(DecoratorDefinition definition) {
        if (definition.isFull()) {
            if (getFullDecoratorDefinition(definition.getId()) == null) {
                FullDecoratorDefinition[] oldDefs = getFullDefinitions();
                fullDefinitions = new FullDecoratorDefinition[fullDefinitions.length + 1];
                System
                        .arraycopy(oldDefs, 0, fullDefinitions, 0,
                                oldDefs.length);
                fullDefinitions[oldDefs.length] = (FullDecoratorDefinition) definition;
                clearCaches();
                updateForEnablementChange();
            }
        } else {
            if (getLightweightManager().addDecorator(
                    (LightweightDecoratorDefinition) definition)) {
                clearCaches();
                updateForEnablementChange();
            }
        }
        ((Workbench) PlatformUI.getWorkbench())
				.getExtensionTracker().registerObject(
						definition.getConfigurationElement().getDeclaringExtension(), definition, IExtensionTracker.REF_WEAK);
    }

    /**
     * See if the supplied decorator cache has a value for the
     * element. If not calculate it from the enabledDefinitions and
     * update the cache.
     * @return Collection of DecoratorDefinition.
     * @param element The element being tested.
     * @param enabledDefinitions The definitions currently defined for this decorator.
     */
    static Collection getDecoratorsFor(Object element,
            DecoratorDefinition[] enabledDefinitions) {

        ArrayList decorators = new ArrayList();

        for (int i = 0; i < enabledDefinitions.length; i++) {
            if (enabledDefinitions[i].getEnablement().isEnabledFor(element))
                decorators.add(enabledDefinitions[i]);
        }

        return decorators;

    }


    /**
     * Add the listener to the list of listeners.
     */
    public void addListener(ILabelProviderListener listener) {
        listeners.add(listener);
    }

    /**
     * Remove the listener from the list.
     */
    public void removeListener(ILabelProviderListener listener) {
        listeners.remove(listener);
    }

    /**
     * Inform all of the listeners that require an update
     * @param event the event with the update details
     */
    void fireListeners(final LabelProviderChangedEvent event) {
        Object[] array = listeners.getListeners();
        for (int i = 0; i < array.length; i++) {
            final ILabelProviderListener l = (ILabelProviderListener) array[i];
            Platform.run(new SafeRunnable() {
                public void run() {
                    l.labelProviderChanged(event);
                }
            });
        }
    }

    /**
     * Fire any listeners from the UIThread. Used for cases where this
     * may be invoked outside of the UI by the public API.
     * @param event the event with the update details
     */
    void fireListenersInUIThread(final LabelProviderChangedEvent event) {

        //No updates if there is no UI
        if (!PlatformUI.isWorkbenchRunning())
            return;

        //Only bother with the job if in the UI Thread
        if (Thread.currentThread() == PlatformUI.getWorkbench().getDisplay()
                .getThread()) {
            fireListeners(event);
            return;
        }

        WorkbenchJob updateJob = new WorkbenchJob(WorkbenchMessages.DecorationScheduler_UpdateJobName) { 
            /*
             * (non-Javadoc)
             * 
             * @see org.eclipse.ui.progress.UIJob#runInUIThread(org.eclipse.core.runtime.IProgressMonitor)
             */
            public IStatus runInUIThread(IProgressMonitor monitor) {
                fireListeners(event);
                return Status.OK_STATUS;
            }
            
            /* (non-Javadoc)
			 * @see org.eclipse.core.runtime.jobs.Job#belongsTo(java.lang.Object)
			 */
			public boolean belongsTo(Object family) {
				return FAMILY_DECORATE == family;
			}
        };
        updateJob.setSystem(true);
        updateJob.schedule();

    }

    /*
     *  (non-Javadoc)
     * @see org.eclipse.jface.viewers.ILabelDecorator#decorateText(java.lang.String, java.lang.Object)
     */
    public String decorateText(String text, Object element) {

        //Get any adaptions to IResource
        Object adapted = getResourceAdapter(element);
        String result = scheduler.decorateWithText(text, element, adapted);
        FullDecoratorDefinition[] decorators = getDecoratorsFor(element);
        for (int i = 0; i < decorators.length; i++) {
            if (decorators[i].getEnablement().isEnabledFor(element)) {
                String newResult = safeDecorateText(element, result,
                        decorators[i]);
                if (newResult != null)
                    result = newResult;
            }
        }

        if (adapted != null) {
            decorators = getDecoratorsFor(adapted);
            for (int i = 0; i < decorators.length; i++) {
                if (decorators[i].isAdaptable()
                        && decorators[i].getEnablement().isEnabledFor(adapted)) {
                    String newResult = safeDecorateText(adapted, result,
                            decorators[i]);
                    if (newResult != null)
                        result = newResult;
                }
            }
        }

        return result;
    }

    /**
     * Decorate the text in a SafeRunnable.
     * @param element The element we are decorating
     * @param start The currently decorated String
     * @param decorator The decorator to run.
     * @return String
     */
    private String safeDecorateText(Object element, String start,
            FullDecoratorDefinition decorator) {
        fullTextRunnable.setValues(start, element, decorator);
        Platform.run(fullTextRunnable);
        String newResult = fullTextRunnable.getResult();
        return newResult;
    }

    /*
     *  (non-Javadoc)
     * @see org.eclipse.jface.viewers.ILabelDecorator#decorateImage(org.eclipse.swt.graphics.Image, java.lang.Object)
     */
    public Image decorateImage(Image image, Object element) {

        Object adapted = getResourceAdapter(element);
        Image result = scheduler.decorateWithOverlays(image, element, adapted);
        FullDecoratorDefinition[] decorators = getDecoratorsFor(element);

        for (int i = 0; i < decorators.length; i++) {
            if (decorators[i].getEnablement().isEnabledFor(element)) {
                Image newResult = safeDecorateImage(element, result,
                        decorators[i]);
                if (newResult != null)
                    result = newResult;
            }
        }

        //Get any adaptions to IResource

        if (adapted != null) {
            decorators = getDecoratorsFor(adapted);
            for (int i = 0; i < decorators.length; i++) {
                if (decorators[i].isAdaptable()
                        && decorators[i].getEnablement().isEnabledFor(adapted)) {
                    Image newResult = safeDecorateImage(adapted, result,
                            decorators[i]);
                    if (newResult != null)
                        result = newResult;
                }
            }
        }

        return result;
    }

    /**
     * Decorate the image in a SafeRunnable.
     * @param element The element we are decorating
     * @param start The currently decorated Image
     * @param decorator The decorator to run.
     * @return Image
     */
    private Image safeDecorateImage(Object element, Image start,
            FullDecoratorDefinition decorator) {
        fullImageRunnable.setValues(start, element, decorator);
        Platform.run(fullImageRunnable);
        Image newResult = fullImageRunnable.getResult();
        return newResult;
    }

    /**
     * Get the resource adapted object for the supplied
     * element. Return <code>null</code>. if there isn't one.
     * @param element
     * @return Object or <code>null</code>.
     */
    private Object getResourceAdapter(Object element) {

        //Get any adaptions to IResource (when resources are available)
        if (element instanceof IAdaptable) {
            IAdaptable adaptable = (IAdaptable) element;
            Class contributorResourceAdapterClass = LegacyResourceSupport
                    .getIContributorResourceAdapterClass();
            if (contributorResourceAdapterClass == null) {
                return null;
            }
            Object resourceAdapter = adaptable
                    .getAdapter(contributorResourceAdapterClass);
            if (resourceAdapter == null)
                // reflective equivalent of
                //    resourceAdapter = DefaultContributorResourceAdapter.getDefault();
                try {
                    Class c = LegacyResourceSupport
                            .getDefaultContributorResourceAdapterClass();
                    Method m = c.getDeclaredMethod("getDefault", new Class[0]); //$NON-NLS-1$
                    resourceAdapter = m.invoke(null, new Object[0]);
                } catch (Exception e) {
                    // shouldn't happen - but play it safe
                    return null;
                }

            Object adapted;
            // reflective equivalent of
            //    adapted = ((IContributorResourceAdapter) resourceAdapter).getAdaptedResource(adaptable);
            try {
                Method m = contributorResourceAdapterClass.getDeclaredMethod(
                        "getAdaptedResource", new Class[] { IAdaptable.class }); //$NON-NLS-1$
                adapted = m.invoke(resourceAdapter, new Object[] { adaptable });
            } catch (Exception e) {
                // shouldn't happen - but play it safe
                return null;
            }
            if (adapted != element) {
                return adapted; //Avoid applying decorator twice
            }
        }
        return null;
    }

    /**
     * Return whether or not the decorator registered for element
     * has a label property called property name.
     */
    public boolean isLabelProperty(Object element, String property) {
        return isLabelProperty(element, property, true);
    }

    /**
     * Return whether or not the decorator registered for element
     * has a label property called property name.
     * Check for an adapted resource if checkAdapted is true.
     * @param element 
     * @param property
     * @param checkAdapted
     * @return boolean <code>true</code> if there is a label property
     * for element or its adapted value
     */
    public boolean isLabelProperty(Object element, String property,
            boolean checkAdapted) {
        boolean fullCheck = isLabelProperty(element, property,
                getDecoratorsFor(element));

        if (fullCheck)
            return fullCheck;

        boolean lightweightCheck = isLabelProperty(element, property,
                getLightweightManager().getDecoratorsFor(element));

        if (lightweightCheck)
            return true;

        if (checkAdapted) {
            //Get any adaptions to IResource
            Object adapted = getResourceAdapter(element);
            if (adapted == null || adapted == element)
                return false;

            fullCheck = isLabelProperty(adapted, property,
                    getDecoratorsFor(adapted));
            if (fullCheck)
                return fullCheck;

            return isLabelProperty(adapted, property, lightweightManager
                    .getDecoratorsFor(adapted));
        }
        return false;
    }

    private boolean isLabelProperty(Object element, String property,
            DecoratorDefinition[] decorators) {
        for (int i = 0; i < decorators.length; i++) {
            if (decorators[i].getEnablement().isEnabledFor(element)
                    && decorators[i].isLabelProperty(element, property))
                return true;
        }

        return false;
    }

    /**
     * Return the enabled full decorator definitions.
     * @return FullDecoratorDefinition[]
     */
    private FullDecoratorDefinition[] enabledFullDefinitions() {
        ArrayList result = new ArrayList();
        FullDecoratorDefinition[] full = getFullDefinitions();
        for (int i = 0; i < full.length; i++) {
            if (full[i].isEnabled())
                result.add(full[i]);
        }
        FullDecoratorDefinition[] returnArray = new FullDecoratorDefinition[result
                .size()];
        result.toArray(returnArray);
        return returnArray;
    }

    /*
     * @see IBaseLabelProvider#dispose()
     */
    public void dispose() {
        //Do nothing as this is not viewer dependant
    }

    /**
     * Clear the caches in the manager. This is required
     * to avoid updates that may occur due to changes in 
     * enablement.
     */
    public void clearCaches() {
        getLightweightManager().reset();
        fullTextRunnable.clearReferences();
        fullImageRunnable.clearReferences();
    }

    /**
     * Enablement had changed. Fire the listeners and write
     * the preference.
     */
    public void updateForEnablementChange() {
        //Clear any results that may be around as all labels have changed
        scheduler.clearResults();
        fireListenersInUIThread(new LabelProviderChangedEvent(this));
        writeDecoratorsPreference();
    }

    /**
     * Get the DecoratorDefinitions defined on the receiver.
     * @return DecoratorDefinition[]
     */
    public DecoratorDefinition[] getAllDecoratorDefinitions() {
        LightweightDecoratorDefinition[] lightweightDefinitions = getLightweightManager()
                .getDefinitions();
        DecoratorDefinition[] returnValue = new DecoratorDefinition[fullDefinitions.length
                + lightweightDefinitions.length];
        System.arraycopy(fullDefinitions, 0, returnValue, 0,
                fullDefinitions.length);
        System.arraycopy(lightweightDefinitions, 0, returnValue,
                fullDefinitions.length, lightweightDefinitions.length);
        return returnValue;
    }

    /*
     * @see ILabelProviderListener#labelProviderChanged(LabelProviderChangedEvent)
     */
    public void labelProviderChanged(LabelProviderChangedEvent event) {
        Object[] elements = event.getElements();
        scheduler.clearResults();
        //If the elements are not specified send out a general update
        if (elements == null)
            fireListeners(event);
        else {
            //Assume that someone is going to care about the 
            //decoration result and just start it right away
            for (int i = 0; i < elements.length; i++) {
                Object adapted = getResourceAdapter(elements[i]);
                //Force an update in case full decorators are the only ones enabled
                scheduler.queueForDecoration(elements[i], adapted, true, null);
            }
        }
    }

    /**
     * Store the currently enabled decorators in
     * preference store.
     */
    private void writeDecoratorsPreference() {
        StringBuffer enabledIds = new StringBuffer();
        writeDecoratorsPreference(enabledIds, getFullDefinitions());
        writeDecoratorsPreference(enabledIds, getLightweightManager()
                .getDefinitions());

        WorkbenchPlugin.getDefault().getPreferenceStore().setValue(
                IPreferenceConstants.ENABLED_DECORATORS, enabledIds.toString());
    }

    private void writeDecoratorsPreference(StringBuffer enabledIds,
            DecoratorDefinition[] definitions) {
        for (int i = 0; i < definitions.length; i++) {
            enabledIds.append(definitions[i].getId());
            enabledIds.append(VALUE_SEPARATOR);
            if (definitions[i].isEnabled())
                enabledIds.append(P_TRUE);
            else
                enabledIds.append(P_FALSE);

            enabledIds.append(PREFERENCE_SEPARATOR);
        }
    }

    /**
     * Get the currently enabled decorators in
     * preference store and set the state of the
     * current definitions accordingly.
     */
    public void applyDecoratorsPreference() {

        String preferenceValue = WorkbenchPlugin.getDefault()
                .getPreferenceStore().getString(
                        IPreferenceConstants.ENABLED_DECORATORS);

        StringTokenizer tokenizer = new StringTokenizer(preferenceValue,
                PREFERENCE_SEPARATOR);
        Set enabledIds = new HashSet();
        Set disabledIds = new HashSet();
        while (tokenizer.hasMoreTokens()) {
            String nextValuePair = tokenizer.nextToken();

            //Strip out the true or false to get the id
            String id = nextValuePair.substring(0, nextValuePair
                    .indexOf(VALUE_SEPARATOR));
            if (nextValuePair.endsWith(P_TRUE))
                enabledIds.add(id);
            else
                disabledIds.add(id);
        }

        FullDecoratorDefinition[] full = getFullDefinitions();
        for (int i = 0; i < full.length; i++) {
            String id = full[i].getId();
            if (enabledIds.contains(id))
            	full[i].setEnabled(true);
            else {
                if (disabledIds.contains(id))
                	full[i].setEnabled(false);
            }
        }

        LightweightDecoratorDefinition[] lightweightDefinitions = getLightweightManager()
                .getDefinitions();
        for (int i = 0; i < lightweightDefinitions.length; i++) {
            String id = lightweightDefinitions[i].getId();
            if (enabledIds.contains(id))
                lightweightDefinitions[i].setEnabled(true);
            else {
                if (disabledIds.contains(id))
                    lightweightDefinitions[i].setEnabled(false);
            }
        }

    }

    /**
     * Shutdown the decorator manager by disabling all
     * of the decorators so that dispose() will be called
     * on them.
     */
    public void shutdown() {
        //Disable all of the enabled decorators 
        //so as to force a dispose of thier decorators
    	FullDecoratorDefinition[] full = getFullDefinitions();
        for (int i = 0; i < full.length; i++) {
            if (full[i].isEnabled())
                full[i].setEnabled(false);
        }
        if(lightweightManager != null)//Do not create if not required
        	getLightweightManager().shutdown();
        scheduler.shutdown();
    }

    /**
     * @see IDecoratorManager#getEnabled(String)
     */
    public boolean getEnabled(String decoratorId) {
        DecoratorDefinition definition = getDecoratorDefinition(decoratorId);
        if (definition == null)
            return false;
        return definition.isEnabled();
    }

    /**
     * @see IDecoratorManager#getLabelDecorator()
     */
    public ILabelDecorator getLabelDecorator() {
        return this;
    }

    /**
     * @see IDecoratorManager#setEnabled(String, boolean)
     */
    public void setEnabled(String decoratorId, boolean enabled) {
        DecoratorDefinition definition = getDecoratorDefinition(decoratorId);
        if (definition != null) {
            definition.setEnabled(enabled);
            clearCaches();
            updateForEnablementChange();
        }
    }

    /*
     * @see IDecoratorManager#getBaseLabelProvider(String)
     */
    public IBaseLabelProvider getBaseLabelProvider(String decoratorId) {
        IBaseLabelProvider fullProvider = getLabelDecorator(decoratorId);
        if (fullProvider == null)
            return getLightweightLabelDecorator(decoratorId);
        return fullProvider;
    }

    /*
     * @see IDecoratorManager#getLabelDecorator(String)
     */
    public ILabelDecorator getLabelDecorator(String decoratorId) {
        FullDecoratorDefinition definition = getFullDecoratorDefinition(decoratorId);

        //Do not return for a disabled decorator
        if (definition != null && definition.isEnabled()) {
            return definition.getDecorator();
        }
        return null;
    }

    /*
     * @see IDecoratorManager#getLightweightLabelDecorator(String)
     */
    public ILightweightLabelDecorator getLightweightLabelDecorator(
            String decoratorId) {
        LightweightDecoratorDefinition definition = getLightweightManager()
                .getDecoratorDefinition(decoratorId);
        //Do not return for a disabled decorator
        if (definition != null && definition.isEnabled()) {
            return definition.getDecorator();
        }
        return null;
    }

    /**
     * Get the DecoratorDefinition with the supplied id
     * @return DecoratorDefinition or <code>null</code> if it is not found
     * @param decoratorId String
     */
    private DecoratorDefinition getDecoratorDefinition(String decoratorId) {
        DecoratorDefinition returnValue = getFullDecoratorDefinition(decoratorId);
        if (returnValue == null)
            return getLightweightManager().getDecoratorDefinition(decoratorId);
        return returnValue;
    }

    /**
     * Get the FullDecoratorDefinition with the supplied id
     * @return FullDecoratorDefinition or <code>null</code> if it is not found
     * @param decoratorId the id
     */
    private FullDecoratorDefinition getFullDecoratorDefinition(
            String decoratorId) {
    	int idx = getFullDecoratorDefinitionIdx(decoratorId);
    	if (idx != -1)
    		return getFullDefinitions()[idx];
    	return null;
    }
    
    /**
     * Return the index of the definition in the array.
     * 
     * @param decoratorId the id
     * @return the index of the definition in the array or <code>-1</code>
     * @since 3.1
     */
    private int getFullDecoratorDefinitionIdx(
    		String decoratorId) {
    	FullDecoratorDefinition[] full = getFullDefinitions();
        for (int i = 0; i < full.length; i++) {
            if (full[i].getId().equals(decoratorId))
                return i;
        }    	
    	return -1;
    }
    		

    /**
     * Get the full decorator definitions registered for elements of this type.
     * @param element The element to look up
     * @return FullDecoratorDefinition[]
     */
    private FullDecoratorDefinition[] getDecoratorsFor(Object element) {

        if (element == null)
            return EMPTY_FULL_DEF;

          Collection decorators = getDecoratorsFor(element,
                enabledFullDefinitions());
	    FullDecoratorDefinition[] decoratorArray = EMPTY_FULL_DEF;
        if (decorators.size() > 0){
            decoratorArray = new FullDecoratorDefinition[decorators.size()];
            decorators.toArray(decoratorArray);
        }

        return decoratorArray;
    }

    /**
     * Returns the lightweightManager.
     * @return LightweightDecoratorManager
     */
    LightweightDecoratorManager getLightweightManager() {
    	if(lightweightManager == null)
    		initializeDecoratorDefinitions();
        return lightweightManager;
    }

    /**
     * @see org.eclipse.ui.IDecoratorManager#update(java.lang.String)
     */
    public void update(String decoratorId) {

        IBaseLabelProvider provider = getBaseLabelProvider(decoratorId);
        if (provider != null) {
            scheduler.clearResults();
            fireListeners(new LabelProviderChangedEvent(provider));
        }

    }

    /* (non-Javadoc)
     * @see org.eclipse.jface.viewers.IDelayedLabelDecorator#prepareDecoration(java.lang.Object, java.lang.String)
     */
    public boolean prepareDecoration(Object element, String originalText) {

        // Check if there is a decoration ready or if there is no lightweight decorators to be applied
        if (scheduler.isDecorationReady(element)
                || !getLightweightManager().hasEnabledDefinitions()) {
            return true;
        }

        // Force an update if there is a text already
        boolean force = true;
        //If not then do not force as the undecorated value is fine
        if(originalText == null || originalText.length() == 0)
        	force = false;
        
        // Queue the decoration.
        scheduler.queueForDecoration(element, getResourceAdapter(element),
                force, originalText); 

        //If all that is there is deferred ones then defer decoration.
        //For the sake of effeciency we do not test for enablement at this
        //point and just abandon deferment if there are any to run right
        //away
        return getFullDefinitions().length > 0;

    }
    
	
	/* (non-Javadoc)
	 * @see org.eclipse.jface.viewers.IFontDecorator#decorateFont(java.lang.Object)
	 */
	public Font decorateFont(Object element) {
		return scheduler.getFont(element, getResourceAdapter(element));
	}
	/* (non-Javadoc)
	 * @see org.eclipse.jface.viewers.IColorDecorator#decorateBackground(java.lang.Object)
	 */
	public Color decorateBackground(Object element) {
		return scheduler.getBackgroundColor(element, getResourceAdapter(element));
	}
	/* (non-Javadoc)
	 * @see org.eclipse.jface.viewers.IColorDecorator#decorateForeground(java.lang.Object)
	 */
	public Color decorateForeground(Object element) {
		return scheduler.getForegroundColor(element, getResourceAdapter(element));
	}
	/**
	 * Get all of the defined fullDefinitions. Initalize if
	 * required
	 * @return FullDecoratorDefinition[]
	 */
	private FullDecoratorDefinition[] getFullDefinitions() {
		if(fullDefinitions == null)
			initializeDecoratorDefinitions();
		return fullDefinitions;
	}

    //PASCAL Need to see with Kim. 
    public IExtensionPoint getExtensionPointFilter() {
        return Platform.getExtensionRegistry().getExtensionPoint(EXTENSIONPOINT_UNIQUE_ID);
    }

    public void addInstance(IExtensionTracker tracker, IExtension addedExtension) {
        IConfigurationElement addedElements[] = addedExtension.getConfigurationElements();
        for (int i = 0; i < addedElements.length; i++) {
            DecoratorRegistryReader reader = new DecoratorRegistryReader();
            reader.readElement(addedElements[i]);
            for (Iterator j = reader.getValues().iterator(); j.hasNext();) {
                addDecorator((DecoratorDefinition) j.next());
            }
        }
    }

	/* (non-Javadoc)
	 * @see org.eclipse.ui.internal.registry.experimental.IConfigurationElementRemovalHandler#removeInstance(org.eclipse.core.runtime.IConfigurationElement, java.lang.Object)
	 */
	public void removeInstance(IExtension source, Object[] objects) {
        for (int i = 0; i < objects.length; i++) {
            if (objects[i] instanceof DecoratorDefinition) {
                DecoratorDefinition definition = (DecoratorDefinition) objects[i];
                if (definition.isFull()) {
                    int idx = getFullDecoratorDefinitionIdx(definition.getId());
                    if (idx != -1) {                    
                        FullDecoratorDefinition[] oldDefs = getFullDefinitions();
                        Util
                                .arrayCopyWithRemoval(
                                        oldDefs,
                                        fullDefinitions = new FullDecoratorDefinition[fullDefinitions.length - 1],
                                        idx);                   
                        clearCaches();
                        updateForEnablementChange();
                    }
                } else {
                    if (getLightweightManager().removeDecorator(
                            (LightweightDecoratorDefinition) definition)) {
                        clearCaches();
                        updateForEnablementChange();
                    }
                }           
            }    
        }
		
	}
}
