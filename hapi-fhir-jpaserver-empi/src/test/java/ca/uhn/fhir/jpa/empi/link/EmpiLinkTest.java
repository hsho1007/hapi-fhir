package ca.uhn.fhir.jpa.empi.link;

import ca.uhn.fhir.empi.rules.EmpiMatchResultEnum;
import ca.uhn.fhir.jpa.api.EmpiLinkSourceEnum;
import ca.uhn.fhir.jpa.api.IEmpiLinkSvc;
import ca.uhn.fhir.jpa.empi.BaseEmpiR4Test;
import ca.uhn.fhir.jpa.empi.dao.IEmpiLinkDao;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Person;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.Assert.assertEquals;

public class EmpiLinkTest extends BaseEmpiR4Test {
	@Autowired
	IEmpiLinkSvc myEmpiLinkSvc;
	@Autowired
	IEmpiLinkDao myEmpiLinkDao;

	@Test
	public void compareEmptyPatients() {
		Patient patient = new Patient();
		patient.setId("Patient/1");
		EmpiMatchResultEnum result = myEmpiResourceComparator.getMatchResult(patient, patient);
		assertEquals(EmpiMatchResultEnum.NO_MATCH, result);
	}

	@Test
	public void testCreateLink() {
		long initialLinkCount = myEmpiLinkDao.count();
		assertEquals(0, myPerson.getLink().size());
		myEmpiLinkSvc.createLink(myPerson, myPatient, EmpiLinkSourceEnum.AUTO);
		assertEquals(1 + initialLinkCount, myEmpiLinkDao.count());
		Person newPerson = myPersonDao.read(myPersonId);
		assertEquals(1, newPerson.getLink().size());
	}
}