package org.openmrs.module.labonfhir.api.fhir;


import java.util.HashSet;

import ca.uhn.fhir.model.api.Include;
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import ca.uhn.fhir.rest.param.DateRangeParam;
import ca.uhn.fhir.rest.param.ReferenceAndListParam;
import ca.uhn.fhir.rest.param.TokenAndListParam;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.openmrs.Obs;
import org.openmrs.TestOrder;
import org.openmrs.module.fhir2.FhirConstants;
import org.openmrs.module.fhir2.api.FhirServiceRequestService;
import org.openmrs.module.fhir2.api.dao.FhirServiceRequestDao;
import org.openmrs.module.fhir2.api.impl.BaseFhirService;
import org.openmrs.module.fhir2.api.search.SearchQuery;
import org.openmrs.module.fhir2.api.search.SearchQueryInclude;
import org.openmrs.module.fhir2.api.search.param.SearchParameterMap;
import org.openmrs.module.fhir2.api.translators.ServiceRequestTranslator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Primary
@Component
@Transactional
@Getter(AccessLevel.PROTECTED)
@Setter(AccessLevel.PACKAGE)
public class ServiceRequestServiceImpl extends BaseFhirService<ServiceRequest, Obs> implements FhirServiceRequestService {

	@Autowired
	private ServiceRequestTranslator<Obs> translator;

	@Autowired
	private FhirServiceRequestDao<Obs> dao;

	@Autowired
	private SearchQueryInclude<ServiceRequest> searchQueryInclude;

	@Autowired
	private SearchQuery<Obs, ServiceRequest, FhirServiceRequestDao<Obs>, ServiceRequestTranslator<Obs>, SearchQueryInclude<ServiceRequest>> searchQuery;

	@Override
	public IBundleProvider searchForServiceRequests(ReferenceAndListParam patientReference, TokenAndListParam code,
			ReferenceAndListParam encounterReference, ReferenceAndListParam participantReference, DateRangeParam occurrence,
			TokenAndListParam uuid, DateRangeParam lastUpdated, HashSet<Include> includes) {

		SearchParameterMap theParams = new SearchParameterMap()
				.addParameter(FhirConstants.PATIENT_REFERENCE_SEARCH_HANDLER, patientReference)
				.addParameter(FhirConstants.CODED_SEARCH_HANDLER, code)
				.addParameter(FhirConstants.ENCOUNTER_REFERENCE_SEARCH_HANDLER, encounterReference)
				.addParameter(FhirConstants.PARTICIPANT_REFERENCE_SEARCH_HANDLER, participantReference)
				.addParameter(FhirConstants.DATE_RANGE_SEARCH_HANDLER, occurrence)
				.addParameter(FhirConstants.COMMON_SEARCH_HANDLER, FhirConstants.ID_PROPERTY, uuid)
				.addParameter(FhirConstants.COMMON_SEARCH_HANDLER, FhirConstants.LAST_UPDATED_PROPERTY, lastUpdated)
				.addParameter(FhirConstants.INCLUDE_SEARCH_HANDLER, includes);

		return searchQuery.getQueryResults(theParams, dao, translator, searchQueryInclude);
	}

}
