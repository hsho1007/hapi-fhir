package ca.uhn.fhir.jpa.empi.svc;

import ca.uhn.fhir.empi.api.EmpiLinkSourceEnum;
import ca.uhn.fhir.empi.api.EmpiMatchResultEnum;
import ca.uhn.fhir.empi.api.IEmpiConfig;
import ca.uhn.fhir.empi.api.IEmpiLinkSvc;
import ca.uhn.fhir.jpa.dao.DaoMethodOutcome;
import ca.uhn.fhir.jpa.empi.BaseEmpiR4Test;
import ca.uhn.fhir.jpa.empi.dao.IEmpiLinkDao;
import ca.uhn.fhir.jpa.empi.entity.EmpiLink;
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.model.primitive.IdDt;
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Person;
import org.junit.Test;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;
import java.util.Optional;

import static ca.uhn.fhir.rest.api.Constants.CODE_NO_EMPI_MANAGED;
import static ca.uhn.fhir.rest.api.Constants.SYSTEM_EMPI_MANAGED;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.slf4j.LoggerFactory.getLogger;

public class EmpiMatchLinkSvcTest extends BaseEmpiR4Test {
	private static final Logger ourLog = getLogger(EmpiMatchLinkSvcTest.class);

	@Autowired
	private EmpiMatchLinkSvc myEmpiMatchLinkSvc;
	@Autowired
	IEmpiLinkDao myEmpiLinkDao;
	@Autowired
	IEmpiConfig myEmpiConfig;
	@Autowired
	IEmpiLinkSvc myEmpiLinkSvc;

	@Test
	public void testAddPatientLinksToNewPersonIfNoneFound() {
		createPatientAndUpdateLinks(buildJanePatient());
		assertLinkCount(1);
	}

	@Test
	public void testAddPatientLinksToNewPersonIfNoMatch() {
		Patient patient1 = createPatientAndUpdateLinks(buildJanePatient());
		Patient patient2 = createPatientAndUpdateLinks(buildPaulPatient());

		assertLinkCount(2);
		assertThat(patient1, is(not(samePersonAs(patient2))));
	}

	@Test
	public void testAddPatientLinksToExistingPersonIfMatch() {
		Patient patient1 = createPatientAndUpdateLinks(buildJanePatient());
		assertLinkCount(1);

		Patient patient2 = createPatientAndUpdateLinks(buildJanePatient());
		assertLinkCount(2);

		assertThat(patient1, is(samePersonAs(patient2)));
	}

	@Test
	public void testWhenMatchOccursOnPersonThatHasBeenManuallyNOMATCHedThatItIsBlocked() {
		Patient originalJane = createPatientAndUpdateLinks(buildJanePatient());
		IBundleProvider search = myPersonDao.search(new SearchParameterMap());
		IBaseResource janePerson = search.getResources(0,1).get(0);

		//Create a manual NO_MATCH between janePerson and unmatchedJane.
		Patient unmatchedJane= createPatient(buildJanePatient());
		myEmpiLinkSvc.updateLink(janePerson, unmatchedJane, EmpiMatchResultEnum.NO_MATCH, EmpiLinkSourceEnum.MANUAL);

		//rerun EMPI rules against unmatchedJane.
		myEmpiMatchLinkSvc.updateEmpiLinksForPatient(unmatchedJane);

		assertThat(unmatchedJane, is(not(samePersonAs(janePerson))));
		assertThat(unmatchedJane, is(not(linkedTo(originalJane))));
	}

	@Test
	public void testWhenPOSSIBLE_MATCHOccursOnPersonThatHasBeenManuallyNOMATCHedThatItIsBlocked() {
		Patient originalJane = createPatientAndUpdateLinks(buildJanePatient());
		IBundleProvider search = myPersonDao.search(new SearchParameterMap());
		IBaseResource janePerson = search.getResources(0, 1).get(0);

		Patient unmatchedPatient = createPatient(buildJanePatient());

		//This simulates an admin specifically saying that unmatchedPatient does NOT match janePerson.
		myEmpiLinkSvc.updateLink(janePerson, unmatchedPatient, EmpiMatchResultEnum.NO_MATCH, EmpiLinkSourceEnum.MANUAL);
		//TODO change this so that it will only partially match.

		//Now normally, when we run update links, it should link to janePerson. However, this manual NO_MATCH link
		//should cause a whole new Person to be created.
		myEmpiMatchLinkSvc.updateEmpiLinksForPatient(unmatchedPatient);

		assertThat(unmatchedPatient, is(not(samePersonAs(janePerson))));
		assertThat(unmatchedPatient, is(not(linkedTo(originalJane))));
	}



	@Test
	public void testWhenPatientIsCreatedWithEIDThatItPropagatesToNewPersons() {
		String sampleEID = "sample-eid";
		Patient janePatient = addEID(buildJanePatient(), sampleEID);
		janePatient = createPatientAndUpdateLinks(janePatient);

		Optional<EmpiLink> empiLink = myEmpiLinkDaoSvc.getMatchedLinkForTargetPid(janePatient.getIdElement().getIdPartAsLong());
		assertThat(empiLink.isPresent(), is(true));

		Person person = myPersonDao.read(new IdDt(empiLink.get().getPersonPid()));
		Identifier identifier = person.getIdentifierFirstRep();
		assertThat(identifier.getSystem(), is(equalTo(myEmpiConfig.getEmpiRules().getEnterpriseEIDSystem())));
		assertThat(identifier.getValue(), is(equalTo(sampleEID)));
	}

	@Test
	public void testWhenPatientIsCreatedWithoutAnEIDThePersonGetsAutomaticallyAssignedOne() {
		Patient patient = createPatientAndUpdateLinks(buildJanePatient());
		EmpiLink empiLink = myEmpiLinkDaoSvc.getMatchedLinkForTargetPid(patient.getIdElement().getIdPartAsLong()).get();

		Person person = myPersonDao.read(new IdDt(empiLink.getPersonPid()));
		Identifier identifierFirstRep = person.getIdentifierFirstRep();
		assertThat(identifierFirstRep.getSystem(), is(equalTo(myEmpiConfig.getEmpiRules().getEnterpriseEIDSystem())));
		assertThat(identifierFirstRep.getValue(), is(notNullValue()));
	}

	@Test
	public void testPatientAttributesAreCopiedOverWhenPersonIsCreatedFromPatient() {
		Patient patient = createPatientAndUpdateLinks(buildPatientWithNameIdAndBirthday("Gary", "GARY_ID", new Date()));;

		Optional<EmpiLink> empiLink = myEmpiLinkDaoSvc.getMatchedLinkForTargetPid(patient.getIdElement().getIdPartAsLong());
		Person read = myPersonDao.read(new IdDt(empiLink.get().getPersonPid()));

		assertThat(read.getNameFirstRep().getFamily(), is(equalTo(patient.getNameFirstRep().getFamily())));
		assertThat(read.getNameFirstRep().getGivenAsSingleString(), is(equalTo(patient.getNameFirstRep().getGivenAsSingleString())));
		assertThat(read.getBirthDateElement().toHumanDisplay(), is(equalTo(patient.getBirthDateElement().toHumanDisplay())));
	}


	@Test
	public void testPatientMatchingAnotherPatientLinksToSamePerson() {
		Patient janePatient = createPatientAndUpdateLinks(buildJanePatient());
		Patient sameJanePatient = createPatientAndUpdateLinks(buildJanePatient());
		assertThat(janePatient, is(samePersonAs(sameJanePatient)));
	}


	@Test
	public void testIncomingPatientWithEIDThatMatchesPersonWithDifferentEIDCausesOverwriteOnPerson(){
		// Existing Person with system-assigned EID found linked from matched Patient.  incoming Patient has EID.  Replace Person system-assigned EID with Patient EID.
		Patient patient = createPatientAndUpdateLinks(buildJanePatient());

		Patient janePatient= addEID(buildJanePatient(), "12345");
		createPatientAndUpdateLinks(janePatient);

		//We want to make sure the patients were linked to the same person.
		assertThat(patient, is(samePersonAs(janePatient)));

		EmpiLink empiLink = myEmpiLinkDaoSvc.getMatchedLinkForTargetPid(patient.getIdElement().getIdPartAsLong()).get();
		Person person = myPersonDao.read(new IdDt(empiLink.getPersonPid()));
		Identifier identifier = person.getIdentifierFirstRep();

		assertThat(identifier.getSystem(), is(equalTo(myEmpiConfig.getEmpiRules().getEnterpriseEIDSystem())));
		assertThat(identifier.getValue(), is(equalTo("12345")));
	}

	public Patient addEID(Patient thePatient, String theEID) {
		thePatient.addIdentifier().setSystem(myEmpiConfig.getEmpiRules().getEnterpriseEIDSystem()).setValue(theEID);
		return thePatient;
	}

	@Test
	public void testIncomingPatientWithEIDMatchesAnotherPatientWithSameEIDAreLinked() {
		Patient patient1 = addEID(buildJanePatient(), "12345");
		patient1 = createPatientAndUpdateLinks(patient1);

		Patient patient2 = addEID(buildPaulPatient(), "12345");
		patient2 = createPatientAndUpdateLinks(patient2);

		assertThat(patient1, is(samePersonAs(patient2)));
	}


	@Test
	public void testDuplicatePersonLinkIsCreatedWhenAnIncomingPatientArrivesWithEIDThatMatchesAnotherEIDPatient() {
		// Existing Person with legit EID (from a Patient) found linked from matched Patient.  incoming Patient has different EID.   Create new Person with incoming EID and link.
		// Record somehow (design TBD) that these two Persons may be duplicates.  -- Maybe we have a special kind of EmpiLink table entry where the target also points to a Person and it's
		// flagged with a special PROBABLE_DUPLICATE match status?
		fail();
	}



	@Test
	public void testPatientWithNoEmpiTagIsNotMatched() {
		// Patient with "no-empi" tag is not matched
		Patient janePatient = buildJanePatient();
		janePatient.getMeta().addTag(SYSTEM_EMPI_MANAGED, CODE_NO_EMPI_MANAGED, "Don't EMPI on me!");
		createPatientAndUpdateLinks(janePatient);
		assertLinkCount(0);
	}

	private Patient createPatientAndUpdateLinks(Patient thePatient) {
		//Note that since our empi-rules block on active=true, all patients must be active.
		thePatient.setActive(true);
		DaoMethodOutcome daoMethodOutcome = myPatientDao.create(thePatient);
		thePatient.setId(daoMethodOutcome.getId());
		myEmpiMatchLinkSvc.updateEmpiLinksForPatient(thePatient);
		return thePatient;
	}
}