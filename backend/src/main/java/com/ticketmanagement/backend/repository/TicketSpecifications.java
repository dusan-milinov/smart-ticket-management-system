package com.ticketmanagement.backend.repository;

import com.ticketmanagement.backend.enums.TicketPriority;
import com.ticketmanagement.backend.enums.TicketStatus;
import com.ticketmanagement.backend.model.Ticket;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class TicketSpecifications {
    private TicketSpecifications() {}

    public static Specification<Ticket> matching(TicketStatus status, TicketPriority priority, String search) {
        String term = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) predicates.add(cb.equal(root.get("ticketStatus"), status));
            if (priority != null) predicates.add(cb.equal(root.get("ticketPriority"), priority));
            if (!term.isEmpty()) {
                String pattern = "%" + term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")), pattern, '\\'),
                        cb.like(cb.lower(root.get("ticketNumber")), pattern, '\\')));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
