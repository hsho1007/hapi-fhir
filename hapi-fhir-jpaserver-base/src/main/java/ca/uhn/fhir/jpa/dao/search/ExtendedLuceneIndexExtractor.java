package ca.uhn.fhir.jpa.dao.search;

/*-
 * #%L
 * HAPI FHIR JPA Server
 * %%
 * Copyright (C) 2014 - 2022 Smile CDR, Inc.
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

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.RuntimeSearchParam;
import ca.uhn.fhir.jpa.model.entity.ResourceLink;
import ca.uhn.fhir.jpa.model.search.ExtendedLuceneIndexData;
import ca.uhn.fhir.jpa.searchparam.extractor.ISearchParamExtractor;
import ca.uhn.fhir.jpa.searchparam.extractor.ResourceIndexedSearchParams;
import ca.uhn.fhir.rest.api.RestSearchParameterTypeEnum;
import org.hl7.fhir.instance.model.api.IBase;
import org.hl7.fhir.instance.model.api.IBaseCoding;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Extract search params for advanced lucene indexing.
 * <p>
 * This class re-uses the extracted JPA entities to build an ExtendedLuceneIndexData instance.
 */
public class ExtendedLuceneIndexExtractor {

	private final FhirContext myContext;
	private final Map<String, RuntimeSearchParam> myParams;
	private final ISearchParamExtractor mySearchParamExtractor;

	public ExtendedLuceneIndexExtractor(FhirContext theContext, Map<String, RuntimeSearchParam> theActiveParams, ISearchParamExtractor theSearchParamExtractor) {
		myContext = theContext;
		myParams = theActiveParams;
		mySearchParamExtractor = theSearchParamExtractor;
	}

	@NotNull
	public ExtendedLuceneIndexData extract(IBaseResource theResource, ResourceIndexedSearchParams theNewParams) {
		ExtendedLuceneIndexData retVal = new ExtendedLuceneIndexData(myContext);

		extractAutocompleteTokens(theResource, retVal);

		theNewParams.myStringParams.forEach(nextParam ->
			retVal.addStringIndexData(nextParam.getParamName(), nextParam.getValueExact()));

		theNewParams.myTokenParams.forEach(nextParam ->
			retVal.addTokenIndexDataIfNotPresent(nextParam.getParamName(), nextParam.getSystem(), nextParam.getValue()));

		theNewParams.myDateParams.forEach(nextParam ->
			retVal.addDateIndexData(nextParam.getParamName(), nextParam.getValueLow(), nextParam.getValueLowDateOrdinal(),
				nextParam.getValueHigh(), nextParam.getValueHighDateOrdinal()));

		if (!theNewParams.myLinks.isEmpty()) {

			// awkwardly, links are indexed by jsonpath, not by search param.
			// so we re-build the linkage.
			Map<String, List<String>> linkPathToParamName = new HashMap<>();
			for (String nextParamName : theNewParams.getPopulatedResourceLinkParameters()) {
				RuntimeSearchParam sp = myParams.get(nextParamName);
				List<String> pathsSplit = sp.getPathsSplit();
				for (String nextPath : pathsSplit) {
					// we want case-insensitive matching
					nextPath = nextPath.toLowerCase(Locale.ROOT);

					linkPathToParamName
						.computeIfAbsent(nextPath, (p) -> new ArrayList<>())
						.add(nextParamName);
				}
			}

			for (ResourceLink nextLink : theNewParams.getResourceLinks()) {
				String insensitivePath = nextLink.getSourcePath().toLowerCase(Locale.ROOT);
				List<String> paramNames = linkPathToParamName.getOrDefault(insensitivePath, Collections.emptyList());
				for (String nextParamName : paramNames) {
					String qualifiedTargetResourceId = nextLink.getTargetResourceType() + "/" + nextLink.getTargetResourceId();
					retVal.addResourceLinkIndexData(nextParamName, qualifiedTargetResourceId);
				}
			}
		}

		return retVal;
	}

	/**
	 * Re-extract token parameters so we can distinguish
	 */
	private void extractAutocompleteTokens(IBaseResource theResource, ExtendedLuceneIndexData theRetVal) {
		// we need to re-index token params to match up display with codes.
		myParams.values().stream()
			.filter(p->p.getParamType() == RestSearchParameterTypeEnum.TOKEN)
			// TODO it would be nice to reuse TokenExtractor
			.forEach(p-> mySearchParamExtractor.extractValues(p.getPath(), theResource)
				.stream()
				.forEach(nextValue->indexTokenValue(theRetVal, p, nextValue)
			));
	}

	private void indexTokenValue(ExtendedLuceneIndexData theRetVal, RuntimeSearchParam p, IBase nextValue) {
		String nextType = mySearchParamExtractor.toRootTypeName(nextValue);
		String spName = p.getName();
		switch (nextType) {
		case "CodeableConcept":
			addToken_CodeableConcept(theRetVal, spName, nextValue);
			break;
		case "Coding":
			addToken_Coding(theRetVal, spName, (IBaseCoding) nextValue);
			break;
			// TODO share this with TokenExtractor and introduce a ITokenIndexer interface.
		// Ignore unknown types for now.
		// This is just for autocomplete, and we are focused on Observation.code, category, combo-code, etc.
//					case "Identifier":
//						mySearchParamExtractor.addToken_Identifier(myResourceTypeName, params, searchParam, value);
//						break;
//					case "ContactPoint":
//						mySearchParamExtractor.addToken_ContactPoint(myResourceTypeName, params, searchParam, value);
//						break;
		default:
			break;
	}
	}

	private void addToken_CodeableConcept(ExtendedLuceneIndexData theRetVal, String theSpName, IBase theValue) {
		List<IBase> codings = mySearchParamExtractor.getCodingsFromCodeableConcept(theValue);
		for (IBase nextCoding : codings) {
			addToken_Coding(theRetVal, theSpName, (IBaseCoding) nextCoding);
		}
	}

	private void addToken_Coding(ExtendedLuceneIndexData theRetVal, String theSpName, IBaseCoding theNextValue) {
		theRetVal.addTokenIndexData(theSpName, theNextValue);
	}
}
