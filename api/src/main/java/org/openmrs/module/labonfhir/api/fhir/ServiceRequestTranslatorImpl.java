package org.openmrs.module.labonfhir.api.fhir;
/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */

import static org.apache.commons.lang3.Validate.notNull;

import javax.annotation.Nonnull;

import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Set;
import java.util.stream.Collectors;

import ca.uhn.fhir.rest.api.server.IBundleProvider;
import ca.uhn.fhir.rest.param.ReferenceAndListParam;
import ca.uhn.fhir.rest.param.ReferenceOrListParam;
import ca.uhn.fhir.rest.param.ReferenceParam;
import lombok.AccessLevel;
import lombok.Setter;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.hl7.fhir.r4.model.Task;
import org.openmrs.Encounter;
import org.openmrs.EncounterProvider;
import org.openmrs.Obs;
import org.openmrs.Order;
import org.openmrs.Patient;
import org.openmrs.Provider;
import org.openmrs.TestOrder;
import org.openmrs.module.fhir2.api.FhirTaskService;
import org.openmrs.module.fhir2.api.translators.ConceptTranslator;
import org.openmrs.module.fhir2.api.translators.EncounterReferenceTranslator;
import org.openmrs.module.fhir2.api.translators.OrderIdentifierTranslator;
import org.openmrs.module.fhir2.api.translators.PatientReferenceTranslator;
import org.openmrs.module.fhir2.api.translators.PractitionerReferenceTranslator;
import org.openmrs.module.fhir2.api.translators.ServiceRequestTranslator;
import org.openmrs.module.fhir2.api.translators.impl.BaseReferenceHandlingTranslator;
import org.openmrs.module.labonfhir.LabOnFhirConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Primary
@Component
@Setter(AccessLevel.PACKAGE)
public class ServiceRequestTranslatorImpl extends BaseReferenceHandlingTranslator implements ServiceRequestTranslator<Obs> {

	private static final int START_INDEX = 0;

	private static final int END_INDEX = 10;

	@Autowired
	private LabOnFhirConfig config;

	@Autowired
	private FhirTaskService taskService;

	@Autowired
	private ConceptTranslator conceptTranslator;

	@Autowired
	private PatientReferenceTranslator patientReferenceTranslator;

	@Autowired
	private EncounterReferenceTranslator<Encounter> encounterReferenceTranslator;

	@Autowired
	private PractitionerReferenceTranslator<Provider> providerReferenceTranslator;

	@Autowired
	private OrderIdentifierTranslator orderIdentifierTranslator;

	@Override
	public ServiceRequest toFhirResource(@Nonnull Obs order) {
		notNull(order, "The Obs object should not be null");

		if (!order.getConcept().getUuid().equals(config.getTestOrderConceptUuid())) {
			return null;
		}

		ServiceRequest serviceRequest = new ServiceRequest();

		serviceRequest.setId(order.getUuid());

		serviceRequest.setStatus(determineServiceRequestStatus(order));

		serviceRequest.setCode(conceptTranslator.toFhirResource(order.getValueCoded()));

		serviceRequest.setIntent(ServiceRequest.ServiceRequestIntent.ORDER);

		serviceRequest.setSubject(patientReferenceTranslator.toFhirResource((Patient) order.getPerson()));

		serviceRequest.setEncounter(encounterReferenceTranslator.toFhirResource(order.getEncounter()));

		serviceRequest.setRequester(providerReferenceTranslator.toFhirResource(determineRequester(order)));

		serviceRequest.setPerformer(Collections.singletonList(determineServiceRequestPerformer(order.getUuid())));

		serviceRequest.getMeta().setLastUpdated(order.getDateChanged());

		// TODO: Figure out how Obs structure translates to these fields
		/*
		serviceRequest
				.setOccurrence(new Period().setStart(order.getEffectiveStartDate()).setEnd(order.getEffectiveStopDate()));

		if (order.getPreviousOrder() != null
				&& (order.getAction() == Order.Action.DISCONTINUE || order.getAction() == Order.Action.REVISE)) {
			serviceRequest.setReplaces((Collections.singletonList(createOrderReference(order.getPreviousOrder())
					.setIdentifier(orderIdentifierTranslator.toFhirResource(order.getPreviousOrder())))));
		} else if (order.getPreviousOrder() != null && order.getAction() == Order.Action.RENEW) {
			serviceRequest.setBasedOn(Collections.singletonList(createOrderReference(order.getPreviousOrder())
					.setIdentifier(orderIdentifierTranslator.toFhirResource(order.getPreviousOrder()))));
		}
		*/

		return serviceRequest;
	}

	private Provider determineRequester(Obs obs) {
		if (obs.getEncounter() != null) {
			Encounter encounter = obs.getEncounter();

			Set<EncounterProvider> encounterProviders = encounter.getEncounterProviders();

			if (encounterProviders != null && !encounterProviders.isEmpty()) {
				try {
					return encounterProviders.iterator().next().getProvider();
				} catch (Exception ignored) {}
			}
		}

		return null;
	}

	@Override
	public Obs toOpenmrsType(@Nonnull ServiceRequest resource) {
		throw new UnsupportedOperationException();
	}

	private ServiceRequest.ServiceRequestStatus determineServiceRequestStatus(Obs order) {

		Date currentDate = new Date();

		IBundleProvider results = taskService.searchForTasks(
				new ReferenceAndListParam()
						.addAnd(new ReferenceOrListParam().add(new ReferenceParam("ServiceRequest", null, order.getUuid()))),
				null, null, null, null, null);

		Collection<Task> serviceRequestTasks = results.getResources(START_INDEX, END_INDEX).stream().map(p -> (Task) p)
				.collect(Collectors.toList());

		ServiceRequest.ServiceRequestStatus serviceRequestStatus = ServiceRequest.ServiceRequestStatus.UNKNOWN;

		if (serviceRequestTasks.size() != 1) {
			return serviceRequestStatus;
		}

		Task serviceRequestTask = serviceRequestTasks.iterator().next();

		if (serviceRequestTask.getStatus() != null) {
			switch (serviceRequestTask.getStatus()) {
				case ACCEPTED:
				case REQUESTED:
					serviceRequestStatus = ServiceRequest.ServiceRequestStatus.ACTIVE;
					break;
				case REJECTED:
					serviceRequestStatus = ServiceRequest.ServiceRequestStatus.REVOKED;
					break;
				case COMPLETED:
					serviceRequestStatus = ServiceRequest.ServiceRequestStatus.COMPLETED;
					break;
			}
		}
		return serviceRequestStatus;
	}

	private Reference determineServiceRequestPerformer(String orderUuid) {
		IBundleProvider results = taskService.searchForTasks(
				new ReferenceAndListParam()
						.addAnd(new ReferenceOrListParam().add(new ReferenceParam("ServiceRequest", null, orderUuid))),
				null, null, null, null, null);

		Collection<Task> serviceRequestTasks = results.getResources(START_INDEX, END_INDEX).stream().map(p -> (Task) p)
				.collect(Collectors.toList());

		if (serviceRequestTasks.size() != 1) {
			return null;
		}

		return serviceRequestTasks.iterator().next().getOwner();
	}
}
