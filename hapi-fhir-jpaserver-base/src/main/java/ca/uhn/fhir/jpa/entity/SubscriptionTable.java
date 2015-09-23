package ca.uhn.fhir.jpa.entity;

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.ForeignKey;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OneToOne;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.persistence.UniqueConstraint;

import ca.uhn.fhir.model.dstu2.valueset.SubscriptionStatusEnum;

//@formatter:off
@Entity
@Table(name = "HFJ_SUBSCRIPTION", uniqueConstraints= {
	@UniqueConstraint(name="IDX_SUBS_RESID", columnNames= { "RES_ID" }),
	@UniqueConstraint(name="IDX_SUBS_NEXTCHECK", columnNames= { "SUBSCRIPTION_STATUS", "NEXT_CHECK" })
})
@NamedQueries({
	@NamedQuery(name="Q_HFJ_SUBSCRIPTION_SET_STATUS", query="UPDATE SubscriptionTable t SET t.myStatus = :status WHERE t.myResId = :res_id"),
	@NamedQuery(name="Q_HFJ_SUBSCRIPTION_NEXT_CHECK", query="SELECT t FROM SubscriptionTable t WHERE t.myStatus = :status AND t.myNextCheck <= :next_check"),
	@NamedQuery(name="Q_HFJ_SUBSCRIPTION_GET_BY_RES", query="SELECT t FROM SubscriptionTable t WHERE t.myResId = :res_id"),
	@NamedQuery(name="Q_HFJ_SUBSCRIPTION_DELETE", query="DELETE FROM SubscriptionTable t WHERE t.myResId = :res_id"),
})
//@formatter:on
public class SubscriptionTable {

	@Column(name = "CHECK_INTERVAL", nullable = false)
	private long myCheckInterval;

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE)
	@SequenceGenerator(name = "SEQ_SUBSCRIPTION_ID", sequenceName = "SEQ_SUBSCRIPTION_ID")
	@Column(name = "PID", insertable = false, updatable = false)
	private Long myId;

	@Temporal(TemporalType.TIMESTAMP)
	@Column(name = "NEXT_CHECK", nullable = false)
	private Date myNextCheck;

	@Temporal(TemporalType.TIMESTAMP)
	@Column(name = "MOST_RECENT_MATCH", nullable = false)
	private Date myMostRecentMatch;

	@Column(name = "RES_ID", insertable = false, updatable = false)
	private Long myResId;

	@Column(name = "SUBSCRIPTION_STATUS", nullable = false, length = 20)
	@Enumerated(EnumType.STRING)
	private SubscriptionStatusEnum myStatus;

	@OneToOne()
	@JoinColumn(name = "RES_ID", insertable = true, updatable = false, referencedColumnName = "RES_ID", foreignKey = @ForeignKey(name = "FK_SUBSCRIPTION_RESOURCE_ID") )
	private ResourceTable mySubscriptionResource;

	public long getCheckInterval() {
		return myCheckInterval;
	}

	public Long getId() {
		return myId;
	}

	public Date getNextCheck() {
		return myNextCheck;
	}

	public SubscriptionStatusEnum getStatus() {
		return myStatus;
	}

	public ResourceTable getSubscriptionResource() {
		return mySubscriptionResource;
	}

	public void setCheckInterval(long theCheckInterval) {
		myCheckInterval = theCheckInterval;
	}

	public void setNextCheck(Date theNextCheck) {
		myNextCheck = theNextCheck;
	}

	public void setStatus(SubscriptionStatusEnum theStatus) {
		myStatus = theStatus;
	}

	public void setSubscriptionResource(ResourceTable theSubscriptionResource) {
		mySubscriptionResource = theSubscriptionResource;
	}

	public Date getMostRecentMatch() {
		return myMostRecentMatch;
	}

	public void setMostRecentMatch(Date theMostRecentMatch) {
		myMostRecentMatch = theMostRecentMatch;
	}

}
