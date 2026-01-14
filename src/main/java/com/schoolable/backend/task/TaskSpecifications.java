package com.schoolable.backend.task;

import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Predicate;

import java.util.Collection;
import java.util.ArrayList;
import java.util.UUID;

public final class TaskSpecifications {

    private TaskSpecifications() {}

    public static Specification<Task> hasAssignee(UUID assigneeId) {
        return (root, query, cb) -> assigneeId == null ? cb.conjunction() : cb.equal(root.get("assigneeId"), assigneeId);
    }

    public static Specification<Task> hasAssigneeOrTaskIds(UUID assigneeId, Collection<Long> taskIds) {
        return (root, query, cb) -> {
            boolean hasAssignee = assigneeId != null;
            boolean hasTaskIds = taskIds != null && !taskIds.isEmpty();
            if (!hasAssignee && !hasTaskIds) {
                return cb.conjunction();
            }
            var predicates = new ArrayList<Predicate>();
            if (hasAssignee) {
                predicates.add(cb.equal(root.get("assigneeId"), assigneeId));
            }
            if (hasTaskIds) {
                predicates.add(root.get("id").in(taskIds));
            }
            return cb.or(predicates.toArray(new Predicate[0]));
        };
    }

    public static Specification<Task> hasDepartment(String department) {
        return (root, query, cb) -> department == null ? cb.conjunction() : cb.equal(root.get("organization"), department);
    }

    public static Specification<Task> hasStatus(String status) {
        return (root, query, cb) -> status == null ? cb.conjunction() : cb.equal(root.get("status"), status);
    }

    public static Specification<Task> hasPriority(String priority) {
        return (root, query, cb) -> priority == null ? cb.conjunction() : cb.equal(root.get("priority"), priority);
    }

    public static Specification<Task> titleContains(String queryText) {
        return (root, query, cb) -> queryText == null ? cb.conjunction() : cb.like(cb.lower(root.get("title")), "%" + queryText.toLowerCase() + "%");
    }
}
