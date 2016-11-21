package org.apereo.cas.ticket;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apereo.cas.authentication.Authentication;
import org.apereo.cas.authentication.principal.Service;
import org.apereo.cas.ticket.proxy.ProxyGrantingTicket;
import org.springframework.util.Assert;

import javax.persistence.Column;
import javax.persistence.DiscriminatorColumn;
import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Lob;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

/**
 * Domain object representing a Service Ticket. A service ticket grants specific
 * access to a particular service. It will only work for a particular service.
 * Generally, it is a one time use Ticket, but the specific expiration policy
 * can be anything.
 *
 * @author Scott Battaglia
 * @since 3.0.0
 */
@Entity
@Table(name = "SERVICETICKET")
@DiscriminatorColumn(name = "TYPE")
@DiscriminatorValue(ServiceTicket.PREFIX)
public class ServiceTicketImpl extends AbstractTicket implements ServiceTicket {
    
    private static final long serialVersionUID = -4223319704861765405L;

    /**
     * The {@link TicketGrantingTicket} this is associated with.
     */
    @ManyToOne(targetEntity = TicketGrantingTicketImpl.class)
    private TicketGrantingTicket ticketGrantingTicket;

    /**
     * The service this ticket is valid for.
     */
    @Lob
    @Column(name = "SERVICE", nullable = false, length = Integer.MAX_VALUE)
    private Service service;

    /**
     * Is this service ticket the result of a new login.
     */
    @Column(name = "FROM_NEW_LOGIN", nullable = false)
    private boolean fromNewLogin;

    @Column(name = "TICKET_ALREADY_GRANTED", nullable = false)
    private Boolean grantedTicketAlready = Boolean.FALSE;

    /**
     * Instantiates a new service ticket impl.
     */
    public ServiceTicketImpl() {
        // exists for JPA purposes
    }

    /**
     * Constructs a new ServiceTicket with a Unique Id, a TicketGrantingTicket,
     * a Service, Expiration Policy and a flag to determine if the ticket
     * creation was from a new Login or not.
     *
     * @param id           the unique identifier for the ticket.
     * @param ticket       the TicketGrantingTicket parent.
     * @param service      the service this ticket is for.
     * @param credentialProvided current credential that prompted this service ticket. May be null.
     * @param policy       the expiration policy for the Ticket.
     * @throws IllegalArgumentException if the TicketGrantingTicket or the
     *                                  Service are null.
     */
    public ServiceTicketImpl(final String id,
                             final TicketGrantingTicket ticket, final Service service,
                             final boolean credentialProvided, final ExpirationPolicy policy) {
        super(id, policy);

        Assert.notNull(service, "service cannot be null");
        Assert.notNull(ticket, "ticket cannot be null");
        this.ticketGrantingTicket = ticket;
        this.service = service;
        this.fromNewLogin = credentialProvided || ticket.getCountOfUses() == 0;
    }

    @Override
    public boolean isFromNewLogin() {
        return this.fromNewLogin;
    }

    @Override
    public Service getService() {
        return this.service;
    }

    /**
     * {@inheritDoc}
     * <p>The state of the ticket is affected by this operation and the
     * ticket will be considered used regardless of the match result.
     * The state update subsequently may impact the ticket expiration
     * policy in that, depending on the policy configuration, the ticket
     * may be considered expired.
     */
    @Override
    public boolean isValidFor(final Service serviceToValidate) {
        update();
        return serviceToValidate.matches(this.service);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(final Object object) {
        if (object == null) {
            return false;
        }
        if (object == this) {
            return true;
        }
        if (!(object instanceof ServiceTicket)) {
            return false;
        }

        final Ticket ticket = (Ticket) object;

        return new EqualsBuilder()
                .append(ticket.getId(), this.getId())
                .isEquals();
    }

    @Override
    public ProxyGrantingTicket grantProxyGrantingTicket(
            final String id, final Authentication authentication,
            final ExpirationPolicy expirationPolicy) throws AbstractTicketException {
        synchronized (this) {
            if (this.grantedTicketAlready) {
                throw new InvalidProxyGrantingTicketForServiceTicket(this.service);
            }
            this.grantedTicketAlready = Boolean.TRUE;
        }
        final ProxyGrantingTicket pgt = new ProxyGrantingTicketImpl(id, this.service,
                this.getGrantingTicket(), authentication, expirationPolicy);
        getGrantingTicket().getProxyGrantingTickets().add(pgt);
        return pgt;
    }

    @Override
    public TicketGrantingTicket getGrantingTicket() {
        return this.ticketGrantingTicket;
    }

    @Override
    public Authentication getAuthentication() {
        return getGrantingTicket().getAuthentication();
    }

}
