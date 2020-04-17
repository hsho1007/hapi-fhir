package ca.uhn.fhir.jpa.empi.svc;

/*-
 * #%L
 * HAPI FHIR JPA Server - Enterprise Master Patient Index
 * %%
 * Copyright (C) 2014 - 2020 University Health Network
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import ca.uhn.fhir.empi.api.EmpiLinkSourceEnum;
import ca.uhn.fhir.empi.api.EmpiMatchResultEnum;
import ca.uhn.fhir.empi.api.IEmpiLinkSvc;
import ca.uhn.fhir.empi.util.AssuranceLevelHelper;
import ca.uhn.fhir.empi.util.PersonHelper;
import ca.uhn.fhir.jpa.entity.EmpiLink;
import ca.uhn.fhir.rest.server.exceptions.InternalErrorException;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.instance.model.api.IIdType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.util.HashMap;
import java.util.Map;

@Service
public class EmpiLinkSvcImpl implements IEmpiLinkSvc {

	@Autowired
	private EmpiResourceDaoSvc myEmpiResourceDaoSvc;
	@Autowired
	private EmpiLinkDaoSvc myEmpiLinkDaoSvc;
	@Autowired
	private PersonHelper myPersonHelper;
	@Autowired
	private ResourceTableHelper myResourceTableHelper;
	private static Map<EmpiMatchResultEnum, String> matchToCanonicalAssuranceLevel = new HashMap<EmpiMatchResultEnum, String>(){{
		put(EmpiMatchResultEnum.NO_MATCH, "level1");
		put(EmpiMatchResultEnum.POSSIBLE_MATCH, "level2");
		put(EmpiMatchResultEnum.MATCH, "level3");
	}};

	@Override
	@Transactional
	public void updateLink(IBaseResource thePerson, IBaseResource theResource, EmpiMatchResultEnum theMatchResult, EmpiLinkSourceEnum theLinkSource) {
		IIdType resourceId = theResource.getIdElement().toUnqualifiedVersionless();

		validateRequestIsLegal(thePerson, theResource, theMatchResult, theLinkSource);

		switch (theMatchResult) {
			case MATCH:
			case POSSIBLE_MATCH:
				myPersonHelper.addOrUpdateLink(thePerson, resourceId, AssuranceLevelHelper.getAssuranceLevel(theMatchResult, theLinkSource));
				break;
			case NO_MATCH:
				myPersonHelper.removeLink(thePerson, resourceId);
			case POSSIBLE_DUPLICATE:
				break;
		}
		myEmpiResourceDaoSvc.updatePerson(thePerson);
		createOrUpdateLinkEntity(thePerson, theResource, theMatchResult, theLinkSource);
	}

	/**
	 * Helper function which runs various business rules about what types of requests are allowed.
	 */
	private void validateRequestIsLegal(IBaseResource thePerson, IBaseResource theResource, EmpiMatchResultEnum theMatchResult, EmpiLinkSourceEnum theLinkSource) {
		EmpiLink existingLink = getEmpiLinkForPersonTargetPair(thePerson, theResource);
		if (existingLink != null && systemIsAttemptingToModifyManualLink(theLinkSource, existingLink.getLinkSource())) {
			throw new InternalErrorException("EMPI system is not allowed to modify links on manually created links");
		}

		if (systemIsAttemptingToAddNoMatch(theLinkSource, theMatchResult)) {
			throw new InternalErrorException("EMPI system is not allowed to automatically NO_MATCH a resource");
		}
	}

	/**
	 * Helper function which detects when the EMPI system is attempting to add a NO_MATCH link, which is not allowed.
	 */
	private boolean systemIsAttemptingToAddNoMatch(EmpiLinkSourceEnum theLinkSource, EmpiMatchResultEnum theMatchResult) {
		return EmpiLinkSourceEnum.AUTO.equals(theLinkSource) && EmpiMatchResultEnum.NO_MATCH.equals(theMatchResult);
	}

	/**
	 * Helper function to let us catch when System EMPI rules are attempting to override a manually defined link.
	 */
	private boolean systemIsAttemptingToModifyManualLink(EmpiLinkSourceEnum theIncomingSource, EmpiLinkSourceEnum theExistingSource) {
		return EmpiLinkSourceEnum.AUTO.equals(theIncomingSource) && EmpiLinkSourceEnum.MANUAL.equals(theExistingSource);
	}

	private EmpiLink getEmpiLinkForPersonTargetPair(IBaseResource thePerson, IBaseResource theCandidate) {
		if (thePerson.getIdElement().getIdPart() == null || theCandidate.getIdElement().getIdPart() == null) {
			return null;
		} else {
			return myEmpiLinkDaoSvc.getLinkByPersonPidAndTargetPid(
				myResourceTableHelper.getPidOrNull(thePerson),
				myResourceTableHelper.getPidOrNull(theCandidate)
			);
		}
	}

	private void createOrUpdateLinkEntity(IBaseResource thePerson, IBaseResource theResource, EmpiMatchResultEnum theMatchResult, EmpiLinkSourceEnum theLinkSource) {
		myEmpiLinkDaoSvc.createOrUpdateLinkEntity(thePerson, theResource, theMatchResult, theLinkSource);
	}




}
