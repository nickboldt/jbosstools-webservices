/******************************************************************************* 
 * Copyright (c) 2012 Red Hat, Inc. 
 * Distributed under license by Red Hat, Inc. All rights reserved. 
 * This program is made available under the terms of the 
 * Eclipse Public License v1.0 which accompanies this distribution, 
 * and is available at http://www.eclipse.org/legal/epl-v10.html 
 * 
 * Contributors: 
 * Red Hat, Inc. - initial API and implementation 
 ******************************************************************************/
package org.jboss.tools.ws.jaxrs.ui.internal.validation;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.ISourceRange;
import org.jboss.tools.ws.jaxrs.core.internal.metamodel.domain.JaxrsBaseElement;
import org.jboss.tools.ws.jaxrs.core.internal.metamodel.domain.JaxrsResource;
import org.jboss.tools.ws.jaxrs.core.internal.metamodel.domain.JaxrsResourceField;
import org.jboss.tools.ws.jaxrs.core.internal.metamodel.domain.JaxrsResourceMethod;
import org.jboss.tools.ws.jaxrs.core.jdt.Annotation;
import org.jboss.tools.ws.jaxrs.core.jdt.JdtUtils;
import org.jboss.tools.ws.jaxrs.core.jdt.SourceType;
import org.jboss.tools.ws.jaxrs.core.metamodel.domain.IJaxrsParamConverterProvider;
import org.jboss.tools.ws.jaxrs.ui.internal.utils.Logger;
import org.jboss.tools.ws.jaxrs.ui.preferences.JaxrsPreferences;

/**
 * JAX-RS Resource Method validator.
 * 
 * @author Xavier Coulon
 * 
 */
public class JaxrsResourceFieldValidatorDelegate extends AbstractJaxrsElementValidatorDelegate<JaxrsResourceField> {

	/** The underlying marker manager.*/
	private final IMarkerManager markerManager;

	/**
	 * Constructor
	 * @param markerManager the underlying marker manager to use
	 */
	public JaxrsResourceFieldValidatorDelegate(final IMarkerManager markerManager) {
		this.markerManager = markerManager;
	}

	/**
	 * @throws CoreException
	 * @see org.jboss.tools.ws.jaxrs.ui.internal.validation.AbstractJaxrsElementValidatorDelegate#internalValidate(Object)
	 */
	@Override
	void internalValidate(final JaxrsResourceField resourceField) throws CoreException {
		Logger.debug("Validating element {}", resourceField);
		// markers were already removed at the Resource level, they should *not*
		// be removed again here (because another resource method
		// of the same parent resource may already have been validated and have
		// markers created.
		validateParameterType(resourceField);
		validateNotUnboundPathParamAnnotationValue(resourceField);
	}

	/**
	 * Validate that the {@code @PathParam} annotation value matches a given
	 * {@code @Path} template parameter at the parent {@link JaxrsResource}
	 * level or on any sibling {@link JaxrsResourceMethod}.
	 * 
	 * @param resourceField the resource field to validate.
	 * @throws CoreException 
	 */
	private void validateNotUnboundPathParamAnnotationValue(final JaxrsResourceField resourceField) throws CoreException {
		final Annotation pathParamAnnotation = resourceField.getPathParamAnnotation();
		if(pathParamAnnotation == null) {
			return;
		}
		final JaxrsResource parentResource = resourceField.getParentResource();
		final Set<String> pathTemplateParameters = new HashSet<String>();
		// put all path template parameters at the parent resource level
		pathTemplateParameters.addAll(parentResource.getPathTemplateParameters().keySet());
		// also include all template parameters at the sibling resource methods level
		final Collection<JaxrsResourceMethod> resourceMethods = parentResource.getMethods().values();
		for(JaxrsResourceMethod resourceMethod : resourceMethods) {
			pathTemplateParameters.addAll(resourceMethod.getPathTemplateParameters().keySet());
		}
		// now, check:
		if(!pathTemplateParameters.contains(pathParamAnnotation.getValue())) {
			final ISourceRange annotationValueRange = JdtUtils.resolveMemberPairValueRange(pathParamAnnotation.getJavaAnnotation(), "value");
			markerManager.addMarker((JaxrsBaseElement)resourceField, annotationValueRange,
					JaxrsValidationMessages.RESOURCE_FIELD_UNBOUND_PATHPARAM_ANNOTATION_VALUE,
					new String[] { pathParamAnnotation.getValue(), parentResource.getJavaElement().getFullyQualifiedName() },
					JaxrsPreferences.RESOURCE_FIELD_UNBOUND_PATHPARAM_ANNOTATION_VALUE);
		}
	}

	/**
	 * Validates the type of the parameter when annotated with {@code @PathParam},
	 * {@code @QueryParam} and {@code @MatrixParam}.
	 * 
	 * @param resourceField
	 *            the resource field to validate
	 * @throws CoreException
	 * @see JaxrsParameterValidatorDelegate
	 */
	private void validateParameterType(final JaxrsResourceField resourceField) throws CoreException {
		// for now, we bypass this validation if the metamodel has at least one ParamConverterProvider
		final Collection<IJaxrsParamConverterProvider> allParamConverterProviders = resourceField.getMetamodel().findAllParamConverterProviders();
		if(allParamConverterProviders != null && ! allParamConverterProviders.isEmpty()) {
			return;
		}
		
		final JaxrsParameterValidatorDelegate parameterValidatorDelegate = new JaxrsParameterValidatorDelegate();
		final SourceType type = resourceField.getType();
		final boolean isValid = parameterValidatorDelegate.validate(type, resourceField.getMetamodel()
				.getJavaProject(), new NullProgressMonitor());
		if (!isValid) {
			markerManager.addMarker((JaxrsBaseElement)resourceField, resourceField.getJavaElement().getNameRange(),
					JaxrsValidationMessages.RESOURCE_METHOD_INVALID_ANNOTATED_PARAMETER_TYPE,
					new String[] { type.getErasureName() },
					JaxrsPreferences.RESOURCE_METHOD_INVALID_ANNOTATED_PARAMETER_TYPE);
		}
	}

}
