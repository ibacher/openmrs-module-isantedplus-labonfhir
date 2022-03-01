package org.openmrs.module.labonfhir.api.fhir;

import static org.hibernate.criterion.Restrictions.and;
import static org.hibernate.criterion.Restrictions.or;

import java.util.Optional;
import java.util.stream.Stream;

import ca.uhn.fhir.rest.param.DateRangeParam;
import ca.uhn.fhir.rest.param.ReferenceAndListParam;
import ca.uhn.fhir.rest.param.TokenAndListParam;
import lombok.AccessLevel;
import lombok.Setter;
import org.hibernate.Criteria;
import org.hibernate.criterion.Criterion;
import org.openmrs.Obs;
import org.openmrs.TestOrder;
import org.openmrs.module.fhir2.FhirConstants;
import org.openmrs.module.fhir2.api.dao.FhirServiceRequestDao;
import org.openmrs.module.fhir2.api.dao.impl.BaseFhirDao;
import org.openmrs.module.fhir2.api.search.param.SearchParameterMap;
import org.openmrs.module.labonfhir.LabOnFhirConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Primary
@Component
@Setter(AccessLevel.PACKAGE)
public class ServiceRequestDaoImpl extends BaseFhirDao<Obs> implements FhirServiceRequestDao<Obs> {

	@Autowired
	private LabOnFhirConfig config;

	@Override
	protected void setupSearchParams(Criteria criteria, SearchParameterMap theParams) {
		theParams.getParameters().forEach(entry -> {
			switch (entry.getKey()) {
				case FhirConstants.ENCOUNTER_REFERENCE_SEARCH_HANDLER:
					entry.getValue().forEach(param -> handleEncounterReference("e", (ReferenceAndListParam) param.getParam())
							.ifPresent(c -> criteria.createAlias("encounter", "e").add(c)));
					break;
				case FhirConstants.PATIENT_REFERENCE_SEARCH_HANDLER:
					entry.getValue().forEach(patientReference -> handlePatientReference(criteria,
							(ReferenceAndListParam) patientReference.getParam(), "patient"));
					break;
				case FhirConstants.CODED_SEARCH_HANDLER:
					entry.getValue().forEach(code -> handleCodedConcept(criteria, (TokenAndListParam) code.getParam()));
					break;
				case FhirConstants.PARTICIPANT_REFERENCE_SEARCH_HANDLER:
					entry.getValue().forEach(participantReference -> handleProviderReference(criteria,
							(ReferenceAndListParam) participantReference.getParam()));
					break;
				case FhirConstants.DATE_RANGE_SEARCH_HANDLER:
					entry.getValue().forEach(dateRangeParam -> handleDateRange((DateRangeParam) dateRangeParam.getParam())
							.ifPresent(criteria::add));
					break;
				case FhirConstants.COMMON_SEARCH_HANDLER:
					handleCommonSearchParameters(entry.getValue()).ifPresent(criteria::add);
					break;
			}
		});
	}

	private void handleCodedConcept(Criteria criteria, TokenAndListParam code) {
		if (code != null) {
			if (lacksAlias(criteria, "c")) {
				criteria.createAlias("concept", "c");
			}

			handleCodeableConcept(criteria, code, "c", "cm", "crt").ifPresent(criteria::add);
		}
	}

	private Optional<Criterion> handleDateRange(DateRangeParam dateRangeParam) {
		if (dateRangeParam == null) {
			return Optional.empty();
		}

		return Optional.of(and(toCriteriaArray(Stream.of(
				Optional.of(or(toCriteriaArray(Stream.of(handleDate("scheduledDate", dateRangeParam.getLowerBound()),
						handleDate("dateActivated", dateRangeParam.getLowerBound()))))),
				Optional.of(or(toCriteriaArray(Stream.of(handleDate("dateStopped", dateRangeParam.getUpperBound()),
						handleDate("autoExpireDate", dateRangeParam.getUpperBound())))))))));
	}

}
