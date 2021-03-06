/*******************************************************************************
 * Copyright (c) 2013 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package org.jboss.tools.windup.core.internal.validators;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.wst.validation.AbstractValidator;
import org.eclipse.wst.validation.ValidationResult;
import org.eclipse.wst.validation.ValidationState;
import org.eclipse.wst.validation.ValidatorMessage;
import org.jboss.tools.windup.core.WindupCorePlugin;
import org.jboss.tools.windup.core.WindupService;
import org.jboss.tools.windup.runtime.WindupRuntimePlugin;
import org.jboss.windup.reporting.model.Severity;
import org.jboss.windup.tooling.data.Classification;
import org.jboss.windup.tooling.data.Hint;

/**
 * <p>
 * {@link AbstractValidator} which uses the {@link WindupService} to add {@link ValidatorMessage}s to resources based on the decorations and hits
 * found by the {@link WindupService}.
 * </p>
 */
public class WindupValidator extends AbstractValidator
{
    public WindupValidator()
    {
    }

    /**
     * @see org.eclipse.wst.validation.AbstractValidator#clean(org.eclipse.core.resources.IProject, org.eclipse.wst.validation.ValidationState,
     *      org.eclipse.core.runtime.IProgressMonitor)
     */
    @Override
    public void clean(IProject project, ValidationState state, IProgressMonitor monitor)
    {
        cleanUpWindUpMarkers(project);
    }

    /**
     * @see org.eclipse.wst.validation.AbstractValidator#validationStarting(org.eclipse.core.resources.IProject,
     *      org.eclipse.wst.validation.ValidationState, org.eclipse.core.runtime.IProgressMonitor)
     */
    @Override
    public void validationStarting(IProject project,
                org.eclipse.wst.validation.ValidationState state,
                IProgressMonitor monitor)
    {

        super.validationStarting(project, state, monitor);
    }

    /**
     * @see org.eclipse.wst.validation.AbstractValidator#validationFinishing(org.eclipse.core.resources.IProject,
     *      org.eclipse.wst.validation.ValidationState, org.eclipse.core.runtime.IProgressMonitor)
     */
    @Override
    public void validationFinishing(IProject project,
                org.eclipse.wst.validation.ValidationState state,
                IProgressMonitor monitor)
    {

        super.validationFinishing(project, state, monitor);
    }

    /**
     * @see org.eclipse.wst.validation.AbstractValidator#validate(org.eclipse.core.resources.IResource, int,
     *      org.eclipse.wst.validation.ValidationState, org.eclipse.core.runtime.IProgressMonitor)
     */
    @Override
    public ValidationResult validate(IResource resource, int kind,
                org.eclipse.wst.validation.ValidationState state,
                IProgressMonitor monitor)
    {
        cleanUpWindUpMarkers(resource);

        ValidationResult result = new ValidationResult();

        Iterable<Hint> hints = WindupService.getDefault().getHints(resource, monitor);
        for (Hint hint : hints)
        {

            ValidatorMessage hintMessage = ValidatorMessage.create(hint.getHint(), resource);
            hintMessage.setAttribute(IMarker.SEVERITY, convertSeverity(hint.getSeverity()));
            hintMessage.setType(WindupCorePlugin.WINDUP_HINT_MARKER_ID);
            hintMessage.setAttribute(IMarker.LINE_NUMBER, hint.getLineNumber());

            try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(hint.getFile())))
            {
                int currentLine = 1;
                int pos = 0;
                int currentByte;
                int lastByte = 0;

                int startPos = -1;
                int endPos = -1;
                while ((currentByte = bis.read()) != -1)
                {
                    pos++;
                    if (currentByte == '\n' && lastByte != '\r')
                    {
                        currentLine++;
                        if (startPos != -1)
                        {
                            endPos = pos;
                            break;
                        }
                    }

                    if (currentLine == hint.getLineNumber())
                        startPos = pos;

                    lastByte = currentByte;
                }
                if (endPos == -1)
                    endPos = pos;

                hintMessage.setAttribute(IMarker.CHAR_START, startPos);
                hintMessage.setAttribute(IMarker.CHAR_END, endPos);
            }
            catch (IOException e)
            {
                WindupRuntimePlugin.logError(e.getMessage(), e);
            }
            hintMessage.setAttribute(IMarker.USER_EDITABLE, true);

            result.add(hintMessage);
        }

        Iterable<Classification> classifications = WindupService.getDefault().getClassifications(resource, monitor);
        for (Classification classification : classifications)
        {
            ValidatorMessage message = ValidatorMessage.create(classification.getClassification(), resource);
            message.setAttribute(IMarker.SEVERITY, convertSeverity(classification.getSeverity()));
            message.setType(WindupCorePlugin.WINDUP_CLASSIFICATION_MARKER_ID);
            message.setAttribute(IMarker.LINE_NUMBER, 1);
            message.setAttribute(IMarker.CHAR_START, 0);
            message.setAttribute(IMarker.CHAR_END, 0);

            result.add(message);
        }

        return result;
    }

    private int convertSeverity(Severity severity)
    {
        if (severity == null)
            return IMarker.SEVERITY_WARNING;

        switch (severity)
        {
        case INFO:
            return IMarker.SEVERITY_INFO;
        case WARNING:
            return IMarker.SEVERITY_WARNING;
        case CRITICAL:
            return IMarker.SEVERITY_ERROR;
        case SEVERE:
            return IMarker.SEVERITY_ERROR;
        default:
            return IMarker.SEVERITY_WARNING;
        }
    }

    /**
     * <p>
     * Removes all of the WindUp markers from the given {@link IResource}.
     * </p>
     * 
     * @param resource to cleanup the WindUp markers from
     */
    private static void cleanUpWindUpMarkers(IResource resource)
    {
        if (resource != null)
        {
            try
            {
                resource.deleteMarkers(WindupCorePlugin.WINDUP_CLASSIFICATION_MARKER_ID, true, IResource.DEPTH_INFINITE);
                resource.deleteMarkers(WindupCorePlugin.WINDUP_HINT_MARKER_ID, true, IResource.DEPTH_INFINITE);
            }
            catch (CoreException e)
            {
                WindupCorePlugin.logError("Error cleaning up markers from: " + resource, e); //$NON-NLS-1$
            }
        }
    }
}